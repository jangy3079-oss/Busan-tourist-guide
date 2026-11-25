package kr.busan.safetrip.photo.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * [사진 담당] PhotoPlaces
 * 요구사항: FR-P006, FR-P010, FR-P016, FR-P020
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 사진용 place 조회. 공용 PlaceRepository 에는 쿼리를 추가하지 않는다는 규칙에 따라 사진 패키지에 둔다.
 * 쓰기는 name_norm 한 컬럼뿐이다(#135: 수집 뒤 사진이 정규화 이름을 만든다).
 * 대상 2개 구의 장소만 수집돼 있으므로 "2개 구 전체" = 유형이 맞는 전체 장소다. 반경은 PlaceMatcher 가 좌표로 거른다.
 */
@Repository
public class PhotoPlaces {
    /** 매칭 후보 한 건. nameNorm 이 아직 없으면 null */
    public record Row(long id, String contentId, String name, String nameNorm, double lat, double lng) {}

    private final JdbcTemplate jdbc;

    public PhotoPlaces(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 유형 번호(content_type_id)가 맞는 좌표 있는 장소. 순서는 contentId 오름차순(결정적) */
    public List<Row> candidates(List<Integer> contentTypes) {
        String in = String.join(",", contentTypes.stream().map(t -> "?").toList());
        return jdbc.query("SELECT id, content_id, name, name_norm, lat, lng FROM place "
                        + "WHERE lat IS NOT NULL AND lng IS NOT NULL AND content_type_id IN (" + in + ") ORDER BY content_id",
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5), rs.getDouble(6)),
                contentTypes.toArray());
    }

    /** 전체 장소(이름 정규화용) */
    public List<Row> all() {
        return jdbc.query("SELECT id, content_id, name, name_norm, lat, lng FROM place ORDER BY id",
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5), rs.getDouble(6)));
    }

    /** 장소 이름 전체(설명 검증에서 다른 장소 이름이 섞였는지 볼 때) */
    public List<String> names() {
        return jdbc.queryForList("SELECT name FROM place WHERE name IS NOT NULL", String.class);
    }

    /** [id, name_norm] 묶음 갱신 */
    public void updateNameNorms(List<Object[]> idAndNorm) {
        if (idAndNorm.isEmpty()) return;
        jdbc.batchUpdate("UPDATE place SET name_norm = ? WHERE id = ?",
                idAndNorm.stream().map(r -> new Object[]{r[1], r[0]}).toList());
    }

    /** 요일별 운영 구간 [day_of_week(1=월), open_min, close_min]. close_min 이 1440 을 넘으면 다음날 새벽까지 */
    public List<int[]> hours(long placeId) {
        return jdbc.query("SELECT day_of_week, open_min, close_min FROM place_hours WHERE place_id = ? ORDER BY day_of_week, open_min",
                (rs, i) -> new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)}, placeId);
    }
}
