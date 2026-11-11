package kr.busan.safetrip.common.api;

/**
 * [공용] Lang
 * 상태: 완료
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * 응답 언어. 요청 파라미터 lang=ko|en|ja.
 */
public enum Lang {
    KO, EN, JA;

    public static Lang parse(String s) {
        if (s == null) return KO;
        return switch (s.toLowerCase()) {
            case "en" -> EN;
            case "ja" -> JA;
            default -> KO;
        };
    }
}
