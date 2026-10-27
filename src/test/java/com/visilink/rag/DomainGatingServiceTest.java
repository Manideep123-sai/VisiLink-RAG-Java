package com.visilink.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visilink.rag.dto.DomainGatingResult;
import com.visilink.rag.model.MultimodalChunk;
import com.visilink.rag.service.DomainGatingService;
import com.visilink.rag.service.MultimodalEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DomainGatingServiceTest {

    private DomainGatingService gatingService;
    private MultimodalEmbeddingService embeddingService;

    @BeforeEach
    public void setup() {
        embeddingService = new MultimodalEmbeddingService(new ObjectMapper());
        ReflectionTestUtils.setField(embeddingService, "alpha", 0.5f);
        ReflectionTestUtils.setField(embeddingService, "beta", 0.5f);

        gatingService = new DomainGatingService(embeddingService);
        ReflectionTestUtils.setField(gatingService, "gatingThreshold", 0.5);
    }

    @Test
    public void testInDomainQueryApproved() {
        MultimodalChunk chunk = new MultimodalChunk(14.0, "Cantilever beam bending moment formula", "Parabolic curve with M_max", null);
        chunk.setMultimodalEmbedding(embeddingService.encodeText("Cantilever beam bending moment formula"));

        DomainGatingResult result = gatingService.evaluateGating("What is the cantilever beam moment?", List.of(chunk), 1);
        assertTrue(result.isInDomain(), "Engineering query aligned with topic should pass gating threshold theta=0.5");
        assertFalse(result.getRelevantChunks().isEmpty());
    }

    @Test
    public void testOutOfDomainQueryRejected() {
        MultimodalChunk chunk = new MultimodalChunk(14.0, "Cantilever beam bending moment formula", "Parabolic curve with M_max", null);
        chunk.setMultimodalEmbedding(embeddingService.encodeText("Cantilever beam bending moment formula"));

        DomainGatingResult result = gatingService.evaluateGating("How do you cook chocolate chip cookies?", List.of(chunk), 1);
        assertFalse(result.isInDomain(), "Irrelevant out-of-domain query should be rejected under theta=0.5");
        assertTrue(result.getGuidanceMessage().contains("Out-of-Domain Query Detected"));
    }
}
