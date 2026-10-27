package com.visilink.rag.util;

import java.util.Arrays;

/**
 * Mathematical utilities for vector projections, linear multimodal fusion,
 * and cosine similarity calculations as formalized in Section 3.5 & 3.6 of the VisiLink-RAG paper.
 */
public class VectorMathUtils {

    /**
     * Computes the cosine similarity between two high-dimensional vectors:
     * Sim(u, v) = (u . v) / (||u|| * ||v||)
     */
    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length == 0 || vectorB.length == 0) {
            return 0.0;
        }

        int length = Math.min(vectorA.length, vectorB.length);
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA <= 1e-9 || normB <= 1e-9) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Performs linear multimodal feature fusion:
     * e_k^(TV) = alpha * e_k^T + beta * e_k^V
     * satisfying alpha + beta = 1 (Section 3.5, Eq. 7).
     */
    public static float[] linearFusion(float[] textEmbedding, float[] visualEmbedding, float alpha, float beta) {
        if (textEmbedding == null && visualEmbedding == null) {
            return new float[0];
        }
        if (textEmbedding == null) {
            return Arrays.copyOf(visualEmbedding, visualEmbedding.length);
        }
        if (visualEmbedding == null) {
            return Arrays.copyOf(textEmbedding, textEmbedding.length);
        }

        int dim = Math.max(textEmbedding.length, visualEmbedding.length);
        float[] fused = new float[dim];

        for (int i = 0; i < dim; i++) {
            float t = (i < textEmbedding.length) ? textEmbedding[i] : 0.0f;
            float v = (i < visualEmbedding.length) ? visualEmbedding[i] : 0.0f;
            fused[i] = alpha * t + beta * v;
        }

        return normalize(fused);
    }

    /**
     * Normalizes a vector to unit length (L2 norm).
     */
    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }

        double sumSquares = 0.0;
        for (float val : vector) {
            sumSquares += val * val;
        }

        double norm = Math.sqrt(sumSquares);
        if (norm <= 1e-9) {
            return vector;
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }

        return normalized;
    }
}
