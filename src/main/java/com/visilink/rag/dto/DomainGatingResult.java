package com.visilink.rag.dto;

import com.visilink.rag.model.MultimodalChunk;

import java.util.List;

/**
 * Result of the Operational Relevance Gating evaluation (theta = 0.5)
 * as defined in Section 3.6 of the VisiLink-RAG paper.
 */
public class DomainGatingResult {

    private boolean inDomain;
    private double maxCosineSimilarity;
    private double gatingThreshold = 0.5;
    private List<MultimodalChunk> relevantChunks;
    private String guidanceMessage;

    public DomainGatingResult() {
    }

    public DomainGatingResult(boolean inDomain, double maxCosineSimilarity, double gatingThreshold,
                              List<MultimodalChunk> relevantChunks, String guidanceMessage) {
        this.inDomain = inDomain;
        this.maxCosineSimilarity = maxCosineSimilarity;
        this.gatingThreshold = gatingThreshold;
        this.relevantChunks = relevantChunks;
        this.guidanceMessage = guidanceMessage;
    }

    public boolean isInDomain() {
        return inDomain;
    }

    public void setInDomain(boolean inDomain) {
        this.inDomain = inDomain;
    }

    public double getMaxCosineSimilarity() {
        return maxCosineSimilarity;
    }

    public void setMaxCosineSimilarity(double maxCosineSimilarity) {
        this.maxCosineSimilarity = maxCosineSimilarity;
    }

    public double getGatingThreshold() {
        return gatingThreshold;
    }

    public void setGatingThreshold(double gatingThreshold) {
        this.gatingThreshold = gatingThreshold;
    }

    public List<MultimodalChunk> getRelevantChunks() {
        return relevantChunks;
    }

    public void setRelevantChunks(List<MultimodalChunk> relevantChunks) {
        this.relevantChunks = relevantChunks;
    }

    public String getGuidanceMessage() {
        return guidanceMessage;
    }

    public void setGuidanceMessage(String guidanceMessage) {
        this.guidanceMessage = guidanceMessage;
    }
}
