package com.visilink.rag.service;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * Stage 1: Audio Demuxing & Normalization Engine.
 *
 * Implements Section 3.1 of the VisiLink-RAG paper:
 * Demuxes and normalizes audio stream into MP3 format with fixed sampling rate
 * F_s = 44.1 kHz and bit rate 192 kbps using native JavaCV (FFmpeg 6.1) bindings.
 */
@Service
public class AudioExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(AudioExtractionService.class);

    private static final int SAMPLE_RATE = 44100; // 44.1 kHz
    private static final int BIT_RATE = 192000;   // 192 kbps
    private static final int CHANNELS = 2;        // Stereo

    /**
     * Extracts and normalizes audio track from video file into an MP3 file.
     *
     * @param videoFile Input video file
     * @return Normalized temporary MP3 File
     * @throws IOException If demuxing fails
     */
    public File extractAudio(File videoFile) throws IOException {
        if (!videoFile.exists() || videoFile.length() == 0) {
            throw new IOException("Input video file does not exist or is empty: " + videoFile.getAbsolutePath());
        }

        File tempMp3 = File.createTempFile("visilink_audio_", ".mp3");
        tempMp3.deleteOnExit();

        try {
            extractWithJavaCV(videoFile, tempMp3);
            logger.info("Successfully demuxed audio using native JavaCV bindings to {}", tempMp3.getAbsolutePath());
            return optimizeAudioSize(tempMp3);
        } catch (Throwable t) {
            logger.warn("Native JavaCV demuxing encountered exception: {}. Falling back to system FFmpeg binary...", t.getMessage());
            extractWithFfmpegProcess(videoFile, tempMp3);
            return optimizeAudioSize(tempMp3);
        }
    }

    /**
     * Demuxes audio via native JavaCV FFmpeg bindings.
     */
    private void extractWithJavaCV(File videoFile, File outputFile) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFile, CHANNELS)) {
                recorder.setSampleRate(SAMPLE_RATE);
                recorder.setAudioBitrate(BIT_RATE);
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_MP3);
                recorder.setFormat("mp3");
                recorder.start();

                Frame frame;
                while ((frame = grabber.grabSamples()) != null) {
                    recorder.record(frame);
                }

                recorder.stop();
            }
            grabber.stop();
        }
    }

    /**
     * Fallback execution using native system ffmpeg CLI if JNI dynamic loader misses local C++ runtimes.
     */
    private void extractWithFfmpegProcess(File videoFile, File outputFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-i", videoFile.getAbsolutePath(),
                "-vn", "-ar", String.valueOf(SAMPLE_RATE),
                "-ab", "192k", "-acodec", "libmp3lame",
                outputFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0 || !outputFile.exists() || outputFile.length() == 0) {
                throw new IOException("FFmpeg process failed with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg audio extraction interrupted", e);
        }
    }

    /**
     * Optimizes audio file size if it exceeds the 12MB threshold,
     * downsampling to 16 kHz mono 48kbps so it fits within Gemini inline_data limits.
     */
    private File optimizeAudioSize(File audioFile) {
        long thresholdBytes = 12 * 1024 * 1024L; // 12 MB
        if (!audioFile.exists() || audioFile.length() <= thresholdBytes) {
            return audioFile;
        }

        logger.info("Audio file size ({} MB) exceeds 12MB. Downsampling to 16 kHz mono 48kbps for ASR API compatibility...",
                audioFile.length() / (1024 * 1024));

        try {
            File compressedMp3 = File.createTempFile("visilink_compressed_", ".mp3");
            compressedMp3.deleteOnExit();

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", audioFile.getAbsolutePath(),
                    "-ar", "16000", "-ac", "1", "-ab", "48k",
                    compressedMp3.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();

            if (compressedMp3.exists() && compressedMp3.length() > 0 && compressedMp3.length() < audioFile.length()) {
                audioFile.delete();
                logger.info("Audio successfully compressed to {} MB", compressedMp3.length() / (1024 * 1024));
                return compressedMp3;
            }
        } catch (Exception e) {
            logger.warn("Audio compression encountered exception: {}. Continuing with original audio.", e.getMessage());
        }

        return audioFile;
    }
}
