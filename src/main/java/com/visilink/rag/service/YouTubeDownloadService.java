package com.visilink.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.UUID;

/**
 * Handles YouTube video ingestion and audio stream extraction using yt-dlp.
 */
@Service
public class YouTubeDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeDownloadService.class);

    @Value("${visilink.rag.video-storage-dir:data/videos}")
    private String videoStorageDir;

    public record DownloadResult(File videoFile, String title) {}

    /**
     * Downloads YouTube video and stores persistently for on-demand frame extraction.
     */
    public DownloadResult downloadVideo(String youtubeUrl) throws Exception {
        File storageDir = new File(videoStorageDir);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        String videoId = "yt_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        File outputFile = new File(storageDir, videoId + ".mp4");

        // First fetch title
        String title = fetchVideoTitle(youtubeUrl);

        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                "--merge-output-format", "mp4",
                "-o", outputFile.getAbsolutePath(),
                "--no-playlist",
                youtubeUrl
        );
        pb.redirectErrorStream(true);

        logger.info("Executing yt-dlp download for URL: {}", youtubeUrl);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("[yt-dlp] {}", line);
            }
        }

        int exitCode = process.waitFor();

        // Check if output file was written directly or with slightly different extension
        if (!outputFile.exists() || outputFile.length() == 0) {
            File[] matches = storageDir.listFiles((dir, name) -> name.startsWith(videoId));
            if (matches != null && matches.length > 0 && matches[0].length() > 0) {
                outputFile = matches[0];
            } else {
                throw new RuntimeException("yt-dlp failed to download video. Exit code: " + exitCode);
            }
        }

        logger.info("Successfully downloaded YouTube video to {}", outputFile.getAbsolutePath());
        return new DownloadResult(outputFile, title);
    }

    private String fetchVideoTitle(String youtubeUrl) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "yt-dlp", "--get-title", "--no-playlist", youtubeUrl
            );
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String title = reader.readLine();
                if (title != null && !title.isBlank()) {
                    return title.trim();
                }
            }
            process.waitFor();
        } catch (Exception e) {
            logger.warn("Could not retrieve YouTube video title: {}", e.getMessage());
        }
        return "YouTube Video (" + System.currentTimeMillis() + ")";
    }
}
