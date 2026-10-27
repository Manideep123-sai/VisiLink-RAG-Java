package com.visilink.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visilink.rag.dto.TranscriptSegmentDto;
import com.visilink.rag.service.DeicticTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DeicticTriggerServiceTest {

    private DeicticTriggerService deicticTriggerService;

    @BeforeEach
    public void setup() {
        deicticTriggerService = new DeicticTriggerService(new ObjectMapper());
    }

    @Test
    public void testDeicticCueDetection() {
        assertTrue(deicticTriggerService.isDeicticUtterance("Now look at this diagram on the board"));
        assertTrue(deicticTriggerService.isDeicticUtterance("As you can see, this equation models the deflection"));
        assertTrue(deicticTriggerService.isDeicticUtterance("Notice this curve right here"));
        assertFalse(deicticTriggerService.isDeicticUtterance("We will meet next Tuesday for the midterm review"));
    }

    @Test
    public void testTemporalDeduplicationRefractoryWindow() {
        List<TranscriptSegmentDto> segments = List.of(
                new TranscriptSegmentDto("Look at this diagram here", 15.0, 18.0),
                new TranscriptSegmentDto("Notice this curve in the diagram", 18.0, 22.0), // Within 10s of 15.0 -> deduplicated
                new TranscriptSegmentDto("Standard verbal explanation continues", 25.0, 30.0),
                new TranscriptSegmentDto("As shown in this equation right here", 40.0, 45.0)  // > 10s from 15.0 -> kept
        );

        List<Double> triggers = deicticTriggerService.detectTriggers(segments);

        assertEquals(2, triggers.size(), "Should deduplicate triggers occurring within refractory window");
        assertEquals(15.0, triggers.get(0));
        assertEquals(40.0, triggers.get(1));
    }
}
