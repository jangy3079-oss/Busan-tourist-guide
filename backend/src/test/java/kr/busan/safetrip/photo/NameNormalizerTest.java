package kr.busan.safetrip.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import kr.busan.safetrip.photo.service.MatchConfig;
import kr.busan.safetrip.photo.service.NameNormalizer;
import org.junit.jupiter.api.Test;

/** [사진 담당] TC-P003 상호 정규화 (FR-P005, NFR-P006) */
class NameNormalizerTest {
    private final NameNormalizer n = new NameNormalizer(new MatchConfig());

    @Test
    void 공백과_기호를_지운다() {
        assertEquals("해운대암소갈비집", n.normalize("  해운대 암소갈비집! "));
        assertEquals("abccafe", n.normalize("ＡＢＣ　Cafe!")); // NFKC 전각 → 반각, 소문자
    }

    @Test
    void 지점_접미어를_끝에서_한번_지운다() {
        assertEquals("할매국밥", n.normalize("할매국밥 (광안리점)"));
        assertEquals("카페", n.normalize("카페 센텀시티점")); // 긴 접미어 먼저
        assertEquals("광안리점", n.normalize("광안리점"));    // 이름 전체가 접미어면 남긴다
    }

    @Test
    void 혼동_문자를_치환한다() {
        assertEquals("coffee", n.normalize("C0FFEE"));
    }

    @Test
    void OCR과_장소이름에_같은_규칙() {
        assertEquals(n.normalize("해운대암소갈비집"), n.normalize("해운대 암소갈비집 본점"));
    }

    @Test
    void null과_빈값() {
        assertEquals("", n.normalize(null));
        assertEquals("", n.normalize("!!! ---"));
        assertEquals("", n.decomposeJamo(null));
    }

    @Test
    void 자모_분해() {
        assertEquals("ㄱㅏ", n.decomposeJamo("가"));
        assertEquals("ㄱㅏㄱ", n.decomposeJamo("각"));
        assertEquals("ㅎㅣㅎ", n.decomposeJamo("힣"));
        assertEquals("aㄱㅏ1", n.decomposeJamo("a가1"));
    }

    @Test
    void 같은_입력은_같은_결과() {
        assertEquals(n.normalize("밀면 전문점 ㈜"), n.normalize("밀면 전문점 ㈜"));
    }

    @Test
    void 설정_파일이_없으면_실패() {
        assertThrows(IllegalStateException.class, () -> new MatchConfig("photo/없는파일.yml"));
    }
}
