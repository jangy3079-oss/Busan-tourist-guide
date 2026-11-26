package kr.busan.safetrip.course.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.Lang;

/**
 * [코스 담당] CourseSettings
 * 요구사항: NFR-C005
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * resources/course/ 의 설정(intent.json)·문구(messages.json)·프롬프트(*-prompt.txt)를 읽는다. 값은 코드가 아니라 파일에서 바꾼다.
 */
@Component
public class CourseSettings {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Intent(int defaultDurationMin, int minDurationMin, int maxDurationMin, int topK,
                         List<String> categories, Map<Area, String> areaAddress,
                         Map<Area, List<String>> areaKeywords, Map<String, List<String>> categoryKeywords,
                         List<String> outOfScopeKeywords, List<String> relaxOrder,
                         int narrationMaxChars, int titleMaxChars, double promptLengthRatio, int wantedPerCategory, String preferenceNoise,
                         List<String> indoorCodes, List<String> indoorKeywords, Demoted demoted, Unsupported unsupported, NextDay nextDay) {}

    /** 날짜를 말하지 않았는데 시간이 지났으면 내일로(#230). lateHour 이후에 시각도 말하지 않았으면 내일 startTime */
    public record NextDay(int lateHour, String startTime) {}

    /** 수정 문장 중 지원하지 않는 요청(식사 시각 지정·가격). 코스를 바꾸지 않고 안내한다 */
    public record Unsupported(List<String> mealWords, String timePattern, List<String> priceWords) {}

    /** 요청하지 않으면 빼고, 요청해도 뒤로 보내는 분류(#151) */
    public record Demoted(String category, List<String> codes, List<String> keywords) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Intent intent = read("/course/intent.json", new TypeReference<>() {});
    private final Map<String, Map<String, String>> messages = read("/course/messages.json", new TypeReference<>() {});
    private final String intentPrompt = text("/course/intent-prompt.txt");
    private final String editPrompt = text("/course/edit-prompt.txt");
    private final String narrationPrompt = text("/course/narration-prompt.txt");

    public Intent intent() { return intent; }
    public String intentPrompt() { return intentPrompt; }
    public String editPrompt() { return editPrompt; }
    public String narrationPrompt() { return narrationPrompt; }

    /** 다국어 문구. {0} 자리에 arg. 없는 키는 키 그대로 */
    public String msg(Lang lang, String key, Object... args) {
        String s = messages.get(lang.name().toLowerCase()).getOrDefault(key, key);
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    private static <T> T read(String path, TypeReference<T> type) {
        try (InputStream in = CourseSettings.class.getResourceAsStream(path)) {
            return JSON.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(String path) {
        try (InputStream in = CourseSettings.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
