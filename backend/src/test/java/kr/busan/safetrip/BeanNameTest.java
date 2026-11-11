package kr.busan.safetrip;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [공용] 서버를 띄우지 않고(DB 없이) 빈 이름 충돌을 잡는다.
 * 패키지가 달라도 클래스 이름이 같으면(예: 코스·사진의 OutputValidator) 빈 이름이 겹쳐 서버가 뜨지 않는다.
 * 실패하면 @Component("고유이름")으로 이름을 붙인다.
 */
class BeanNameTest {
    @Test
    void beanNamesAreUnique() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        var gen = AnnotationBeanNameGenerator.INSTANCE;
        var reg = new SimpleBeanDefinitionRegistry();
        Map<String, String> seen = new HashMap<>();
        StringBuilder dup = new StringBuilder();
        for (var bd : scanner.findCandidateComponents("kr.busan.safetrip")) {
            String name = gen.generateBeanName((AnnotatedBeanDefinition) bd, reg);
            String prev = seen.put(name, bd.getBeanClassName());
            if (prev != null) dup.append("\n  ").append(name).append(": ").append(prev).append(" / ").append(bd.getBeanClassName());
        }
        assertTrue(dup.isEmpty(), "빈 이름 충돌:" + dup);
    }
}
