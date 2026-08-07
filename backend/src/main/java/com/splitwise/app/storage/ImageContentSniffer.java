package com.splitwise.app.storage;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Detects an image's real content type from its magic bytes rather than
 * trusting the client-supplied Content-Type header. Needed because the mobile
 * client defaults to declaring "image/jpeg" for any extension it doesn't
 * specifically recognize, so the declared type is not reliable - sniffing the
 * actual bytes is the correct fix on the server side.
 *
 * Deliberately hand-rolled instead of pulling in a library like Apache Tika:
 * the set of formats we need to recognize is small and fixed
 * (JPEG/PNG/WEBP/HEIC), so a few magic-byte checks are simpler and lighter than
 * a general-purpose content-detection dependency.
 */
public final class ImageContentSniffer {

    private static final Set<String> HEIF_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs",
            "mif1", "msf1", "avif", "avis"
    );

    private ImageContentSniffer() {
    }

    /**
     * @return the sniffed MIME type ("image/jpeg", "image/png", "image/webp",
     * or "image/heic"), or null if the bytes don't match any recognized image
     * format.
     */
    public static String detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }

        if (isJpeg(bytes)) {
            return "image/jpeg";
        }
        if (isPng(bytes)) {
            return "image/png";
        }
        if (isWebp(bytes)) {
            return "image/webp";
        }
        if (isHeic(bytes)) {
            return "image/heic";
        }
        return null;
    }

    public static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" ->
                "jpg";
            case "image/png" ->
                "png";
            case "image/webp" ->
                "webp";
            case "image/heic" ->
                "heic";
            default ->
                "bin";
        };
    }

    private static boolean isJpeg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A;
    }

    private static boolean isWebp(byte[] b) {
        String riff = new String(b, 0, 4, StandardCharsets.US_ASCII);
        String webp = new String(b, 8, 4, StandardCharsets.US_ASCII);
        return "RIFF".equals(riff) && "WEBP".equals(webp);
    }

    /**
     * HEIC/HEIF files are ISO base media format ("ftyp" box) - bytes 4-8 are
     * the literal ASCII "ftyp", and bytes 8-12 are a 4-char brand code that
     * tells us it's specifically HEIC/HEIF rather than some other ftyp-based
     * format (e.g. plain MP4, which we don't want to accept here).
     */
    private static boolean isHeic(byte[] b) {
        String ftyp = new String(b, 4, 4, StandardCharsets.US_ASCII);
        if (!"ftyp".equals(ftyp)) {
            return false;
        }
        String brand = new String(b, 8, 4, StandardCharsets.US_ASCII).trim().toLowerCase();
        return HEIF_BRANDS.contains(brand);
    }
}
