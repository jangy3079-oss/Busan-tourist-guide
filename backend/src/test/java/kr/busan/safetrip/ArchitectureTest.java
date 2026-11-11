package kr.busan.safetrip;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * [공용] "남의 소스 건드리지 않기"를 빌드가 대신 지켜 준다.
 * 코스·사진·긴급 패키지는 서로를 import 할 수 없고, common 은 기능 패키지를 몰라야 한다.
 * 공유가 필요하면 common 으로 올리되 3인 합의(OWNERSHIP.md).
 */
class ArchitectureTest {
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("kr.busan.safetrip");

    @Test
    void courseDoesNotUseOthers() {
        noClasses().that().resideInAPackage("..course..")
                .should().dependOnClassesThat().resideInAnyPackage("..photo..", "..emergency..").check(CLASSES);
    }

    @Test
    void photoDoesNotUseOthers() {
        noClasses().that().resideInAPackage("..photo..")
                .should().dependOnClassesThat().resideInAnyPackage("..course..", "..emergency..").check(CLASSES);
    }

    @Test
    void emergencyDoesNotUseOthers() {
        noClasses().that().resideInAPackage("..emergency..")
                .should().dependOnClassesThat().resideInAnyPackage("..course..", "..photo..").check(CLASSES);
    }

    @Test
    void commonDoesNotUseFeatures() {
        noClasses().that().resideInAPackage("..common..")
                .should().dependOnClassesThat().resideInAnyPackage("..course..", "..photo..", "..emergency..").check(CLASSES);
    }
}
