package com.example.auth.infrastructure.totp;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * TASK-MONO-771 S2c (owner decision 2026-10-09) — renders an {@code otpauth://} enrollment URI as a QR PNG,
 * server-side, for {@code GET /mfa/setup} (auth-api.md § IdP 브라우저 화면 — 2단계 인증 § /mfa/setup).
 *
 * <p>Deliberately NOT a Spring bean and NOT an HTTP endpoint of its own — {@link MfaPageController} calls this
 * directly and embeds the result as an inline {@code data:} URI in the SAME {@code /mfa/setup} response that
 * already carries the manual key and the {@code otpauth://} link. That design choice is what gives the QR the
 * three properties the owner decision asked for, for free, instead of as separate code to get right:
 * <ul>
 *   <li><b>Same security chain / CSRF rules as the page</b> — it is not a second request, so there is nothing to
 *   secure separately; it inherits the {@code @Order(0)} permitAll chain's session check exactly as the rest of
 *   the page does ({@link MfaPageController#setupPage}).</li>
 *   <li><b>Never another session's secret</b> — the QR is encoded from the {@code otpauthUri} the CURRENT
 *   request's {@code AccountSecondFactorService.startEnrollment} call just produced for THIS session's account;
 *   there is no account parameter, URL, or cache key an attacker could vary to ask for someone else's.</li>
 *   <li><b>No caching headers to get wrong</b> — Spring Security's default header writer already stamps
 *   {@code Cache-Control: no-cache, no-store} on every response in this chain (including this one); there is no
 *   second response to separately mark.</li>
 * </ul>
 *
 * <p>Pure encoding: no persistence, no logging (R4 — the otpauth URI it encodes is a secret-bearing value, same
 * rule as the manual key and the recovery codes).
 */
public final class QrCodePngEncoder {

    /** Square QR image size in pixels — large enough for a phone camera at arm's length. */
    private static final int SIZE_PX = 240;

    private QrCodePngEncoder() {
    }

    /**
     * {@code data:image/png;base64,...} ready for an {@code <img src>} attribute.
     *
     * @throws IllegalStateException on an encode/PNG-write failure (content is validated upstream; this is not
     *                                expected to be reachable in practice, and is fail-closed by letting the
     *                                caller's existing exception handling turn it into the page's «지금은 확인할 수
     *                                없습니다» result — see {@link MfaPageController#setupPage})
     */
    public static String dataUri(String otpauthUri) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png(otpauthUri));
    }

    private static byte[] png(String content) {
        Objects.requireNonNull(content);
        try {
            // MARGIN deliberately left at zxing's default (4 modules, the quiet zone a phone camera's own QR
            // detector expects) — shrinking it would make the image smaller but risks a real scanner failing to
            // find the symbol, trading a real property (scannability) for a cosmetic one.
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(toImage(matrix), "png", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            // Not R4-sensitive: the exception type/stack trace carries no secret. The otpauth URI itself never
            // goes into this exception or any log line reachable from it.
            throw new IllegalStateException("QR encode failed", e);
        }
    }

    /**
     * {@code BitMatrix} -> {@code BufferedImage} by hand (black/white only) so production never needs zxing's
     * {@code javase} module (that module exists for desktop/CLI tools and pulls in AWT image helpers this
     * service does not otherwise touch).
     */
    private static BufferedImage toImage(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return image;
    }
}
