package kr.busan.safetrip.common.util;

import java.util.regex.Pattern;

/**
 * [공용] PiiMasker
 * 상태: 완료(마스킹 규칙은 SRS 와 대조 필요)
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 로그에 남기기 전 전화번호·이메일 마스킹(NFR-C003, P003). 로그·DB 에 사용자 문장을 쓸 때는 반드시 이 클래스를 거친다.
 */
public final class PiiMasker {
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d{1,3}[- ]?)?0?\\d{1,2}[- ]?\\d{3,4}[- ]?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    private PiiMasker() {}

    public static String mask(String s) {
        if (s == null) return null;
        String r = EMAIL.matcher(s).replaceAll("[email]");
        return PHONE.matcher(r).replaceAll("[phone]");
    }
}
