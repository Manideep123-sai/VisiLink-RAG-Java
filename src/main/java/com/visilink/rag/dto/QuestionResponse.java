package com.visilink.rag.dto;

public class QuestionResponse {
    private String answer;
    private boolean gatedOut;
    private double similarityScore;
    private Double retrievedTimestamp;

    public QuestionResponse() {
    }

    public QuestionResponse(String answer) {
        this.answer = answer;
        this.gatedOut = false;
    }

    public QuestionResponse(String answer, boolean gatedOut, double similarityScore, Double retrievedTimestamp) {
        this.answer = answer;
        this.gatedOut = gatedOut;
        this.similarityScore = similarityScore;
        this.retrievedTimestamp = retrievedTimestamp;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public boolean isGatedOut() {
        return gatedOut;
    }

    public void setGatedOut(boolean gatedOut) {
        this.gatedOut = gatedOut;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public Double getRetrievedTimestamp() {
        return retrievedTimestamp;
    }

    public void setRetrievedTimestamp(Double retrievedTimestamp) {
        this.retrievedTimestamp = retrievedTimestamp;
    }
}
