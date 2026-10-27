package com.visilink.rag.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA Entity mapping the video analysis session to relational storage (H2 / MySQL 8+).
 */
@Entity
@Table(name = "video_analyses")
public class VideoAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "youtube_url", length = 512)
    private String youtubeUrl;

    @Column(name = "video_title", length = 512)
    private String videoTitle;

    @Lob
    @Column(name = "transcript", columnDefinition = "LONGTEXT")
    private String transcript;

    @Lob
    @Column(name = "summary", columnDefinition = "LONGTEXT")
    private String summary;

    @Lob
    @Column(name = "visual_analyses", columnDefinition = "LONGTEXT")
    private String visualAnalyses;

    @Column(name = "video_path", length = 1024)
    private String videoPath;

    @Column(name = "engine", length = 64)
    private String engine = "gemini";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public VideoAnalysis() {
    }

    public VideoAnalysis(String youtubeUrl, String videoTitle, String transcript, String summary,
                         String visualAnalyses, String videoPath, String engine) {
        this.youtubeUrl = youtubeUrl;
        this.videoTitle = videoTitle;
        this.transcript = transcript;
        this.summary = summary;
        this.visualAnalyses = visualAnalyses;
        this.videoPath = videoPath;
        this.engine = engine;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public void setYoutubeUrl(String youtubeUrl) {
        this.youtubeUrl = youtubeUrl;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getVisualAnalyses() {
        return visualAnalyses;
    }

    public void setVisualAnalyses(String visualAnalyses) {
        this.visualAnalyses = visualAnalyses;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
