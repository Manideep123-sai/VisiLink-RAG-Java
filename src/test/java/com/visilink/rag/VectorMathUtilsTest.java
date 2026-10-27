package com.visilink.rag;

import com.visilink.rag.util.VectorMathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VectorMathUtilsTest {

    @Test
    public void testCosineSimilarityIdenticalVectors() {
        float[] a = new float[]{1.0f, 2.0f, 3.0f};
        float[] b = new float[]{1.0f, 2.0f, 3.0f};

        double sim = VectorMathUtils.cosineSimilarity(a, b);
        assertEquals(1.0, sim, 1e-5, "Cosine similarity of identical vectors must be 1.0");
    }

    @Test
    public void testCosineSimilarityOrthogonalVectors() {
        float[] a = new float[]{1.0f, 0.0f, 0.0f};
        float[] b = new float[]{0.0f, 1.0f, 0.0f};

        double sim = VectorMathUtils.cosineSimilarity(a, b);
        assertEquals(0.0, sim, 1e-5, "Cosine similarity of orthogonal vectors must be 0.0");
    }

    @Test
    public void testLinearMultimodalFusion() {
        float[] text = new float[]{1.0f, 0.0f};
        float[] vision = new float[]{0.0f, 1.0f};

        float[] fused = VectorMathUtils.linearFusion(text, vision, 0.5f, 0.5f);
        assertNotNull(fused);
        assertEquals(2, fused.length);

        // Fused vector normalized: [0.5, 0.5] normalized -> [1/sqrt(2), 1/sqrt(2)]
        assertEquals(1.0f / (float) Math.sqrt(2), fused[0], 1e-4);
        assertEquals(1.0f / (float) Math.sqrt(2), fused[1], 1e-4);
    }
}
