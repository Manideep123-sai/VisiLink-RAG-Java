package com.visilink.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visilink.rag.dto.TranscriptSegmentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Stage 2: Time-Aligned ASR & Deictic Linguistic Triggering Engine.
 *
 * Implements Section 3.3 of the VisiLink-RAG paper:
 * 1. Evaluates deictic detection function Psi(w_i) over lexical tokens.
 * 2. Matches curated spatial deictics, visual imperatives, and technical referents.
 * 3. Enforces temporal deduplication: tau_k - tau_(k-1) >= Delta_t_min (10.0s).
 */
@Service
public class DeicticTriggerService {

    private static final Logger logger = LoggerFactory.getLogger(DeicticTriggerService.class);

    @Value("${visilink.rag.refractory-window-seconds:10.0}")
    private double refractoryWindowSeconds = 10.0;

    private final ObjectMapper objectMapper;

    public void setRefractoryWindowSeconds(double refractoryWindowSeconds) {
        this.refractoryWindowSeconds = refractoryWindowSeconds;
    }

    public double getRefractoryWindowSeconds() {
        return refractoryWindowSeconds;
    }

    // Compiled multi-word deictic phrase patterns (Perceptual imperatives & spatial referents)
    private static final List<Pattern> DEICTIC_PHRASE_PATTERNS = List.of(
            Pattern.compile("\\b(look at|take a look|take a look at)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(as (you can |we can )?see|as shown|as depicted|as illustrated)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(notice|observe|note that|focus on)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(on the (board|screen|slide|display|monitor|diagram|chart|graph|left|right))\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(this (diagram|figure|equation|formula|curve|graph|plot|chart|table|schematic|slide|matrix|circuit))\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(that (diagram|figure|equation|formula|curve|graph|plot|chart|table|schematic|slide|matrix|circuit))\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(these (curves|graphs|plots|equations|values|parameters|diagrams))\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(here we (have|see|can see)|right here|over here|down here|up here)\\b", Pattern.CASE_INSENSITIVE)
    );

    // Individual demonstrative/spatial keywords combined with visual pointers
    private static final Set<String> SPATIAL_KEYWORDS = Set.of(
            "here", "there", "this", "that", "these", "those"
    );

    private static final Set<String> VISUAL_NOUNS = Set.of(
            "diagram", "chart", "graph", "curve", "formula", "equation",
            "board", "screen", "slide", "table", "circuit", "plot", "figure", "matrix"
    );

    public DeicticTriggerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates deictic detection function Psi(w_i) over lexical transcript chunks.
     * Applies temporal deduplication constraint (tau_k - tau_(k-1) >= Delta_t_min).
     *
     * @param segments List of time-aligned transcript segments
     * @return Ordered list of keyframe trigger timestamps in seconds
     */
    public List<Double> detectTriggers(List<TranscriptSegmentDto> segments) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Double> rawTimestamps = new ArrayList<>();

        for (TranscriptSegmentDto segment : segments) {
            String text = segment.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            if (isDeicticUtterance(text)) {
                rawTimestamps.add(segment.getStartTimeInSeconds());
            }
        }

        if (rawTimestamps.isEmpty()) {
            return Collections.emptyList();
        }

        // Apply temporal deduplication constraint: tau_k - tau_(k-1) >= Delta_t_min
        Collections.sort(rawTimestamps);
        List<Double> deduplicated = new ArrayList<>();
        deduplicated.add(rawTimestamps.get(0));

        for (int i = 1; i < rawTimestamps.size(); i++) {
            double current = rawTimestamps.get(i);
            double last = deduplicated.get(deduplicated.size() - 1);
            if (current - last >= refractoryWindowSeconds) {
                deduplicated.add(current);
            }
        }

        logger.info("Deictic Trigger Detection: Found {} triggers, deduplicated to {} keyframes (Delta_t_min = {}s)",
                rawTimestamps.size(), deduplicated.size(), refractoryWindowSeconds);
        return deduplicated;
    }

    /**
     * Parses JSON transcript chunks and detects deictic triggers.
     */
    public List<Double> detectTriggersFromJson(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<TranscriptSegmentDto> segments = objectMapper.readValue(
                    transcriptJson, new TypeReference<List<TranscriptSegmentDto>>() {}
            );
            return detectTriggers(segments);
        } catch (Exception e) {
            logger.error("Failed to parse transcript JSON for deictic trigger detection: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Implements Psi(w_i) = 1 if exists w in w_i : w in D_lex, else 0.
     */
    public boolean isDeicticUtterance(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        // 1. Check multi-word phrase patterns
        for (Pattern pattern : DEICTIC_PHRASE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }

        // 2. Co-occurrence test: Check if spatial keyword co-occurs with a visual noun
        String lower = text.toLowerCase(Locale.ROOT);
        boolean hasSpatial = SPATIAL_KEYWORDS.stream().anyMatch(lower::contains);
        boolean hasVisual = VISUAL_NOUNS.stream().anyMatch(lower::contains);

        return hasSpatial && hasVisual;
    }
}
