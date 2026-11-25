// [공용] 카카오맵 그리기(COMP-C09, #24). Dart 쪽은 lib/widgets/kakao_map_web.dart.
// 지도 표시용 SDK 만 쓴다(장소 데이터를 저장하지 않는다).
(function () {
  var loading = null;
  var maps = new WeakMap();

  function load(key) {
    if (loading) return loading;
    loading = new Promise(function (resolve) {
      if (window.kakao && window.kakao.maps) { window.kakao.maps.load(function () { resolve(true); }); return; }
      var done = false;
      function end(ok) { if (!done) { done = true; resolve(ok); } }
      var s = document.createElement('script');
      s.src = 'https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=' + encodeURIComponent(key);
      s.onload = function () {
        if (window.kakao && window.kakao.maps) window.kakao.maps.load(function () { end(true); });
        else end(false);
      };
      s.onerror = function () { end(false); }; // 키 오류·도메인 미등록(401)·오프라인
      setTimeout(function () { end(false); }, 6000);
      document.head.appendChild(s);
    });
    return loading;
  }

  function pinEl(p, i, onTap) {
    var color = p.er ? '#C2410C' : p.fixed ? '#0E7490' : '#191F28';
    var box = document.createElement('div');
    box.style.cssText = 'display:flex;flex-direction:column;align-items:center;font-family:Pretendard,sans-serif;pointer-events:' + (onTap ? 'auto' : 'none') + ';cursor:' + (onTap ? 'pointer' : 'default');
    if (p.name) {
      var tip = document.createElement('div');
      tip.textContent = p.name; // 장소 이름은 글자로만 넣는다
      tip.style.cssText = 'max-width:110px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;background:#fff;border:1px solid #D1D6DB;border-radius:8px;padding:2px 6px;margin-bottom:3px;font-size:11px;font-weight:600;color:#191F28;box-shadow:0 1px 3px rgba(0,0,0,.15)';
      box.appendChild(tip);
    }
    var dot = document.createElement('div');
    dot.textContent = p.label || String(i + 1);
    dot.style.cssText = 'width:24px;height:24px;border-radius:50%;background:' + color + ';color:#fff;border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:800;box-sizing:content-box';
    box.appendChild(dot);
    if (onTap) box.addEventListener('click', function (e) { e.stopPropagation(); onTap(i); });
    return box;
  }

  function render(el, json, onTap) {
    var o = JSON.parse(json);
    var k = window.kakao.maps;
    el.style.width = '100%';
    el.style.height = '100%';
    // 보기 전용 지도는 입력을 받지 않는다. 받으면 페이지 스크롤과 위에 뜬 시트의 누르기를 지도가 가로챈다
    el.style.pointerEvents = onTap ? 'auto' : 'none';
    var st = maps.get(el);
    if (!st) {
      st = { items: [], fit: function () {} };
      maps.set(el, st);
      var first = o.pins[0] || o.me;
      st.map = new k.Map(el, { center: new k.LatLng(first ? first.lat : 35.1556, first ? first.lng : 129.1243), level: 5 });
      // 화면에 붙기 전에 만들어져 크기가 0일 수 있으므로 크기가 바뀔 때마다 다시 맞춘다
      st.observer = new ResizeObserver(function () { st.map.relayout(); st.fit(); });
      st.observer.observe(el);
    }
    st.items.forEach(function (x) { x.setMap(null); });
    st.items = [];
    var ll = function (p) { return new k.LatLng(p.lat, p.lng); };
    if (o.path.length > 1) {
      st.items.push(new k.Polyline({ map: st.map, path: o.path.map(ll), strokeWeight: 3, strokeColor: '#2563EB', strokeOpacity: 0.9, strokeStyle: 'shortdash' }));
    }
    o.pins.forEach(function (p, i) {
      st.items.push(new k.CustomOverlay({ map: st.map, position: ll(p), content: pinEl(p, i, onTap), yAnchor: 1, zIndex: 10 + i }));
    });
    if (o.me) {
      var m = document.createElement('div');
      m.style.cssText = 'width:12px;height:12px;border-radius:50%;background:#2563EB;border:2px solid #fff;box-shadow:0 0 0 6px rgba(37,99,235,.18)';
      st.items.push(new k.CustomOverlay({ map: st.map, position: ll(o.me), content: m, yAnchor: 0.5 }));
    }
    var pts = o.pins.concat(o.me ? [o.me] : []);
    st.fit = function () {
      if (!pts.length) return;
      if (pts.length === 1) { st.map.setCenter(ll(pts[0])); st.map.setLevel(4); return; }
      var b = new k.LatLngBounds();
      pts.forEach(function (p) { b.extend(ll(p)); });
      st.map.setBounds(b, 48, 28, 20, 28); // 핀과 이름이 잘리지 않게 여백
    };
    st.fit();
  }

  function dispose(el) {
    var st = maps.get(el);
    if (st && st.observer) st.observer.disconnect();
    maps.delete(el);
  }

  window.busanKakaoMap = { load: load, render: render, dispose: dispose };
})();
