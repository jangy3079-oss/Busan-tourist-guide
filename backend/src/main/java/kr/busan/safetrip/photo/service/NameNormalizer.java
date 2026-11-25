package kr.busan.safetrip.photo.service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * [사진 담당] COMP-P06 NameNormalizer
 * 요구사항: FR-P005, NFR-P006
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 상호명 정규화(공백·특수문자·한/영 표기)·자모 분해로 name_norm 생성. DB·네트워크 없는 순수 함수(NFR-P006). place.name_norm 적재(KnowledgeIngestor)도 이 규칙을 쓴다.
 * 규칙: NFKC → 소문자 → 혼동 문자 치환 → 글자·숫자 외 제거 → 끝의 지점 접미어 1개 제거. 접미어·치환표는 matching.yml.
 * 규칙을 바꾸면 place.name_norm 을 다시 만들어야 한다(공용 담당에게 알릴 것).
 */
@Component
public class NameNormalizer {
    private static final char[] CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".toCharArray();
    private static final char[] JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ".toCharArray();
    private static final String[] JONG = {"", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ",
            "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"};

    private final List<String> branchSuffixes;
    private final Map<String, String> confusables;

    public NameNormalizer(MatchConfig config) {
        // 긴 접미어부터 검사한다("센텀시티점"이 "센텀점"보다 먼저)
        this.branchSuffixes = config.normalize().branchSuffixes().stream()
                .sorted(Comparator.comparingInt(String::length).reversed()).toList();
        this.confusables = config.normalize().confusables();
    }

    public String normalize(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT);
        for (Map.Entry<String, String> e : confusables.entrySet()) s = s.replace(e.getKey(), e.getValue());

        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().filter(Character::isLetterOrDigit).forEach(sb::appendCodePoint);
        s = sb.toString();

        for (String suffix : branchSuffixes) {
            if (s.length() > suffix.length() && s.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length());
                break;
            }
        }
        return s;
    }

    /** 한글 음절을 초·중·종성(호환 자모)으로 푼다. 한글 음절이 아닌 글자는 그대로 둔다. */
    public String decomposeJamo(String normalized) {
        if (normalized == null) return "";
        StringBuilder sb = new StringBuilder(normalized.length() * 3);
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int idx = c - 0xAC00;
                sb.append(CHO[idx / (21 * 28)]).append(JUNG[(idx % (21 * 28)) / 28]).append(JONG[idx % 28]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
