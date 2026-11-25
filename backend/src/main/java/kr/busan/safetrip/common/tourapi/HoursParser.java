package kr.busan.safetrip.common.tourapi;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [공용] COMP-C07 HoursParser
 * 요구사항: FR-C018, FR-E010 전제, AI-REQ-C06
 * 상태: 규칙 완료(LLM 구조화 ADR-C07 은 TODO)
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * TourAPI 운영시간·휴무일 문장 -> 요일별 구간. 코스(영업 중 판정)와 긴급(OpenNowEvaluator)이 같이 쓰는 순수 함수.
 * 아는 표현을 지우고 모르는 글자가 남으면 전부 포기한다(틀린 시간보다 '불확실'이 낫다). 계절별·층별·격주 휴무는 불확실.
 * ponytail: 명절·공휴일 휴무는 결과에 없다(평소 요일만). 공휴일 데이터(#120)가 생기면 부르는 쪽에서 함께 판정한다.
 */
public final class HoursParser {
    private static final String T = "\\d{1,2}:\\d{2}";
    private static final Pattern RANGE = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*[~-]\\s*(?:익일\\s*)?(\\d{1,2}):(\\d{2})");
    private static final String DAY = "[월화수목금토일](?:요일)?";
    private static final Pattern TOKEN = Pattern.compile(
            "(?<brk>준비\\s*시간|휴게\\s*시간|브레이크\\s*타임)"
                    + "|(?<range>" + RANGE.pattern().replaceAll("\\((?!\\?)", "(?:") + ")"
                    + "|(?<word>매일|평일|주말|공휴일)"
                    + "|(?<![가-힣])(?<from>" + DAY + ")\\s*(?:[~-]\\s*(?<to>" + DAY + "))?(?![가-힣])");
    /** 주문·입장 마감: 여닫는 시각이 아니라서 지운다 */
    private static final Pattern LAST_ORDER = Pattern.compile("(?<![~\\-\\d:]|[~-]\\s)(?:" + T + "[,/\\s]*)+라스트\\s*오더(?!\\s*:?\\s*\\d)"
            + "|(?:점심|저녁)?\\s*(?:마지막\\s*주문|라스트\\s*오더|주문\\s*마감|입장\\s*마감|발권\\s*마감|샐러드바\\s*마감)\\s*:?(?:\\s*[,/]?\\s*(?:점심|저녁)?\\s*:?\\s*" + T + ")+");
    private static final Pattern HARMLESS = Pattern.compile("※[^※]*예약제[^※]*|(?:※|단,)?\\s*(?:단,)?\\s*재료\\s*소진\\s*시\\s*조기\\s*마감(?:\\s*가능)?|※?\\s*자세한[^※]*$");
    /** 요일·계절·장소에 따라 달라져 한 주 시간표로 쓸 수 없는 표현 */
    private static final Pattern VARIES = Pattern.compile("상이|변동|\\d\\s*월|\\d층|(?:평일|주말)[^\\d가-힣]*만?\\)?\\s*(?:준비|휴게|브레이크)|(?:준비|휴게)\\s*시간\\s*\\((?:평일|주말)|(?:평일|주말)만|주말에는");

    private static final Pattern REST_HARMLESS = Pattern.compile("\\(단,[^)]*\\)|\\(전화\\s*문의\\)|※[^※]*|자세한.*$|연중\\s*무휴|정기\\s*휴무|1월\\s*1일|정부\\s*지정"
            + "|삼일절|광복절|국경일|(?:법정)?\\s*공휴일|(?:명절|설날?|추석)(?:\\s*[·,]\\s*(?:설날?|추석))?|연휴|전날|전일|당일|및");
    private static final Pattern REST_IRREGULAR = Pattern.compile("월\\s*\\d\\s*회|연\\s*\\d\\s*회|매월|매달|\\d\\s*주|[첫둘셋넷]째|부정기|휴무일|휴점|점검|변동|상이");
    private static final Pattern REST_DAYS = Pattern.compile("(?:매주)?\\s*(" + DAY + ")\\s*(?:[~-]\\s*(" + DAY + "))?(?![가-힣])");

    private HoursParser() {}

    /**
     * 예: "월~금 09:00~18:00, 토 10:00~15:00" + "매주 일요일" -> 요일별 [여는 시각, 닫는 시각].
     * 빈 맵 = 운영시간 불확실. 값이 빈 목록인 요일 = 휴무. 닫는 시각이 여는 시각보다 이르거나 같으면 다음 날(24시간 = 00:00~00:00).
     */
    public static Map<DayOfWeek, List<LocalTime[]>> parse(String hours, String rest) {
        if (hours == null || hours.isBlank()) return Map.of();
        Set<DayOfWeek> closed = closedDays(rest);
        if (closed == null) return Map.of();
        Map<DayOfWeek, List<LocalTime[]>> week = week(hours.replaceAll("<[^>]*>|[\\r\\n]+", " ").strip());
        if (week.isEmpty()) return week;
        closed.forEach(d -> week.put(d, List.of()));
        return week;
    }

    private static Map<DayOfWeek, List<LocalTime[]>> week(String text) {
        Map<DayOfWeek, List<LocalTime[]>> week = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) week.put(d, new ArrayList<>());
        if (text.matches("상시\\s*(?:개방|영업)")) {
            week.values().forEach(l -> l.add(new LocalTime[]{LocalTime.MIDNIGHT, LocalTime.MIDNIGHT}));
            return week;
        }
        String s = LAST_ORDER.matcher(HARMLESS.matcher(text).replaceAll(" ")).replaceAll(" ");
        if (VARIES.matcher(s).find()) return Map.of();

        Map<DayOfWeek, List<LocalTime[]>> breaks = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) breaks.put(d, new ArrayList<>());
        Set<DayOfWeek> days = EnumSet.allOf(DayOfWeek.class);
        boolean groupOpen = false; // 요일이 이어지는 중(평일, 토)
        boolean inBreak = false;
        boolean any = false;
        Matcher m = TOKEN.matcher(s);
        StringBuilder left = new StringBuilder();
        int at = 0;
        while (m.find()) {
            String gap = s.substring(at, m.start());
            left.append(gap).append(' ');
            at = m.end();
            if (m.group("brk") != null) {
                inBreak = true;
                continue;
            }
            if (m.group("range") != null) {
                if (inBreak && gap.matches(".*[^,\\s:].*")) inBreak = false; // "준비시간 15:00~16:00, 17:00~18:00" 만 이어지는 휴게로 본다
                LocalTime[] span = span(m.group("range"));
                if (span == null) return Map.of();
                for (DayOfWeek d : days) (inBreak ? breaks : week).get(d).add(span);
                any |= !inBreak;
                groupOpen = false;
                continue;
            }
            inBreak = false;
            if (!groupOpen) days = EnumSet.noneOf(DayOfWeek.class);
            groupOpen = true;
            days.addAll(days(m.group("word"), m.group("from"), m.group("to")));
        }
        left.append(s.substring(at));
        if (!any || groupOpen || left.toString().matches("(?s).*[가-힣A-Za-z0-9].*")) return Map.of();
        for (DayOfWeek d : DayOfWeek.values()) {
            if (week.get(d).size() > 1) return Map.of(); // 같은 요일에 시간이 둘 이상이면 시설별 시간일 수 있다
            List<LocalTime[]> open = week.get(d);
            for (LocalTime[] b : breaks.get(d)) open = subtract(open, b);
            week.put(d, open);
        }
        return week;
    }

    /** 매주 쉬는 요일. 모르는 표현이 있으면 null */
    private static Set<DayOfWeek> closedDays(String rest) {
        Set<DayOfWeek> closed = EnumSet.noneOf(DayOfWeek.class);
        if (rest == null || rest.isBlank()) return closed;
        String s = REST_HARMLESS.matcher(rest.replaceAll("<[^>]*>|[\\r\\n]+", " ")).replaceAll(" ");
        if (REST_IRREGULAR.matcher(rest).find()) return null;
        Matcher m = REST_DAYS.matcher(s);
        StringBuilder left = new StringBuilder();
        int at = 0;
        while (m.find()) {
            left.append(s, at, m.start());
            at = m.end();
            closed.addAll(days(null, m.group(1), m.group(2)));
        }
        left.append(s.substring(at));
        return left.toString().matches("(?s).*[가-힣A-Za-z0-9].*") ? null : closed;
    }

    private static Set<DayOfWeek> days(String word, String from, String to) {
        if (word != null) {
            return switch (word) {
                case "매일" -> EnumSet.allOf(DayOfWeek.class);
                case "평일" -> EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
                case "주말" -> EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
                default -> EnumSet.noneOf(DayOfWeek.class); // 공휴일
            };
        }
        DayOfWeek a = day(from);
        Set<DayOfWeek> r = EnumSet.of(a);
        if (to != null) for (DayOfWeek d = a; d != day(to); d = d.plus(1)) r.add(d.plus(1)); // 금~월처럼 주를 넘어도 된다
        return r;
    }

    private static DayOfWeek day(String s) {
        return DayOfWeek.of("월화수목금토일".indexOf(s.charAt(0)) + 1);
    }

    private static LocalTime[] span(String s) {
        Matcher m = RANGE.matcher(s);
        if (!m.matches()) return null;
        int h1 = Integer.parseInt(m.group(1)), m1 = Integer.parseInt(m.group(2));
        int h2 = Integer.parseInt(m.group(3)), m2 = Integer.parseInt(m.group(4));
        if (h1 > 24 || h2 > 30 || m1 > 59 || m2 > 59) return null;
        return new LocalTime[]{LocalTime.of(h1 % 24, m1), LocalTime.of(h2 % 24, m2)}; // 24:00 -> 00:00, 25:00 -> 01:00(다음 날)
    }

    /** 휴게 시간을 뺀다. 자정을 넘기는 영업도 같은 날 안의 휴게만 있다고 본다 */
    private static List<LocalTime[]> subtract(List<LocalTime[]> open, LocalTime[] b) {
        List<LocalTime[]> r = new ArrayList<>();
        for (LocalTime[] o : open) {
            boolean overnight = !o[1].isAfter(o[0]);
            if (b[0].isAfter(o[0]) && b[1].isAfter(b[0]) && (overnight || b[1].isBefore(o[1]))) {
                r.add(new LocalTime[]{o[0], b[0]});
                r.add(new LocalTime[]{b[1], o[1]});
            } else {
                r.add(o);
            }
        }
        return r;
    }
}
