package com.visilink.rag.service;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/**
 * Stage 3: Targeted JavaCV Frame Extraction & Dual-Mode Decomposition Engine.
 *
 * Implements Section 3.4 of the VisiLink-RAG paper:
 * 1. Microsecond Timestamp Seeking: t_mu = (long)(tau_k * 1,000,000).
 * 2. Grabs uncompressed raster frame via JavaCV's FFmpegFrameGrabber.
 * 3. Converts to RGB BufferedImage via Java2DFrameConverter.
 * 4. Normalizes dimensions to H0 = 512, W0 = 512 and encodes as Base64 JPEG (v_k).
 */
@Service
public class FrameExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(FrameExtractionService.class);

    @Value("${visilink.rag.frame-size:512}")
    private int targetDimension; // 512x512 normalized spatial bounding

    /**
     * Extracts base64-encoded JPEG frames for an ordered list of deictic trigger timestamps.
     *
     * @param videoFile Input video file
     * @param timestamps List of timestamps in seconds (tau_k)
     * @return Map of timestamp -> Base64 encoded JPEG string (v_k)
     */
    public Map<Double, String> extractFrames(File videoFile, List<Double> timestamps) {
        if (!videoFile.exists() || timestamps == null || timestamps.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Double, String> extractedFrames = new LinkedHashMap<>();

        try {
            extractWithJavaCV(videoFile, timestamps, extractedFrames);
        } catch (Throwable t) {
            logger.warn("Native JavaCV frame grabber failed: {}. Falling back to FFmpeg subprocess...", t.getMessage());
            extractWithFfmpegProcess(videoFile, timestamps, extractedFrames);
        }

        // Ensure all timestamps are captured; fill any gaps with fallback
        for (Double tau : timestamps) {
            if (!extractedFrames.containsKey(tau)) {
                extractWithFfmpegProcess(videoFile, List.of(tau), extractedFrames);
            }
        }

        logger.info("Targeted Keyframe Extraction: Retrieved {} frames across {} trigger timestamps",
                extractedFrames.size(), timestamps.size());
        return extractedFrames;
    }

    /**
     * Extracts a single frame on demand (e.g., when the user asks a timestamp-specific question).
     */
    public String extractSingleFrame(File videoFile, double timestampSeconds) {
        Map<Double, String> frames = extractFrames(videoFile, List.of(timestampSeconds));
        return frames.getOrDefault(timestampSeconds, null);
    }

    /**
     * Native JavaCV Microsecond Timestamp Seeking implementation (Section 3.4, Eq. 5).
     */
    private void extractWithJavaCV(File videoFile, List<Double> timestamps, Map<Double, String> outputMap) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();
            Java2DFrameConverter converter = new Java2DFrameConverter();

            for (Double tau : timestamps) {
                // Section 3.4, Eq. 5: t_mu = (long)(tau_k * 1,000,000)
                long tMicroseconds = (long) (tau * 1_000_000L);
                grabber.setTimestamp(tMicroseconds);

                Frame frame = grabber.grabImage();
                if (frame != null) {
                    BufferedImage originalImage = converter.convert(frame);
                    if (originalImage != null) {
                        BufferedImage normalizedImage = resizeImage(originalImage, targetDimension, targetDimension);
                        String base64Jpeg = convertToBase64Jpeg(normalizedImage);
                        outputMap.put(tau, base64Jpeg);
                    }
                }
            }
            grabber.stop();
        }
    }

    /**
     * Fallback subprocess frame extraction.
     */
    private void extractWithFfmpegProcess(File videoFile, List<Double> timestamps, Map<Double, String> outputMap) {
        for (Double tau : timestamps) {
            File tempFrame = null;
            try {
                tempFrame = File.createTempFile("frame_" + tau + "_", ".jpg");
                ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg", "-y", "-ss", String.valueOf(tau),
                        "-i", videoFile.getAbsolutePath(),
                        "-vframes", "1",
                        "-vf", "scale='min(" + targetDimension + ",iw)':-1",
                        "-q:v", "2",
                        tempFrame.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();

                if (tempFrame.exists() && tempFrame.length() > 0) {
                    byte[] bytes = Files.readAllBytes(tempFrame.toPath());
                    String b64 = Base64.getEncoder().encodeToString(bytes);
                    outputMap.put(tau, b64);
                }
            } catch (Exception e) {
                logger.error("Failed to extract frame at {}s via fallback FFmpeg: {}", tau, e.getMessage());
            } finally {
                if (tempFrame != null && tempFrame.exists()) {
                    tempFrame.delete();
                }
            }
        }
    }

    /**
     * Resizes the image to normalized dimensions (H0 = 512, W0 = 512) while preserving clarity.
     */
    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // Calculate aspect ratio preserving dimensions within target bounds
        double ratio = Math.min((double) targetWidth / originalWidth, (double) targetHeight / originalHeight);
        int newWidth = (int) (originalWidth * ratio);
        int newHeight = (int) (originalHeight * ratio);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resized;
    }

    /**
     * Encodes BufferedImage to Base64 JPEG string.
     */
    private String convertToBase64Jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
