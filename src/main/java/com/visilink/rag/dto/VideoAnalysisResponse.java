package com.visilink.rag.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.visilink.rag.model.VideoAnalysis;

import java.time.LocalDateTime;

public class VideoAnalysisResponse {

    private Long id;

    @JsonProperty("youtube_url")
    private String youtubeUrl;

    @JsonProperty("video_title")
    private String videoTitle;

    private String transcript;
    private String summary;

    @JsonProperty("visual_analyses")
    private String visualAnalyses;

    @JsonProperty("video_path")
    private String videoPath;

    private String engine;

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public VideoAnalysisResponse() {
    }

    public VideoAnalysisResponse(VideoAnalysis entity) {
        if (entity != null) {
            this.id = entity.getId();
            this.youtubeUrl = entity.getYoutubeUrl();
            this.videoTitle = entity.getVideoTitle();
            this.transcript = entity.getTranscript();
            this.summary = entity.getSummary();
            this.visualAnalyses = entity.getVisualAnalyses();
            this.videoPath = entity.getVideoPath();
            this.engine = entity.getEngine();
            this.createdAt = entity.getCreatedAt();
        }
    }

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
