package kr.busan.safetrip.emergency.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [긴급 담당] 역지오코딩 결과에서 법정동 토큰만 추출한다.
 */
public final class DongExtractor {
    private static final Pattern DONG = Pattern.compile(
            "(?<![가-힣])([가-힣]+(?:\\d+)?동(?:\\d+)?가|[가-힣]+\\d*동|[가-힣]+\\d*가)(?![가-힣])");

    private DongExtractor() {}

    public static String extract(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        Matcher matcher = DONG.matcher(address);
        return matcher.find() ? matcher.group(1) : null;
    }
}
