package kr.busan.safetrip.photo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.photo.service.ImagePreprocessor;
import kr.busan.safetrip.photo.service.MatchConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** [사진 담당] TC-P001, TC-P019 사진 검증·전처리 (FR-P002, NFR-P002) */
class ImagePreprocessorTest {
    private final ImagePreprocessor p = new ImagePreprocessor(new MatchConfig());

    private static byte[] encode(int w, int h, String format, int type) throws Exception {
        BufferedImage img = new BufferedImage(w, h, type);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, format, bos);
        return bos.toByteArray();
    }

    private static BufferedImage decode(byte[] b) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(b));
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    private static ErrorCode codeOf(Runnable r) {
        return assertThrows(BusinessException.class, r::run).code();
    }

    @Test
    void 큰_사진은_긴변_1280으로_줄이고_JPEG로() throws Exception {
        byte[] out = p.prepare(encode(3000, 1500, "png", BufferedImage.TYPE_INT_ARGB), "sign.PNG");
        BufferedImage img = decode(out);
        assertEquals(1280, img.getWidth());
        assertEquals(640, img.getHeight());
        assertEquals((byte) 0xFF, out[0]);
        assertEquals((byte) 0xD8, out[1]);
    }

    @Test
    void 작은_사진은_크기_유지() throws Exception {
        BufferedImage img = decode(p.prepare(encode(100, 50, "jpg", BufferedImage.TYPE_INT_RGB), "a.jpeg"));
        assertEquals(100, img.getWidth());
        assertEquals(50, img.getHeight());
    }

    @Test
    void EXIF는_결과에_남지_않는다() throws Exception {
        byte[] jpg = encode(64, 64, "jpg", BufferedImage.TYPE_INT_RGB);
        byte[] secret = "Exif\0\0SECRET-GPS-35.15,129.11".getBytes(StandardCharsets.ISO_8859_1);
        int len = secret.length + 2;
        // SOI 바로 뒤에 APP1(EXIF) 세그먼트를 끼워 넣는다
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpg, 0, 2);
        withExif.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (len >> 8), (byte) len});
        withExif.write(secret);
        withExif.write(jpg, 2, jpg.length - 2);
        byte[] in = withExif.toByteArray();
        assertTrue(contains(in, "SECRET-GPS".getBytes(StandardCharsets.ISO_8859_1)));

        byte[] out = p.prepare(in, "a.jpg");
        assertFalse(contains(out, "SECRET-GPS".getBytes(StandardCharsets.ISO_8859_1)));
        assertFalse(contains(out, "Exif".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void 허용되지_않은_형식() throws Exception {
        byte[] png = encode(10, 10, "png", BufferedImage.TYPE_INT_RGB);
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA, codeOf(() -> p.prepare(png, "a.gif")));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA, codeOf(() -> p.prepare(png, "noext")));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA, codeOf(() -> p.prepare(png, null)));
        // 확장자만 jpg 이고 내용은 다른 형식
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA, codeOf(() -> p.prepare("GIF89a....".getBytes(StandardCharsets.ISO_8859_1), "a.jpg")));
    }

    @Test
    void 십MB_초과() {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF; big[1] = (byte) 0xD8; big[2] = (byte) 0xFF;
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, codeOf(() -> p.prepare(big, "a.jpg")));
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE,
                codeOf(() -> p.prepare(new MockMultipartFile("image", "a.jpg", "image/jpeg", big))));
    }

    @Test
    void 비었거나_깨진_사진() {
        assertEquals(ErrorCode.INVALID_INPUT, codeOf(() -> p.prepare(new byte[0], "a.jpg")));
        assertEquals(ErrorCode.INVALID_INPUT, codeOf(() -> p.prepare((byte[]) null, "a.jpg")));
        assertEquals(ErrorCode.INVALID_INPUT, codeOf(() -> p.prepare(new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[0]))));
        assertEquals(ErrorCode.INVALID_INPUT, codeOf(() -> p.prepare((org.springframework.web.multipart.MultipartFile) null)));
        byte[] broken = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        assertEquals(ErrorCode.INVALID_INPUT, codeOf(() -> p.prepare(broken, "a.png")));
    }

    @Test
    void MultipartFile로도_처리() throws Exception {
        byte[] jpg = encode(20, 10, "jpg", BufferedImage.TYPE_INT_RGB);
        byte[] out = p.prepare(new MockMultipartFile("image", "a.jpg", "image/jpeg", jpg));
        assertEquals(20, decode(out).getWidth());
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xD8}, new byte[]{out[0], out[1]});
    }
}
