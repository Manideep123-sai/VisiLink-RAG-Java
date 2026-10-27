package com.visilink.rag.model;

import java.util.Arrays;

/**
 * Represents an augmented multimodal segment d_k^(aug) = {w_k, I_k, v_k}
 * as formulated in Section 3.4 of the VisiLink-RAG paper.
 */
public class MultimodalChunk {

    private double timestampSeconds;
    private String transcriptText;
    private String visualInterpretation;
    private String base64Image;
    private float[] multimodalEmbedding;
    private double similarityScore;

    public MultimodalChunk() {
    }

    public MultimodalChunk(double timestampSeconds, String transcriptText, String visualInterpretation, String base64Image) {
        this.timestampSeconds = timestampSeconds;
        this.transcriptText = transcriptText;
        this.visualInterpretation = visualInterpretation;
        this.base64Image = base64Image;
    }

    public double getTimestampSeconds() {
        return timestampSeconds;
    }

    public void setTimestampSeconds(double timestampSeconds) {
        this.timestampSeconds = timestampSeconds;
    }

    public String getTranscriptText() {
        return transcriptText;
    }

    public void setTranscriptText(String transcriptText) {
        this.transcriptText = transcriptText;
    }

    public String getVisualInterpretation() {
        return visualInterpretation;
    }

    public void setVisualInterpretation(String visualInterpretation) {
        this.visualInterpretation = visualInterpretation;
    }

    public String getBase64Image() {
        return base64Image;
    }

    public void setBase64Image(String base64Image) {
        this.base64Image = base64Image;
    }

    public float[] getMultimodalEmbedding() {
        return multimodalEmbedding;
    }

    public void setMultimodalEmbedding(float[] multimodalEmbedding) {
        this.multimodalEmbedding = multimodalEmbedding;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    @Override
    public String toString() {
        return "MultimodalChunk{" +
                "timestampSeconds=" + timestampSeconds +
                ", transcriptText='" + transcriptText + '\'' +
                ", visualInterpretation='" + visualInterpretation + '\'' +
                ", similarityScore=" + similarityScore +
                '}';
    }
}
