package com.visilink.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visilink.rag.dto.DomainGatingResult;
import com.visilink.rag.dto.QuestionResponse;
import com.visilink.rag.dto.TranscriptSegmentDto;
import com.visilink.rag.model.MultimodalChunk;
import com.visilink.rag.model.VideoAnalysis;
import com.visilink.rag.repository.VideoAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pipeline Orchestrator for VisiLink-RAG.
 *
 * Implements Section 4.1 & 4.3:
 * Coordinates end-to-end ingestion, transcription, deictic triggering,
 * JavaCV frame extraction, 4-worker CompletableFuture parallel interpretation,
 * multimodal vector fusion, and gated generation.
 */
@Service
public class VideoAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(VideoAnalysisService.class);

    private final AudioExtractionService audioExtractionService;
    private final FrameExtractionService frameExtractionService;
    private final DeicticTriggerService deicticTriggerService;
    private final GeminiService geminiService;
    private final MultimodalEmbeddingService embeddingService;
    private final DomainGatingService domainGatingService;
    private final YouTubeDownloadService youTubeDownloadService;
    private final VideoAnalysisRepository repository;
    private final ExecutorService executorService;
    private final ObjectMapper objectMapper;

    @Value("${visilink.rag.video-storage-dir:data/videos}")
    private String videoStorageDir;

    // Cache indexed multimodal chunks in memory for fast gated retrieval
    private final Map<Long, List<MultimodalChunk>> memoryIndexCache = new ConcurrentHashMap<>();

    public VideoAnalysisService(AudioExtractionService audioExtractionService,
                                FrameExtractionService frameExtractionService,
                                DeicticTriggerService deicticTriggerService,
                                GeminiService geminiService,
                                MultimodalEmbeddingService embeddingService,
                                DomainGatingService domainGatingService,
                                YouTubeDownloadService youTubeDownloadService,
                                VideoAnalysisRepository repository,
                                @Qualifier("frameAnalysisExecutor") ExecutorService executorService,
                                ObjectMapper objectMapper) {
        this.audioExtractionService = audioExtractionService;
        this.frameExtractionService = frameExtractionService;
        this.deicticTriggerService = deicticTriggerService;
        this.geminiService = geminiService;
        this.embeddingService = embeddingService;
        this.domainGatingService = domainGatingService;
        this.youTubeDownloadService = youTubeDownloadService;
        this.repository = repository;
        this.executorService = executorService;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the complete VisiLink-RAG analysis pipeline on a YouTube URL.
     */
    public VideoAnalysis processYouTubeVideo(String url, String engine) throws Exception {
        logger.info("Starting VisiLink-RAG analysis for YouTube URL: {}", url);

        // 1. Download YouTube video to persistent data directory
        YouTubeDownloadService.DownloadResult download = youTubeDownloadService.downloadVideo(url);
        File videoFile = download.videoFile();
        String title = download.title();

        return executePipeline(videoFile, url, title, engine);
    }

    /**
     * Executes the complete VisiLink-RAG analysis pipeline on an uploaded video file.
     */
    public VideoAnalysis processUploadedVideo(MultipartFile file, String engine) throws Exception {
        logger.info("Starting VisiLink-RAG analysis for uploaded file: {}", file.getOriginalFilename());

        File storageDir = new File(videoStorageDir);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        long count = repository.count() + 1;
        String safeName = (file.getOriginalFilename() != null ? file.getOriginalFilename() : "video.mp4");
        File persistentVideo = new File(storageDir, count + "_" + safeName);

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, persistentVideo.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        return executePipeline(persistentVideo, "local://" + safeName, safeName, engine);
    }

    /**
     * Core 5-Stage Orchestration Pipeline.
     */
    private VideoAnalysis executePipeline(File videoFile, String sourceUrl, String title, String engine) throws Exception {
        File audioFile = null;
        try {
            // Stage 1: Audio Demuxing (JavaCV FFmpeg bindings: 44.1 kHz, 192kbps MP3)
            logger.info("Stage 1: Demuxing audio from video...");
            audioFile = audioExtractionService.extractAudio(videoFile);

            // Stage 2: Time-Aligned ASR & Deictic Linguistic Triggering
            logger.info("Stage 2: Transcribing audio into time-aligned segments...");
            List<TranscriptSegmentDto> segments = geminiService.transcribeAudio(audioFile);
            String transcriptJson = objectMapper.writeValueAsString(segments);

            logger.info("Stage 2b: Evaluating deictic cue detection function Psi(w_i)...");
            List<Double> triggers = deicticTriggerService.detectTriggers(segments);

            // Stage 3: Targeted JavaCV Frame Extraction & Dual-Mode Decomposition
            Map<Double, String> visualAnalyses = new LinkedHashMap<>();
            List<MultimodalChunk> chunks = new ArrayList<>();

            if (triggers.isEmpty() && segments != null && !segments.isEmpty()) {
                double maxDuration = segments.get(segments.size() - 1).getEndTimeInSeconds();
                if (maxDuration > 20) {
                    triggers = List.of(maxDuration * 0.25, maxDuration * 0.50, maxDuration * 0.75);
                    logger.info("Applying baseline visual triggers at distributed timestamps: {}", triggers);
                }
            }

            if (!triggers.isEmpty()) {
                logger.info("Stage 3: Extracting {} keyframes via JavaCV microsecond seeking...", triggers.size());
                Map<Double, String> extractedFrames = frameExtractionService.extractFrames(videoFile, triggers);

                // Stage 3b: CompletableFuture 4-Worker Concurrency Pipeline (Section 4.3)
                logger.info("Stage 3b: Launching asynchronous frame analysis across 4 worker threads...");
                visualAnalyses = analyzeFramesConcurrently(extractedFrames, segments);

                // Stage 4: Cross-Modal Embedding & Unified Linear Fusion (e^TV = alpha*e^T + beta*e^V)
                logger.info("Stage 4: Generating aligned multimodal embeddings...");
                for (Map.Entry<Double, String> entry : visualAnalyses.entrySet()) {
                    double tau = entry.getKey();
                    String interpretation = entry.getValue();
                    String frameB64 = extractedFrames.get(tau);

                    String spokenContext = findSurroundingText(segments, tau, 15.0);
                    MultimodalChunk chunk = new MultimodalChunk(tau, spokenContext, interpretation, frameB64);
                    float[] fusedEmbedding = embeddingService.generateUnifiedEmbedding(chunk);
                    chunk.setMultimodalEmbedding(fusedEmbedding);
                    chunks.add(chunk);
                }
            } else {
                logger.info("No deictic visual triggers detected; proceeding with transcript representation.");
            }

            // Stage 5: Structured Markdown Summary Generation
            logger.info("Stage 5: Synthesizing structured summary with Markdown comparison tables...");
            String summary = geminiService.generateStructuredSummary(transcriptJson, visualAnalyses);

            String visualAnalysesJson = objectMapper.writeValueAsString(visualAnalyses);

            // Relational Persistence (Spring Data JPA)
            VideoAnalysis entity = new VideoAnalysis(
                    sourceUrl, title, transcriptJson, summary, visualAnalysesJson,
                    videoFile.getAbsolutePath(), engine
            );
            entity = repository.save(entity);

            // Cache in-memory multimodal index for instant low-latency Q&A
            memoryIndexCache.put(entity.getId(), chunks);

            logger.info("VisiLink-RAG pipeline successfully completed for Analysis ID: {}", entity.getId());
            return entity;

        } finally {
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
            }
        }
    }

    /**
     * Executes non-blocking parallel frame interpretation using CompletableFuture (Section 4.3).
     */
    private Map<Double, String> analyzeFramesConcurrently(Map<Double, String> frames, List<TranscriptSegmentDto> segments) {
        Map<Double, String> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<Double, String> entry : frames.entrySet()) {
            double tau = entry.getKey();
            String b64 = entry.getValue();
            String spokenContext = findSurroundingText(segments, tau, 15.0);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String analysis = geminiService.interpretFrame(b64, spokenContext, tau);
                results.put(tau, analysis);
            }, executorService);

            futures.add(future);
        }

        // Synchronize all parallel worker threads
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return new TreeMap<>(results);
    }

    /**
     * Resolves Questions using Operational Relevance Gating (theta = 0.5) and Deictic Grounding.
     */
    public QuestionResponse answerQuestion(Long analysisId, String question) {
        VideoAnalysis analysis = repository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found for ID: " + analysisId));

        List<MultimodalChunk> chunks = memoryIndexCache.computeIfAbsent(analysisId, id -> rebuildChunks(analysis));

        // 1. Detect optional timestamp pointer in user query (e.g., "at 14:18" or "minute 5")
        Double detectedSeconds = parseTimestampFromQuery(question);
        String onDemandVisual = null;
        String onDemandAudio = null;

        if (detectedSeconds != null && analysis.getVideoPath() != null) {
            File videoFile = new File(analysis.getVideoPath());
            if (videoFile.exists()) {
                logger.info("On-Demand Frame Extraction triggered for query at {}s", detectedSeconds);
                String freshFrame = frameExtractionService.extractSingleFrame(videoFile, detectedSeconds);
                List<TranscriptSegmentDto> segments = parseSegmentsSafe(analysis.getTranscript());
                String windowContext = findSurroundingText(segments, detectedSeconds, 30.0);

                if (freshFrame != null) {
                    String visualInterpretation = geminiService.interpretFrame(freshFrame, windowContext, detectedSeconds);
                    onDemandVisual = String.format("\nON-DEMAND FRAME ANALYSIS at %.1fs:\n%s", detectedSeconds, visualInterpretation);
                }
                onDemandAudio = String.format("\nAUDIO NEAR %.1fs (±30s):\n%s", detectedSeconds, windowContext);
            }
        }

        // 2. Operational Relevance Gating (theta = 0.5, Section 3.6)
        DomainGatingResult gatingResult = domainGatingService.evaluateGating(question, chunks, 3);

        if (!gatingResult.isInDomain() && detectedSeconds == null) {
            // Out of domain rejection prevents technical hallucination
            return new QuestionResponse(
                    gatingResult.getGuidanceMessage(),
                    true,
                    gatingResult.getMaxCosineSimilarity(),
                    null
            );
        }

        // 3. Grounded Synthesis via Reasoning MLLM
        String answer = geminiService.generateReasoningAnswer(
                question,
                analysis.getTranscript(),
                gatingResult.getRelevantChunks(),
                onDemandVisual,
                onDemandAudio,
                detectedSeconds
        );

        Double topTimestamp = (gatingResult.getRelevantChunks() != null && !gatingResult.getRelevantChunks().isEmpty()) ?
                gatingResult.getRelevantChunks().get(0).getTimestampSeconds() : detectedSeconds;

        return new QuestionResponse(answer, false, gatingResult.getMaxCosineSimilarity(), topTimestamp);
    }

    private String findSurroundingText(List<TranscriptSegmentDto> segments, double timestamp, double windowSeconds) {
        if (segments == null) return "";
        StringBuilder sb = new StringBuilder();
        for (TranscriptSegmentDto s : segments) {
            if (Math.abs(s.getStartTimeInSeconds() - timestamp) <= windowSeconds) {
                sb.append(s.getText()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private Double parseTimestampFromQuery(String query) {
        Pattern pattern = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?|(\\d+)\\s*minute|(\\d+)\\s*second", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            String raw = matcher.group(0);
            List<Integer> parts = new ArrayList<>();
            Matcher digits = Pattern.compile("\\d+").matcher(raw);
            while (digits.find()) {
                parts.add(Integer.parseInt(digits.group()));
            }

            if (raw.contains(":")) {
                if (parts.size() == 3) {
                    return (double) (parts.get(0) * 3600 + parts.get(1) * 60 + parts.get(2));
                } else if (parts.size() == 2) {
                    return (double) (parts.get(0) * 60 + parts.get(1));
                }
            } else if (raw.toLowerCase().contains("minute") && !parts.isEmpty()) {
                return (double) (parts.get(0) * 60);
            } else if (raw.toLowerCase().contains("second") && !parts.isEmpty()) {
                return (double) parts.get(0);
            }
        }
        return null;
    }

    private List<MultimodalChunk> rebuildChunks(VideoAnalysis analysis) {
        List<MultimodalChunk> chunks = new ArrayList<>();
        if (analysis.getVisualAnalyses() == null || analysis.getVisualAnalyses().isBlank()) {
            return chunks;
        }

        try {
            Map<String, String> map = objectMapper.readValue(analysis.getVisualAnalyses(), new TypeReference<Map<String, String>>() {});
            List<TranscriptSegmentDto> segments = parseSegmentsSafe(analysis.getTranscript());

            for (Map.Entry<String, String> e : map.entrySet()) {
                double tau = Double.parseDouble(e.getKey());
                String spoken = findSurroundingText(segments, tau, 15.0);
                MultimodalChunk chunk = new MultimodalChunk(tau, spoken, e.getValue(), null);
                chunk.setMultimodalEmbedding(embeddingService.generateUnifiedEmbedding(chunk));
                chunks.add(chunk);
            }
        } catch (Exception ex) {
            logger.warn("Failed to rebuild chunks from stored visual analyses: {}", ex.getMessage());
        }
        return chunks;
    }

    private List<TranscriptSegmentDto> parseSegmentsSafe(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(transcriptJson, new TypeReference<List<TranscriptSegmentDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
