package kr.busan.safetrip.course.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.api.MapPointDto;
import kr.busan.safetrip.common.api.PlaceDetail;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.common.util.PiiMasker;
import kr.busan.safetrip.course.dto.CourseDtos;
import kr.busan.safetrip.course.dto.CourseDtos.Status;
import kr.busan.safetrip.course.entity.CoursePlan;
import kr.busan.safetrip.course.repository.CoursePlanRepository;
import kr.busan.safetrip.course.repository.CoursePlaceRepository;

/**
 * [코스 담당] COMP-C02 CourseService
 * 요구사항: FR-C001~C015, NFR-C001, C003
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 단계 순서 조율(Facade): 긴급 감지 -> 의도 추출 -> 후보 -> 탐색(안 되면 완화) -> 저장(마스킹) -> 응답. 설명은 별도 호출(NFR-C001).
 */
@Service
public class CourseService {
    private static final String SOURCE = "ⓒ한국관광공사";

    private final IntentParser intentParser;
    private final CandidateRetriever retriever;
    private final CoursePlanner planner;
    private final NarrationService narration;
    private final RelaxationPolicy relaxation;
    private final CourseSettings settings;
    private final CoursePlanRepository plans;
    private final CoursePlaceRepository coursePlaces;
    private final PlaceRepository places;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public CourseService(IntentParser intentParser, CandidateRetriever retriever, CoursePlanner planner, NarrationService narration,
                         RelaxationPolicy relaxation, CourseSettings settings, CoursePlanRepository plans,
                         CoursePlaceRepository coursePlaces, PlaceRepository places, ObjectMapper json) {
        this(intentParser, retriever, planner, narration, relaxation, settings, plans, coursePlaces, places, json,
                Clock.system(ZoneId.of("Asia/Seoul")));
    }

    /** 공용 ApiContractTest 가 경로 매핑만 보려고 이 모양으로 만든다(공용 테스트는 고치지 않는 규칙) */
    public CourseService(IntentParser intentParser, CandidateRetriever retriever, CoursePlanner planner, NarrationService narration) {
        this(intentParser, retriever, planner, narration, null, null, null, null, null, null, Clock.systemDefaultZone());
    }

    /** 테스트는 고정 시각 Clock 을 넣는다(NFR-C006) */
    CourseService(IntentParser intentParser, CandidateRetriever retriever, CoursePlanner planner, NarrationService narration,
                  RelaxationPolicy relaxation, CourseSettings settings, CoursePlanRepository plans,
                  CoursePlaceRepository coursePlaces, PlaceRepository places, ObjectMapper json, Clock clock) {
        this.intentParser = intentParser;
        this.retriever = retriever;
        this.planner = planner;
        this.narration = narration;
        this.relaxation = relaxation;
        this.settings = settings;
        this.plans = plans;
        this.coursePlaces = coursePlaces;
        this.places = places;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public CourseDtos.CourseResponse create(CourseDtos.CreateRequest req) {
        Lang lang = Lang.parse(req.lang());
        LocalDate today = LocalDate.now(clock);
        IntentParser.Intent parsed = intentParser.parse(req.query(), today);

        if (parsed.emergency().isPresent()) return emergency(lang, parsed.emergency().get());
        if (parsed.outOfScope()) {
            return notice(Status.OUT_OF_SCOPE, new CourseDtos.Notice(null, settings.msg(lang, "outOfScope"),
                    List.of(settings.msg(lang, "outOfScopeExample")), List.of()));
        }

        CourseDtos.Overrides o = req.overrides();
        LocalDate date = o != null && o.visitDate() != null ? parseDate(o.visitDate()) : parsed.visitDate();
        LocalTime time = o != null && o.startTime() != null ? parseTime(o.startTime()) : parsed.startTime();
        Integer dur = o != null && o.durationMin() != null ? o.durationMin() : parsed.durationMin();
        Area area = o != null && o.area() != null ? o.area() : parsed.area();

        IntentParser.Intent intent = parsed.with(
                date != null ? date : today,
                time != null ? time : LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES),
                dur != null ? dur : settings.intent().defaultDurationMin(),
                area, parsed.categories(), req.startPlaceId());
        List<CourseDtos.Assumption> assumptions = assumptions(lang, intent, date == null, time == null, dur == null, area == null);

        List<String> relaxed = new ArrayList<>();
        List<CourseDtos.Stop> stops = search(intent, lang, relaxed, retriever::retrieve);
        if (stops.isEmpty()) return noCourse(lang);
        return save(PiiMasker.mask(req.query()), null, 1, assumptions, relaxed, stops);
    }

    /**
     * API-C03. 지금 버전의 조건(가정 칩 값)·장소를 이어받아 수정 명령·칩 변경·출발 장소를 적용하고 다시 탐색한다.
     * 남는 장소는 후보 맨 앞에 두어 되도록 유지한다. 새로 들어온 장소는 changed=true.
     */
    @Transactional
    public CourseDtos.CourseResponse edit(String courseId, CourseDtos.EditRequest req) {
        Lang lang = Lang.parse(req.lang());
        CoursePlan prevPlan = load(courseId);
        CourseDtos.CourseResponse prev = read(prevPlan);

        List<IntentParser.EditOp> ops = List.of();
        if (req.text() != null && !req.text().isBlank()) {
            IntentParser.Edit e = intentParser.parseEdit(req.text(), prev.stops());
            if (e.emergency().isPresent()) return emergency(lang, e.emergency().get());
            ops = e.ops();
            if (ops.isEmpty() && req.overrides() == null && req.startPlaceId() == null) {
                return notice(Status.EDIT_UNCLEAR, new CourseDtos.Notice(null, settings.msg(lang, "editUnclear"),
                        List.of(settings.msg(lang, "editExample1"), settings.msg(lang, "editExample2")), List.of()));
            }
        }

        Map<String, CourseDtos.Assumption> a = new HashMap<>();
        for (CourseDtos.Assumption x : prev.assumptions()) a.put(x.key(), x);
        CourseDtos.Overrides o = req.overrides();
        Integer opDur = null;
        for (IntentParser.EditOp op : ops) if (op.op() == IntentParser.Op.SET_DURATION) opDur = op.durationMin();

        boolean dDef = (o == null || o.visitDate() == null) && a.get("visitDate").isDefault();
        LocalDate date = o != null && o.visitDate() != null ? parseDate(o.visitDate()) : LocalDate.parse(a.get("visitDate").value());
        boolean tDef = (o == null || o.startTime() == null) && a.get("startTime").isDefault();
        LocalTime time = o != null && o.startTime() != null ? parseTime(o.startTime()) : LocalTime.parse(a.get("startTime").value());
        Integer newDur = o != null && o.durationMin() != null ? o.durationMin() : opDur;
        boolean mDef = newDur == null && a.get("durationMin").isDefault();
        int dur = newDur != null ? newDur : Integer.parseInt(a.get("durationMin").value());
        boolean aDef = (o == null || o.area() == null) && a.get("area").isDefault();
        Area area = o != null && o.area() != null ? o.area()
                : a.get("area").value() == null ? null : Area.valueOf(a.get("area").value());

        Set<String> excluded = new HashSet<>();
        List<String> cats = new ArrayList<>();
        for (IntentParser.EditOp op : ops) {
            boolean drop = op.op() == IntentParser.Op.REMOVE || op.op() == IntentParser.Op.REPLACE;
            if (drop && op.order() != null && op.order() <= prev.stops().size()) excluded.add(prev.stops().get(op.order() - 1).contentId());
            boolean add = op.op() == IntentParser.Op.ADD || op.op() == IntentParser.Op.REPLACE;
            if (add && op.category() != null && !cats.contains(op.category())) cats.add(op.category());
        }
        String start = req.startPlaceId() != null ? req.startPlaceId()
                : prev.stops().stream().filter(CourseDtos.Stop::fixed).map(CourseDtos.Stop::contentId)
                        .filter(id -> !excluded.contains(id)).findFirst().orElse(null);

        // ponytail: KEEP 은 따로 강제하지 않는다(남는 장소는 후보 맨 앞이라 대부분 유지). 꼭 고정해야 하면 Planner 에 필수 장소 인자 추가
        List<Place> kept = new ArrayList<>();
        for (CourseDtos.Stop st : prev.stops()) {
            if (!excluded.contains(st.contentId())) places.findByContentId(st.contentId()).ifPresent(kept::add);
        }
        IntentParser.Intent intent = new IntentParser.Intent(date, time, dur, area, cats, start, false, Optional.empty());
        List<CourseDtos.Assumption> assumptions = assumptions(lang, intent, dDef, tDef, mDef, aDef);

        List<String> relaxed = new ArrayList<>();
        List<CourseDtos.Stop> stops = search(intent, lang, relaxed, i -> {
            List<Place> c = new ArrayList<>(kept);
            for (Place p : retriever.retrieve(i)) {
                if (!excluded.contains(p.getContentId()) && c.stream().noneMatch(k -> k.getContentId().equals(p.getContentId()))) c.add(p);
            }
            return c;
        });
        if (stops.isEmpty()) return noCourse(lang);

        Set<String> before = new HashSet<>();
        for (CourseDtos.Stop st : prev.stops()) before.add(st.contentId());
        List<CourseDtos.Stop> marked = stops.stream().map(st -> before.contains(st.contentId()) ? st
                : new CourseDtos.Stop(st.order(), st.contentId(), st.name(), st.category(), st.lat(), st.lng(),
                        st.arriveAt(), st.departAt(), st.moveFromPrev(), st.hoursUncertain(), st.fixed(), true)).toList();
        return save(PiiMasker.mask(req.text()), prevPlan.getId(), prevPlan.getVersion() + 1, assumptions, relaxed, marked);
    }

    /** API-C02. 저장된 코스 장소로 설명을 만든다(코스 응답과 분리, NFR-C001) */
    public CourseDtos.NarrationResponse narrate(String courseId, Lang lang) {
        return narration.narrate(courseId, read(load(courseId)).stops(), lang);
    }

    public CourseDtos.PlacePinsResponse placePins(Area area, String category, int limit) {
        String addr = area == null ? null : settings.intent().areaAddress().get(area);
        String cat = category == null || category.isBlank() ? null : category;
        return new CourseDtos.PlacePinsResponse(coursePlaces.find(addr, cat, Math.max(1, Math.min(limit, 200))).stream()
                .map(p -> new CourseDtos.PlacePin(p.getContentId(), p.getName(), p.getCategory(), p.getLat(), p.getLng()))
                .toList());
    }

    /** API-C05. 원문 그대로(TourAPI 국문). lang 은 화면 문구용이라 여기서는 쓰지 않는다 */
    public PlaceDetail evidence(String contentId, Lang lang) {
        Place p = places.findByContentId(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "장소 없음: " + contentId));
        boolean uncertain = p.getHoursText() == null || p.getHoursText().isBlank();
        return new PlaceDetail(p.getContentId(), p.getName(), p.getCategory(), p.getAddress(), p.getLat(), p.getLng(),
                p.getOverview(), p.getHoursText(), uncertain, null, SOURCE,
                p.getCollectedAt() == null ? null : p.getCollectedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime().toString(),
                null);
    }

    /** FR-C007 탐색 → 안 되면 relaxOrder 순서로 하나씩 풀고 다시 탐색. 푼 항목은 relaxed 에 문구로 쌓는다 */
    private List<CourseDtos.Stop> search(IntentParser.Intent intent, Lang lang, List<String> relaxed,
                                         Function<IntentParser.Intent, List<Place>> candidates) {
        List<CourseDtos.Stop> stops = planner.plan(intent, candidates.apply(intent));
        for (int step = 0; stops.isEmpty(); step++) {
            RelaxationPolicy.Relaxed r = relaxation.relax(intent, step);
            if (r == null) break;
            if (r.intent().equals(intent)) continue; // 이미 풀려 있던 조건
            intent = r.intent();
            relaxed.add(settings.msg(lang, "relax." + r.key()));
            stops = planner.plan(intent, candidates.apply(intent));
        }
        return stops;
    }

    /** 새 버전 저장(질의는 마스킹 값만, NFR-C003). result 에 응답 JSON 을 넣어 수정·설명이 다시 읽는다 */
    private CourseDtos.CourseResponse save(String maskedQuery, Long parentId, int version,
                                           List<CourseDtos.Assumption> assumptions, List<String> relaxed, List<CourseDtos.Stop> stops) {
        CoursePlan plan = plans.save(new CoursePlan(maskedQuery, parentId, version, clock.instant()));
        List<MapPointDto> path = stops.stream().map(s -> new MapPointDto(s.lat(), s.lng())).toList();
        CourseDtos.CourseResponse res = new CourseDtos.CourseResponse(Status.OK, courseId(plan.getId()), plan.getVersion(),
                parentId == null ? null : courseId(parentId), assumptions, relaxed, stops, path, null);
        plan.setResult(toJson(res));
        return res;
    }

    private CoursePlan load(String courseId) {
        long id;
        try {
            if (courseId == null || !courseId.startsWith("c_")) throw new NumberFormatException();
            id = Long.parseLong(courseId.substring(2), 36);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "코스 없음: " + courseId);
        }
        return plans.findById(id).filter(p -> p.getResult() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "코스 없음: " + courseId));
    }

    private CourseDtos.CourseResponse read(CoursePlan plan) {
        try {
            return json.readValue(plan.getResult(), CourseDtos.CourseResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** FR-C003 가정 칩. isDefault = 문장·칩 어디에도 없어서 기본값을 쓴 것 */
    private List<CourseDtos.Assumption> assumptions(Lang lang, IntentParser.Intent i,
                                                    boolean dDef, boolean tDef, boolean mDef, boolean aDef) {
        String hm = String.format("%02d:%02d", i.startTime().getHour(), i.startTime().getMinute());
        int m = i.durationMin();
        return List.of(
                new CourseDtos.Assumption("visitDate", i.visitDate().toString(), dDef ? settings.msg(lang, "today") : i.visitDate().toString(), dDef),
                new CourseDtos.Assumption("startTime", hm, tDef ? settings.msg(lang, "now", hm) : hm, tDef),
                new CourseDtos.Assumption("durationMin", String.valueOf(m),
                        m % 60 == 0 ? settings.msg(lang, "hours", m / 60) : settings.msg(lang, "minutes", m), mDef),
                new CourseDtos.Assumption("area", i.area() == null ? null : i.area().name(),
                        settings.msg(lang, "area." + (i.area() == null ? "ALL" : i.area().name())), aDef));
    }

    private CourseDtos.CourseResponse emergency(Lang lang, EmType type) {
        return notice(Status.EMERGENCY, new CourseDtos.Notice(type, settings.msg(lang, "emergency"), List.of(), List.of()));
    }

    private CourseDtos.CourseResponse noCourse(Lang lang) {
        return notice(Status.NO_COURSE, new CourseDtos.Notice(null, settings.msg(lang, "noCourse"), List.of(),
                List.of(settings.msg(lang, "suggestLonger"), settings.msg(lang, "suggestArea"))));
    }

    private static CourseDtos.CourseResponse notice(Status status, CourseDtos.Notice n) {
        return new CourseDtos.CourseResponse(status, null, null, null, List.of(), List.of(), List.of(), List.of(), n);
    }

    static String courseId(long id) {
        return "c_" + Long.toString(id, 36);
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "visitDate 형식은 YYYY-MM-DD");
        }
    }

    private static LocalTime parseTime(String s) {
        try {
            return LocalTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "startTime 형식은 HH:mm");
        }
    }
}
