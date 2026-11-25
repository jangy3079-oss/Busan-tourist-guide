package kr.busan.safetrip.photo.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;

/**
 * [사진 담당] COMP-P04 ImagePreprocessor
 * 요구사항: FR-P002, P003, NFR-P002
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 형식 판별·리사이즈, EXIF(위치 등) 미사용·폐기, 메모리 처리(NFR-P002).
 * - 확장자와 파일 앞 바이트(JPEG FFD8FF, PNG 시그니처)를 모두 확인한다. 허용 형식·최대 크기·긴 변은 matching.yml.
 * - 픽셀만 디코드해 RGB 로 다시 JPEG 인코딩하므로 EXIF 등 메타데이터는 읽지도 옮기지도 않는다.
 * - 파일을 쓰지 않고 바이트 배열로만 다룬다. 반환값도 호출한 쪽이 응답 뒤 버린다.
 */
@Component
public class ImagePreprocessor {
    private static final byte[] PNG_SIG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final MatchConfig.Image cfg;

    public ImagePreprocessor(MatchConfig config) {
        this.cfg = config.image();
        ImageIO.setUseCache(false); // ImageIO 가 디스크 임시 캐시를 쓰지 않게 한다(NFR-P002)
    }

    public byte[] prepare(MultipartFile image) {
        if (image == null || image.isEmpty()) throw new BusinessException(ErrorCode.INVALID_INPUT, "사진이 없습니다.");
        if (image.getSize() > cfg.maxBytes()) throw tooLarge();
        try {
            return prepare(image.getBytes(), image.getOriginalFilename());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진을 읽지 못했습니다.");
        }
    }

    /** 바이트 → 검증 → 디코드 → (필요 시) 축소 → 메타데이터 없는 JPEG 바이트 */
    public byte[] prepare(byte[] data, String filename) {
        if (data == null || data.length == 0) throw new BusinessException(ErrorCode.INVALID_INPUT, "사진이 없습니다.");
        if (data.length > cfg.maxBytes()) throw tooLarge();
        if (!allowedExtension(filename) || !(isJpeg(data) || isPng(data))) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA,
                    "JPG·PNG 사진만 올릴 수 있습니다. 허용 형식: " + String.join(", ", cfg.allowedFormats()));
        }
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            src = null;
        }
        if (src == null) throw new BusinessException(ErrorCode.INVALID_INPUT, "사진을 열지 못했습니다. 다른 사진으로 다시 시도해 주세요.");

        BufferedImage out = toRgb(src, cfg.maxLongEdgePx());
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(out, "jpg", bos)) throw new IOException("jpg writer 없음");
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL, "사진 처리 중 오류가 났습니다.");
        }
    }

    /** 긴 변이 maxLongEdge 를 넘으면 비율을 유지해 줄이고, 알파 채널은 흰 배경으로 합친다. */
    static BufferedImage toRgb(BufferedImage src, int maxLongEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) maxLongEdge / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private boolean allowedExtension(String filename) {
        if (filename == null) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        List<String> allowed = cfg.allowedFormats();
        return allowed.contains(ext);
    }

    static boolean isJpeg(byte[] d) {
        return d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] d) {
        if (d.length < PNG_SIG.length) return false;
        for (int i = 0; i < PNG_SIG.length; i++) if (d[i] != PNG_SIG[i]) return false;
        return true;
    }

    private BusinessException tooLarge() {
        return new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "사진은 10MB 이하만 올릴 수 있습니다.");
    }
}
