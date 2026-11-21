package com.rbxnative.androidport;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Pure Java validation shared by Android Rook capture and local AI preview. */
public final class StudioPlayFrameRules {
    public static final long WINDOW_MS = 10 * 60 * 1000L;
    public static final int MAX_JPEG_BYTES = 180 * 1024;
    private StudioPlayFrameRules() {}

    public static boolean validTime(long armedAt, long expiresAt, long now) {
        return armedAt > 0 && expiresAt > armedAt &&
                expiresAt - armedAt <= WINDOW_MS &&
                now >= armedAt && now <= expiresAt;
    }

    public static String digest(byte[] image) {
        if (image == null) throw new IllegalArgumentException("No image");
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(image);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 15, 16))
                        .append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException err) {
            throw new IllegalStateException("SHA-256 unavailable", err);
        }
    }

    public static boolean validImage(byte[] jpeg, int width, int height) {
        if (jpeg == null || jpeg.length < 64 ||
                jpeg.length > MAX_JPEG_BYTES ||
                width < 1 || width > 512 || height < 1 || height > 512)
            return false;
        return (jpeg[0] & 255) == 0xff &&
                (jpeg[1] & 255) == 0xd8 && (jpeg[2] & 255) == 0xff &&
                (jpeg[jpeg.length - 2] & 255) == 0xff &&
                (jpeg[jpeg.length - 1] & 255) == 0xd9;
    }

    /** Reject stale, forged, mismatched or incomplete frame records. */
    public static boolean validReceipt(String expectedId, String reportedId,
            String source, boolean playerReady, int pixelCopyResult,
            long armedAt, long expiresAt, long capturedAt,
            String expectedSha256, byte[] jpeg, int width, int height,
            long now) {
        return expectedId != null &&
                expectedId.matches("[a-f0-9-]{36}") &&
                expectedId.equals(reportedId) &&
                "rook_native_window_pixelcopy".equals(source) &&
                playerReady && pixelCopyResult == 0 &&
                validTime(armedAt, expiresAt, capturedAt) &&
                validTime(armedAt, expiresAt, now) &&
                expectedSha256 != null &&
                expectedSha256.matches("[a-f0-9]{64}") &&
                validImage(jpeg, width, height) &&
                MessageDigest.isEqual(expectedSha256.getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII),
                        digest(jpeg).getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII));
    }
}
