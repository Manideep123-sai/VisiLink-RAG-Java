# VisiLink-RAG: Resolving Deictic Ambiguity in Instructional Videos Using Triggered Multimodal Retrieval-Augmented Generation

An enterprise-grade **Java 21 / Spring Boot 3.2** implementation of the research paper:
> **"VisiLink-RAG: Resolving Deictic Ambiguity in Instructional Videos Using Triggered Multimodal Retrieval-Augmented Generation"**  
> *R Karthik, P Yashwanth Reddy, C Manideep Sai, J Nikitha Reddy (Vellore Institute of Technology, 2026)*  
> *Grounded in foundational multimodal RAG principles established by Meng & Tong (Springer Nature, Architectural Intelligence, 2025).*

---

## 1. System Overview & Theoretical Grounding

In technical instructional videos, lecturers frequently use **deictic expressions** (e.g., *"this equation here"*, *"look at this curve"*, *"notice the parameter in this diagram"*). Traditional transcript-based RAG frameworks suffer from the **Silent Data Gap** because speech transcripts capture spoken words but completely miss on-screen visual referents.

**VisiLink-RAG** resolves this problem through an intelligent, triggered multimodal architecture:
1. **Rule-Based Deictic Cue Detection Engine** over time-aligned ASR transcripts:  
   $$\Psi(w_i) = 1 \quad \text{if} \quad \exists w \in w_i : w \in D_{\text{lex}}, \quad \text{else } 0$$
   with an empirical refractory window $\Delta t_{\min} = 10.0\,\text{s}$ ($\tau_k - \tau_{k-1} \ge 10.0\,\text{s}$), reducing redundant visual capture by **87.9%**.
2. **Selective JavaCV Keyframe Extraction**: Native FFmpeg/OpenCV JNI seeking directly to microsecond positions:  
   $$t_\mu = (\text{long})(\tau_k \times 1{,}000{,}000)$$
3. **Dual-Mode Representation Decomposition**: Generates both high-resolution $512 \times 512$ visual frames $v_k$ and semantic MLLM visual interpretations $I_k = \text{MLLM\_interpret}(v_k, \text{context} = w_k)$.
4. **Unified Cross-Modal Embedding Space**: Linear multimodal feature fusion:  
   $$e_k^{(TV)} = \alpha e_k^T + \beta e_k^V \quad (\alpha = 0.5, \; \beta = 0.5)$$
5. **Operational Domain Gating Threshold ($\theta = 0.5$)**: Grounded in Meng & Tong (2025), queries scoring below $\theta = 0.5$ are gated out to prevent technical hallucinations.
6. **Concurrent Async Pipeline**: CompletableFuture parallel execution across a fixed 4-worker thread pool (`Executors.newFixedThreadPool(4)`).

---

## 2. Java / Spring Boot Architecture

```
com.visilink.rag/
├── VisiLinkRagApplication.java            # Spring Boot Entry Point (@EnableAsync)
├── config/
│   ├── AppConfig.java                     # RestTemplate, ObjectMapper, data storage init
│   ├── AsyncConfig.java                   # 4-Worker Thread Pool Executor
│   └── WebConfig.java                     # CORS configuration for React 19 (Port 5173)
├── controller/
│   └── VideoAnalysisController.java       # 6 REST Endpoints on Port 8000
├── dto/
│   ├── AnalyzeRequest.java                # Ingestion payload
│   ├── DomainGatingResult.java            # Relevance thresholding result
│   ├── QuestionRequest.java               # Question input payload
│   ├── QuestionResponse.java              # Answer with gating metadata & citations
│   ├── TranscriptSegmentDto.java          # Time-aligned ASR chunk
│   └── VideoAnalysisResponse.java         # Serialized entity DTO (JSON compatible)
├── model/
│   ├── VideoAnalysis.java                 # JPA Entity (H2 / MySQL 8+)
│   └── MultimodalChunk.java               # Augmented segment d_k^(aug) = {w_k, I_k, v_k}
├── repository/
│   └── VideoAnalysisRepository.java       # Spring Data JPA Repository
├── service/
│   ├── AudioExtractionService.java        # JavaCV audio demuxing (44.1 kHz, 192kbps MP3)
│   ├── DeicticTriggerService.java         # Rule-based NLP cue detector (Delta t >= 10s)
│   ├── DomainGatingService.java           # Operational threshold gate (theta = 0.5)
│   ├── FrameExtractionService.java        # JavaCV native microsecond seeking & 512x512 JPEG
│   ├── GeminiService.java                 # Gemini 2.5 Flash Multimodal Client
│   ├── MultimodalEmbeddingService.java    # Linear cross-modal vector fusion
│   ├── VideoAnalysisService.java          # Pipeline orchestrator
│   └── YouTubeDownloadService.java        # yt-dlp video downloader
└── util/
    └── VectorMathUtils.java               # Cosine similarity, normalization, linear fusion
```

---

## 3. REST API Endpoints (Port 8000)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/analyze` | Ingest and analyze a YouTube URL |
| `POST` | `/api/analyze/upload` | Multipart file upload (up to 500 MB) |
| `GET` | `/api/history` | Retrieve past analysis sessions |
| `GET` | `/api/analyses/{id}` | Retrieve specific analysis details |
| `DELETE` | `/api/analyses/{id}` | Delete analysis session |
| `POST` | `/api/analyses/{id}/question` | Interactive Q&A with deictic resolution and $\theta = 0.5$ gating |

---

## 4. Quick Start & Execution

### Prerequisites
- **Java 21+** (Java 25 LTS supported)
- **FFmpeg** in your system `PATH`
- **yt-dlp** in your system `PATH` (for YouTube ingestion)

### Running the Application

Double-click `run.bat` or run in terminal:

```bash
cd VisiLink-RAG-Java
.\mvnw.cmd spring-boot:run
```

The Spring Boot backend will start on **http://localhost:8000**.  
You can view the H2 database console at **http://localhost:8000/h2-console**.

### Environment Configuration
Set your Google Gemini API key:

```bash
# Windows PowerShell
$env:GEMINI_API_KEY="your_api_key_here"

# Linux / macOS
export GEMINI_API_KEY="your_api_key_here"
```

Alternatively, configure `gemini.api.key` directly in `src/main/resources/application.properties`.

### Connecting to the React Frontend
The React 19 UI in `NLP Project/frontend` is preconfigured to connect to `http://localhost:8000`.  
Run the frontend:

```bash
cd "NLP Project/frontend"
npm run dev
```
Open **http://localhost:5173** in your browser.

---

## 5. Quantitative Evaluation & Results

Evaluation across 450 engineering queries across university lectures (MIT OCW, Stanford Online):

| Framework Architecture | Non-Deictic Acc. (%) | Deictic Visual Acc. (%) | Overall QA Acc. (%) | Precision @ 3 | Frames Processed | Mean Latency (s) |
|---|---|---|---|---|---|---|
| **Transcript-Only RAG** | 88.4% | 56.2% | 72.5% | 0.68 | 0 (Audio only) | 4.8s |
| **Uniform Multimodal RAG (1 fps)** | 87.9% | 75.6% | 81.3% | 0.74 | 205.2 frames | 38.6s |
| **Base Paper M-RAG (Meng & Tong)** | 88.1% | 78.4% | 83.1% | 0.77 | 142.0 frames | 29.4s |
| **VisiLink-RAG (This Repo)** | **89.2%** | **90.1%** | **89.7%** | **0.89** | **24.8 frames (-87.9%)** | **8.9s** |

- **ROC Discrimination**: Area Under the Curve (AUC) of **0.942**, significantly outperforming Basic Multimodal RAG (0.845) and Transcript-Only RAG (0.738).
- **Domain Rejection**: Operational threshold $\theta = 0.5$ rejected 96.8% of irrelevant out-of-domain prompts while retaining 94.2% recall on engineering inquiries.

---

## 6. Citation

```bibtex
@article{visilink2026rag,
  title={VisiLink-RAG: Resolving Deictic Ambiguity in Instructional Videos Using Triggered Multimodal Retrieval-Augmented Generation},
  author={Karthik, R and Reddy, P Yashwanth and Sai, C Manideep and Reddy, J Nikitha},
  journal={School of Computer Science and Engineering, Vellore Institute of Technology},
  year={2026}
}
```
