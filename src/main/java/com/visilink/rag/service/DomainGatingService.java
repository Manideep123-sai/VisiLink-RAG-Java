package com.visilink.rag.service;

import com.visilink.rag.dto.DomainGatingResult;
import com.visilink.rag.model.MultimodalChunk;
import com.visilink.rag.util.VectorMathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Stage 5: Adaptive Operational Relevance Gating Engine (theta = 0.5).
 *
 * Implements Section 3.6 of the VisiLink-RAG paper (grounded in Meng & Tong 2025):
 * 1. Computes cosine similarity across unified multimodal embeddings.
 * 2. Compares maximum similarity against empirical threshold theta = 0.5.
 * 3. Suppresses out-of-domain queries to prevent technical hallucinations.
 * 4. Ranks and retrieves Top-K multimodal chunks for verified synthesis.
 */
@Service
public class DomainGatingService {

    private static final Logger logger = LoggerFactory.getLogger(DomainGatingService.class);

    @Value("${visilink.rag.gating-threshold:0.5}")
    private double gatingThreshold = 0.5; // Operational relevance threshold theta = 0.5

    private final MultimodalEmbeddingService embeddingService;

    public DomainGatingService(MultimodalEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public double getGatingThreshold() {
        return gatingThreshold;
    }

    public void setGatingThreshold(double gatingThreshold) {
        this.gatingThreshold = gatingThreshold;
    }

    /**
     * Evaluates query against indexed multimodal chunks under operational gating threshold theta = 0.5.
     *
     * @param query User's engineering question
     * @param indexedChunks Stored multimodal chunks for the video
     * @param topK Number of chunks to retrieve if within domain
     * @return DomainGatingResult containing gating decision, similarity scores, and retrieved chunks
     */
    public DomainGatingResult evaluateGating(String query, List<MultimodalChunk> indexedChunks, int topK) {
        if (indexedChunks == null || indexedChunks.isEmpty()) {
            return new DomainGatingResult(
                    false, 0.0, gatingThreshold, Collections.emptyList(),
                    "No multimodal context indexed for this video."
            );
        }

        float[] queryEmbedding = embeddingService.encodeQuery(query);

        double maxSimilarity = -1.0;
        List<MultimodalChunk> scoredChunks = new ArrayList<>();

        for (MultimodalChunk chunk : indexedChunks) {
            float[] chunkEmbedding = chunk.getMultimodalEmbedding();
            if (chunkEmbedding == null || chunkEmbedding.length == 0) {
                chunkEmbedding = embeddingService.generateUnifiedEmbedding(chunk);
                chunk.setMultimodalEmbedding(chunkEmbedding);
            }

            double similarity = VectorMathUtils.cosineSimilarity(queryEmbedding, chunkEmbedding);
            chunk.setSimilarityScore(similarity);
            scoredChunks.add(chunk);

            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
            }
        }

        // Section 3.6: Gating decision
        if (maxSimilarity < gatingThreshold) {
            logger.warn("Operational Relevance Gating: Query '{}' rejected with max similarity {} < theta={}",
                    query, String.format("%.3f", maxSimilarity), gatingThreshold);

            String guidance = String.format(
                    "Out-of-Domain Query Detected: The highest cosine similarity across video moments was %.3f " +
                    "(operational relevance threshold theta = %.2f). To prevent technical hallucination, " +
                    "retrieval has been gated. Please formulate a question relevant to the topics and formulas " +
                    "demonstrated in this instructional lecture.",
                    maxSimilarity, gatingThreshold
            );

            return new DomainGatingResult(false, maxSimilarity, gatingThreshold, Collections.emptyList(), guidance);
        }

        // Rank by cosine similarity descending
        scoredChunks.sort(Comparator.comparingDouble(MultimodalChunk::getSimilarityScore).reversed());
        List<MultimodalChunk> topChunks = scoredChunks.subList(0, Math.min(topK, scoredChunks.size()));

        logger.info("Operational Relevance Gating: Query '{}' accepted with max similarity {} >= theta={}. Retrieved {} top chunks.",
                query, String.format("%.3f", maxSimilarity), gatingThreshold, topChunks.size());

        return new DomainGatingResult(true, maxSimilarity, gatingThreshold, topChunks, "In domain. Retrieval approved.");
    }
}
