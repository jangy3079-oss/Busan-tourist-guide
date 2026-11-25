package kr.busan.safetrip.course.service;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * [코스 담당] COMP-C05 RelaxationPolicy
 * 요구사항: FR-C007, NFR-C005
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 조건 완화 순서·규칙. 순서는 intent.json 의 relaxOrder(설정으로 변경, NFR-C005).
 * 고정 출발지(startPlaceId)·시간은 사용자가 정한 것이라 풀지 않는다.
 */
@Component
public class RelaxationPolicy {
    /** key = messages.json 의 "relax.{key}" 로 "완화됨" 칩 문구를 만든다 */
    public record Relaxed(IntentParser.Intent intent, String key) {}

    private final List<String> order;

    public RelaxationPolicy(CourseSettings settings) {
        this.order = settings.intent().relaxOrder();
    }

    /** step 번째 완화(0부터). 더 풀 것이 없으면 null. 이미 풀려 있는 조건이면 intent 가 그대로 돌아온다 */
    public Relaxed relax(IntentParser.Intent i, int step) {
        if (step >= order.size()) return null;
        String key = order.get(step);
        IntentParser.Intent r = switch (key) {
            case "categories" -> i.with(i.visitDate(), i.startTime(), i.durationMin(), i.area(), List.of(), i.startPlaceId());
            case "area" -> i.with(i.visitDate(), i.startTime(), i.durationMin(), null, i.categories(), i.startPlaceId());
            case "indoor" -> i.withIndoor(false);
            default -> throw new IllegalStateException("모르는 완화 단계: " + key);
        };
        return new Relaxed(r, key);
    }
}
