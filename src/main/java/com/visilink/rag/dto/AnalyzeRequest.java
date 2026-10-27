package com.visilink.rag.dto;

public class AnalyzeRequest {
    private String url;
    private String engine = "gemini";

    public AnalyzeRequest() {
    }

    public AnalyzeRequest(String url, String engine) {
        this.url = url;
        this.engine = engine;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }
}
