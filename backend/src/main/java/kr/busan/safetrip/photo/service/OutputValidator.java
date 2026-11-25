package kr.busan.safetrip.photo.service;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.Lang;

/**
 * [사진 담당] COMP-P08 OutputValidator
 * 요구사항: FR-P012, AI-REQ-P02, AI-REQ-P03
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 설명이 장소 원문과 어긋나는지 검사. 사진 전용. 순수 함수.
 * 버리는 경우: 비었음 / 길이 초과 / 요청 언어가 아님 / 원문에 없는 숫자 / 다른 장소 이름이 들어 있음.
 * 언어 판정: ko=한글 있음, ja=가나 있음, en=한글·가나 없음. (상호는 한글 원문 그대로 쓰므로 en·ja 에서도 상호의 한글은 허용)
 */
@Component("photoOutputValidator") // 코스·사진에 같은 이름 클래스가 있어 빈 이름을 구분
public class OutputValidator {
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3]");
    private static final Pattern KANA = Pattern.compile("[\\u3040-\\u30FF]");

    /**
     * @param placeName  이 장소의 한글 상호(언어 판정 때 빼고 본다)
     * @param otherNames 다른 장소 이름들(들어 있으면 실패). 이 장소 이름에 포함되는 이름은 건너뛴다
     */
    public boolean isValid(String generated, String sourceText, Lang lang, int maxChars, String placeName, Collection<String> otherNames) {
        if (generated == null || generated.isBlank() || generated.length() > maxChars) return false;
        String withoutName = placeName == null ? generated : generated.replace(placeName, "");
        boolean ok = switch (lang) {
            case KO -> HANGUL.matcher(withoutName).find();
            case JA -> KANA.matcher(withoutName).find();
            case EN -> !HANGUL.matcher(withoutName).find() && !KANA.matcher(withoutName).find();
        };
        if (!ok) return false;
        String src = sourceText == null ? "" : sourceText;
        Matcher m = NUMBER.matcher(generated);
        while (m.find()) if (!src.contains(m.group())) return false;
        for (String other : otherNames) {
            if (other == null || other.length() < 2) continue;
            if (placeName != null && placeName.contains(other)) continue;
            if (src.contains(other)) continue; // 원문(주소·개요)에 있는 말이면 근거가 있다
            if (generated.contains(other)) return false;
        }
        return true;
    }
}
