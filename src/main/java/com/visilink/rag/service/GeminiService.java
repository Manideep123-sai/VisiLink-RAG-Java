package com.visilink.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visilink.rag.dto.TranscriptSegmentDto;
import com.visilink.rag.model.MultimodalChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

/**
 * AI Integration Layer: Interfacing with Google Gemini 2.5 Flash Multimodal Endpoints.
 *
 * Implements:
 * 1. Base64 ASR Audio Transcription generating time-aligned segments.
 * 2. Visual Semantic Extraction I_k = MLLM_interpret(v_k, context = w_k).
 * 3. Structured Markdown summary generation with comparison tables.
 * 4. Multimodal reasoning Q&A synthesis with verified timestamp citations.
 */
@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String modelName;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Transcribes audio track using Gemini multimodal audio ingestion into time-aligned segments.
     *
     * @param audioFile MP3 audio file
     * @return Ordered list of time-aligned transcript segments
     */
    public List<TranscriptSegmentDto> transcribeAudio(File audioFile) throws IOException {
        byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

        String prompt = """
                Transcribe the provided audio into time-aligned speech segments.
                Output ONLY a JSON array of segment objects in the following exact format:
                [
                  {"text": "...", "start_time_in_seconds": 0.0, "end_time_in_seconds": 4.5},
                  {"text": "...", "start_time_in_seconds": 4.5, "end_time_in_seconds": 9.2}
                ]
                Keep segments small (around 5 to 10 seconds of speech).
                Do not include markdown formatting or backticks around the JSON.
                """;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt),
                                Map.of("inline_data", Map.of(
                                        "mime_type", "audio/mp3",
                                        "data", base64Audio
                                ))
                        ))
                )
        );

        String rawResponse = callGeminiWithRetry(requestBody);
        return parseTranscriptSegments(rawResponse);
    }

    /**
     * Interprets a visual keyframe conditioned on surrounding speech context:
     * I_k = MLLM_interpret(v_k, context = w_k) (Section 3.4, Eq. 6).
     *
     * @param base64Image JPEG image encoded in base64
     * @param context Surrounding transcript text
     * @param timestamp Timestamp in seconds
     * @return Semantic natural language interpretation of formulas, diagrams, and figures
     */
    public String interpretFrame(String base64Image, String context, double timestamp) {
        int minutes = (int) (timestamp / 60);
        int seconds = (int) (timestamp % 60);
        String timeStr = String.format("[%02d:%02d]", minutes, seconds);

        String prompt = String.format(
                "At %s the speaker said: '%s'. " +
                "Analyze what is shown on screen — identify any graphs, mathematical equations, " +
                "structural formulas, schematic diagrams, or visual data and explain it in technical context.",
                timeStr, (context != null ? context : "")
        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt),
                                Map.of("inline_data", Map.of(
                                        "mime_type", "image/jpeg",
                                        "data", base64Image
                                ))
                        ))
                )
        );

        try {
            return callGeminiWithRetry(requestBody);
        } catch (Exception e) {
            logger.error("Failed to interpret frame at {}s: {}", timestamp, e.getMessage());
            return "Visual frame at " + timeStr + " captured.";
        }
    }

    /**
     * Generates a comprehensive structured summary with a Markdown comparison table
     * for visual content identified in the video.
     */
    public String generateStructuredSummary(String transcriptJson, Map<Double, String> visualAnalyses) {
        StringBuilder visualContext = new StringBuilder();
        if (visualAnalyses != null && !visualAnalyses.isEmpty()) {
            visualContext.append("\nVisual Content Identified in Video (from analyzed keyframes):\n");
            for (Map.Entry<Double, String> entry : visualAnalyses.entrySet()) {
                int m = (int) (entry.getKey() / 60);
                int s = (int) (entry.getKey() % 60);
                visualContext.append(String.format("- At %02d:%02d: %s\n", m, s, entry.getValue()));
            }
        }

        String prompt = String.format("""
                You are an expert technical assistant. Summarize the following instructional video content.
                Generate the summary using standard Markdown. Use bolding, bullet points, and headers for clarity.

                CRITICAL CONSTRAINTS:
                - Do NOT output reasoning or internal model notes.
                - Output ONLY the formatted summary.
                - Use MM:SS format for all timestamps.
                - You MUST include a Markdown comparison table with columns: [Timestamp, Visual Element, Analysis/Description].

                Required Structure:
                # [VIDEO TITLE]

                ## OVERVIEW
                [Brief technical overview here]

                ## KEY TOPICS
                - [Topic 1]
                - [Topic 2]

                ## VISUAL CONTENT (TABLE)
                | Timestamp | Visual Element | Analysis/Description |
                |-----------|---------------|-----------------------|
                | [MM:SS]   | [Element]     | [Detail]              |

                ## IMPORTANT POINTS
                - [Point 1]
                - [Point 2]

                Transcript:
                %s
                %s
                """, transcriptJson, visualContext.toString());

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        return callGeminiWithRetry(requestBody);
    }

    /**
     * Synthesizes reasoning-enabled answer grounded in retrieved multimodal context (Eq. 9).
     */
    public String generateReasoningAnswer(String question, String transcript,
                                          List<MultimodalChunk> contextChunks,
                                          String onDemandVisual, String onDemandAudio,
                                          Double detectedSeconds) {

        StringBuilder contextBuilder = new StringBuilder();
        if (contextChunks != null && !contextChunks.isEmpty()) {
            contextBuilder.append("\nRetrieved Multimodal Video Context (Top-K Triggered Moments):\n");
            for (MultimodalChunk chunk : contextChunks) {
                int m = (int) (chunk.getTimestampSeconds() / 60);
                int s = (int) (chunk.getTimestampSeconds() % 60);
                contextBuilder.append(String.format("Moment [%02d:%02d] (Cosine Similarity: %.3f):\n",
                        m, s, chunk.getSimilarityScore()));
                contextBuilder.append("  Spoken: ").append(chunk.getTranscriptText()).append("\n");
                contextBuilder.append("  Visual Diagram: ").append(chunk.getVisualInterpretation()).append("\n\n");
            }
        }

        String timestampNotice = (detectedSeconds != null) ?
                String.format("TIMESTAMP QUERY DETECTED: The user specifically asked about %d seconds into the video. Ground your answer primarily on this moment.", detectedSeconds.intValue())
                : "Answer the question using the retrieved multimodal context below.";

        String prompt = String.format("""
                You are VisiLink-RAG, an advanced multimodal instructional video question answering system.
                Answer the user's question accurately, eliminating deictic ambiguity and resolving any on-screen visual references.

                %s

                CRITICAL INSTRUCTIONS:
                1. Explicitly cite the exact timestamp [MM:SS] where formulas, diagrams, or explanations occur.
                2. If the user refers to 'this diagram', 'this equation', or 'here', resolve it using the on-screen visual analysis.
                3. Do NOT hallucinate mathematical derivations; use the verified formulas extracted from the visual board.

                %s

                %s
                %s

                FULL TRANSCRIPT SUMMARY:
                %s

                USER QUESTION:
                %s
                """,
                timestampNotice,
                contextBuilder.toString(),
                (onDemandAudio != null ? onDemandAudio : ""),
                (onDemandVisual != null ? onDemandVisual : ""),
                (transcript.length() > 4000 ? transcript.substring(0, 4000) + "..." : transcript),
                question
        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        return callGeminiWithRetry(requestBody);
    }

    /**
     * Executes HTTP POST against Google Gemini REST endpoint with exponential backoff on 429.
     */
    private String callGeminiWithRetry(Map<String, Object> requestPayload) {
        String endpoint = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                modelName, apiKey
        );

        int maxRetries = 4;
        long baseWaitMillis = 3000;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String jsonBody = objectMapper.writeValueAsString(requestPayload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(90))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
                    if (!textNode.isMissingNode()) {
                        return textNode.asText();
                    }
                    return response.body();
                } else if (response.statusCode() == 429) {
                    long waitTime = baseWaitMillis * (1L << attempt);
                    logger.warn("Gemini API rate limit (429). Retrying in {}ms (Attempt {}/{})",
                            waitTime, attempt + 1, maxRetries);
                    Thread.sleep(waitTime);
                } else {
                    logger.error("Gemini API returned HTTP {}: {}", response.statusCode(), response.body());
                    if (attempt == maxRetries - 1) {
                        throw new RuntimeException("Gemini API request failed with status " + response.statusCode());
                    }
                    Thread.sleep(1500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Gemini API call interrupted", e);
            } catch (Exception e) {
                logger.error("Error communicating with Gemini: {}", e.getMessage());
                if (attempt == maxRetries - 1) {
                    throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
                }
            }
        }

        throw new RuntimeException("Gemini API failed after " + maxRetries + " retries.");
    }

    private List<TranscriptSegmentDto> parseTranscriptSegments(String responseText) {
        try {
            String cleanJson = responseText.replaceAll("```json", "").replaceAll("```", "").trim();
            int startIndex = cleanJson.indexOf('[');
            int endIndex = cleanJson.lastIndexOf(']');
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                cleanJson = cleanJson.substring(startIndex, endIndex + 1);
            }
            return objectMapper.readValue(cleanJson, new TypeReference<List<TranscriptSegmentDto>>() {});
        } catch (Exception e) {
            logger.warn("Failed to parse segment JSON array: {}. Falling back to single-block segmentation.", e.getMessage());
            return List.of(new TranscriptSegmentDto(responseText.trim(), 0.0, 30.0));
        }
    }
}
