package kr.busan.safetrip.course.service;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.Place;

/**
 * [코스 담당] COMP-C06 OutputValidator
 * 요구사항: FR-C010, AI-REQ-C01
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * LLM 이 쓴 문장 하나를 근거 데이터와 대조한다. 통과 조건:
 * 비어 있지 않음, 길이 maxChars 이하, 요청 언어 글자가 주를 이룸, 숫자는 근거 원문에 있는 것만, 코스 밖 장소명 없음.
 * 코스 전용(사진은 photo.OutputValidator).
 */
@Component("courseOutputValidator") // 코스·사진에 같은 이름 클래스가 있어 빈 이름을 구분
public class OutputValidator {
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /**
     * @param grounding  코스 장소(원문이 근거)
     * @param otherNames 코스 밖 장소 이름(나오면 거부, AI-REQ-C01)
     */
    public boolean isValid(String text, List<Place> grounding, Collection<String> otherNames, Lang lang, int maxChars) {
        return reason(text, grounding, otherNames, lang, maxChars) == null;
    }

    /** 통과하면 null, 아니면 걸린 규칙 이름(로그용. 문장 내용은 담지 않는다) */
    public String reason(String text, List<Place> grounding, Collection<String> otherNames, Lang lang, int maxChars) {
        if (text == null || text.isBlank()) return "empty";
        if (text.codePointCount(0, text.length()) > maxChars) return "length";
        if (!inLanguage(withoutNames(text, grounding, lang), lang)) return "language";
        StringBuilder src = new StringBuilder();
        for (Place p : grounding) src.append(p.getName()).append(' ').append(p.getOverview()).append(' ').append(p.getHoursText()).append(' ');
        Matcher m = NUMBER.matcher(text);
        while (m.find()) if (src.indexOf(m.group()) < 0) return "number";
        // 원문(개요)에 나오는 이웃 장소(예: 광안대교)는 근거가 있으므로 허용
        for (String other : otherNames) {
            if (other != null && other.length() >= 2 && text.contains(other) && src.indexOf(other) < 0) return "otherPlace";
        }
        return null;
    }

    /** 화면은 장소 이름을 원문(한글) 그대로 보여 준다. 영·일 문장에 코스 장소 이름이 한글로 들어간 것은 언어 위반으로 보지 않는다 */
    private static String withoutNames(String text, List<Place> grounding, Lang lang) {
        if (lang == Lang.KO) return text;
        String t = text;
        for (Place p : grounding) if (p.getName() != null && !p.getName().isBlank()) t = t.replace(p.getName(), "");
        return t;
    }

    /** ko = 한글, ja = 가나(또는 한자만), en = 라틴 글자. ja·en 은 다른 글자가 하나라도 섞이면 거부("海운대は" 같은 답) */
    static boolean inLanguage(String text, Lang lang) {
        int hangul = 0, kana = 0, han = 0, latin = 0;
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript s = Character.UnicodeScript.of(text.charAt(i));
            if (s == Character.UnicodeScript.HANGUL) hangul++;
            else if (s == Character.UnicodeScript.HIRAGANA || s == Character.UnicodeScript.KATAKANA) kana++;
            else if (s == Character.UnicodeScript.HAN) han++;
            else if (s == Character.UnicodeScript.LATIN) latin++;
        }
        return switch (lang) {
            case KO -> hangul > 0;
            case JA -> hangul == 0 && kana + han > 0; // 한자만 쓴 짧은 제목(海雲台散策)도 일본어로 본다
            case EN -> latin > 0 && hangul + kana + han == 0;
        };
    }
}
