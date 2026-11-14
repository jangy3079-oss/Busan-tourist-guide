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
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.api.MapPointDto;
import kr.busan.safetrip.common.api.PlaceDetail;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.util.PiiMasker;
import kr.busan.safetrip.course.dto.CourseDtos;
import kr.busan.safetrip.course.dto.CourseDtos.Status;
import kr.busan.safetrip.course.entity.CoursePlan;
import kr.busan.safetrip.course.repository.CoursePlanRepository;
import kr.busan.safetrip.course.repository.CoursePlaceRepository;

/**
 * [코스 담당] COMP-C02 CourseService
 * 요구사항: FR-C001~C015, NFR-C001, C003
 * 상태: create·placePins·evidence 완료 / edit·narrate TODO
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

        if (parsed.emergency().isPresent()) {
            return notice(Status.EMERGENCY, new CourseDtos.Notice(parsed.emergency().get(), settings.msg(lang, "emergency"), List.of(), List.of()));
        }
        if (parsed.outOfScope()) {
            return notice(Status.OUT_OF_SCOPE, new CourseDtos.Notice(null, settings.msg(lang, "outOfScope"),
                    List.of(settings.msg(lang, "outOfScopeExample")), List.of()));
        }

        CourseDtos.Overrides o = req.overrides();
        LocalDate date = o != null && o.visitDate() != null ? parseDate(o.visitDate()) : parsed.visitDate();
        LocalTime time = o != null && o.startTime() != null ? parseTime(o.startTime()) : parsed.startTime();
        Integer dur = o != null && o.durationMin() != null ? o.durationMin() : parsed.durationMin();
        Area area = o != null && o.area() != null ? o.area() : parsed.area();

        List<CourseDtos.Assumption> assumptions = assumptions(lang, date, time, dur, area);
        IntentParser.Intent intent = parsed.with(
                date != null ? date : today,
                time != null ? time : LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES),
                dur != null ? dur : settings.intent().defaultDurationMin(),
                area, parsed.categories(), req.startPlaceId());

        List<String> relaxed = new ArrayList<>();
        List<CourseDtos.Stop> stops = planner.plan(intent, retriever.retrieve(intent));
        for (int step = 0; stops.isEmpty(); step++) {
            RelaxationPolicy.Relaxed r = relaxation.relax(intent, step);
            if (r == null) break;
            if (r.intent().equals(intent)) continue; // 이미 풀려 있던 조건
            intent = r.intent();
            relaxed.add(settings.msg(lang, "relax." + r.key()));
            stops = planner.plan(intent, retriever.retrieve(intent));
        }
        if (stops.isEmpty()) {
            return notice(Status.NO_COURSE, new CourseDtos.Notice(null, settings.msg(lang, "noCourse"), List.of(),
                    List.of(settings.msg(lang, "suggestLonger"), settings.msg(lang, "suggestArea"))));
        }

        CoursePlan plan = plans.save(new CoursePlan(PiiMasker.mask(req.query()), null, 1, clock.instant()));
        List<MapPointDto> path = stops.stream().map(s -> new MapPointDto(s.lat(), s.lng())).toList();
        CourseDtos.CourseResponse res = new CourseDtos.CourseResponse(Status.OK, courseId(plan.getId()), plan.getVersion(), null,
                assumptions, relaxed, stops, path, null);
        plan.setResult(toJson(res));
        return res;
    }

    public CourseDtos.CourseResponse edit(String courseId, CourseDtos.EditRequest req) {
        throw new NotImplementedYetException("코스 담당", "CourseService.edit");
    }

    public CourseDtos.NarrationResponse narrate(String courseId, Lang lang) {
        throw new NotImplementedYetException("코스 담당", "CourseService.narrate");
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

    /** FR-C003 가정 칩. isDefault = 문장·칩 어디에도 없어서 기본값을 쓴 것 */
    private List<CourseDtos.Assumption> assumptions(Lang lang, LocalDate date, LocalTime time, Integer dur, Area area) {
        LocalDate d = date != null ? date : LocalDate.now(clock);
        LocalTime t = time != null ? time : LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        int m = dur != null ? dur : settings.intent().defaultDurationMin();
        String hm = String.format("%02d:%02d", t.getHour(), t.getMinute());
        return List.of(
                new CourseDtos.Assumption("visitDate", d.toString(), date == null ? settings.msg(lang, "today") : d.toString(), date == null),
                new CourseDtos.Assumption("startTime", hm, time == null ? settings.msg(lang, "now", hm) : hm, time == null),
                new CourseDtos.Assumption("durationMin", String.valueOf(m),
                        m % 60 == 0 ? settings.msg(lang, "hours", m / 60) : settings.msg(lang, "minutes", m), dur == null),
                new CourseDtos.Assumption("area", area == null ? null : area.name(),
                        settings.msg(lang, "area." + (area == null ? "ALL" : area.name())), area == null));
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
