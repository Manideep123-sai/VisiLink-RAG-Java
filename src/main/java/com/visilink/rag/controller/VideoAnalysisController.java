package com.visilink.rag.controller;

import com.visilink.rag.dto.*;
import com.visilink.rag.model.VideoAnalysis;
import com.visilink.rag.repository.VideoAnalysisRepository;
import com.visilink.rag.service.VideoAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API Layer for VisiLink-RAG.
 *
 * Exposes 6 reactive REST endpoints on Port 8000 for the React 19 + Vite web client.
 * Implements strict JSON error formatting {"detail": "..."} for seamless frontend integration.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class VideoAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(VideoAnalysisController.class);

    private final VideoAnalysisService analysisService;
    private final VideoAnalysisRepository repository;

    public VideoAnalysisController(VideoAnalysisService analysisService, VideoAnalysisRepository repository) {
        this.analysisService = analysisService;
        this.repository = repository;
    }

    /**
     * POST /api/analyze: Hybrid Ingestion for YouTube URLs.
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeYouTubeVideo(@RequestBody AnalyzeRequest request) {
        try {
            if (request.getUrl() == null || request.getUrl().isBlank()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Video URL is required."));
            }

            String engine = (request.getEngine() != null && !request.getEngine().isBlank())
                    ? request.getEngine() : "gemini";

            VideoAnalysis analysis = analysisService.processYouTubeVideo(request.getUrl(), engine);
            return ResponseEntity.ok(new VideoAnalysisResponse(analysis));

        } catch (Exception e) {
            logger.error("Error analyzing YouTube video: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to analyze YouTube video: " + e.getMessage()));
        }
    }

    /**
     * POST /api/analyze/upload: Hybrid Ingestion for local video file uploads.
     * Supports both 'engine_choice' and 'engine' parameter names.
     */
    @PostMapping(value = "/analyze/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> analyzeUploadedVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "engine_choice", required = false) String engineChoice,
            @RequestParam(value = "engine", required = false) String engineParam) {

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Uploaded video file cannot be empty."));
            }

            String selectedEngine = "gemini";
            if (engineChoice != null && !engineChoice.isBlank()) {
                selectedEngine = engineChoice;
            } else if (engineParam != null && !engineParam.isBlank()) {
                selectedEngine = engineParam;
            }

            VideoAnalysis analysis = analysisService.processUploadedVideo(file, selectedEngine);
            return ResponseEntity.ok(new VideoAnalysisResponse(analysis));

        } catch (Exception e) {
            logger.error("Error analyzing uploaded video: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to analyze uploaded video: " + e.getMessage()));
        }
    }

    /**
     * GET /api/history: Retrieve past analysis sessions.
     */
    @GetMapping("/history")
    public ResponseEntity<List<VideoAnalysisResponse>> getHistory(
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        List<VideoAnalysis> analyses = repository.findAllByOrderByCreatedAtDesc();
        List<VideoAnalysisResponse> response = analyses.stream()
                .skip(skip)
                .limit(limit)
                .map(VideoAnalysisResponse::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/analyses/{id}: Retrieve detailed analysis result by ID.
     */
    @GetMapping("/analyses/{id}")
    public ResponseEntity<?> getAnalysis(@PathVariable("id") Long id) {
        return repository.findById(id)
                .map(analysis -> ResponseEntity.ok(new VideoAnalysisResponse(analysis)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new VideoAnalysisResponse()));
    }

    /**
     * DELETE /api/analyses/{id}: Delete an analysis session.
     */
    @DeleteMapping("/analyses/{id}")
    public ResponseEntity<?> deleteAnalysis(@PathVariable("id") Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Analysis not found with ID: " + id));
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/analyses/{id}/question: Interactive Q&A with Operational Relevance Gating (theta = 0.5)
     * and deictic visual reference resolution.
     */
    @PostMapping("/analyses/{id}/question")
    public ResponseEntity<?> askQuestion(
            @PathVariable("id") Long id,
            @RequestBody QuestionRequest request) {

        try {
            if (request.getQuestion() == null || request.getQuestion().isBlank()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Question string is required."));
            }

            QuestionResponse response = analysisService.answerQuestion(id, request.getQuestion());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error during question answering: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to process question: " + e.getMessage()));
        }
    }
}
