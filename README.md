# DocScanner AI
### AI-Powered Document Scanning & RAG Management System
**Galgotias College of Engineering & Technology — Department of AI & Machine Learning**

---

## 🚀 Quick Start (Zero Dependencies)

```bash
# Clone / navigate to project
cd C:\Users\priya\scratch\DocScanner

# Run application (downloads Maven on first use)
.\mvnw.cmd spring-boot:run

# Access the application
# UI:        http://localhost:8080
# H2 DB:     http://localhost:8080/h2-console
# Actuator:  http://localhost:8080/actuator/health
```

> ✅ **The app runs fully without Tesseract or an OpenAI API key!**
> Upload PDFs for instant digital extraction, or use graceful fallback OCR for images.

---

## 📐 System Architecture

```
[Document Upload: PDF / Image / Text]
          ↓
[Image Preprocessing Pipeline]
  • Grayscale (0.299R + 0.587G + 0.114B)
  • Gaussian Blur (3×3 kernel)
  • Otsu's Adaptive Thresholding
  • Contrast Enhancement (Linear Stretching)
          ↓
[Multi-Engine OCR]
  • Digital PDFs → Apache PDFBox (lossless)
  • Scanned Images → Tess4J (Tesseract)
  • Fallback → Graceful placeholder (no crash)
          ↓
[Smart Text Chunking]
  • Sliding window: 500 chars, 100-char overlap
  • Natural boundary detection (. ! ? \n)
          ↓
[Vector Embedding]
  • OpenAI text-embedding-3-small (1536-dim)
  • Local n-gram TF-IDF (384-dim) — offline fallback
          ↓
[Cosine Similarity Vector Search]
  • Top-K retrieval with configurable threshold
          ↓
[RAG Answer Generation]
  • OpenAI GPT-4o-mini (with API key)
  • Local extractive engine (without API key)
          ↓
[Interactive Modern UI]
  • Dashboard · Upload · Repository · AI Chat · Search
```

---

## ✨ Features

| Feature | Description |
|---|---|
| 📤 **Multi-format Upload** | PDF, PNG, JPG, JPEG, BMP, GIF, TIFF, TXT |
| 🖼️ **Image Preprocessing** | Live stage-by-stage preview (Grayscale → Blur → Threshold → Contrast) |
| 🔤 **OCR Extraction** | PDFBox for digital, Tess4J for scanned, graceful fallback |
| ✂️ **Smart Chunking** | Sentence-boundary-aware sliding window |
| 📐 **Vector Embeddings** | OpenAI or local TF-IDF n-gram vectors |
| 🔍 **Cosine Similarity Search** | Fast in-memory ranked retrieval |
| 🤖 **RAG Q&A** | Context-grounded answers with source citations |
| 📋 **AI Summarization** | Executive summary + key highlights + action items |
| 💬 **Chat Interface** | Multi-turn conversation per document or across all documents |
| 🔎 **Dual Search** | Keyword full-text + Semantic vector search |
| 📊 **Dashboard** | Live stats and processing pipeline visualization |

---

## 🔧 Configuration

### application.properties (Key Settings)

```properties
# OpenAI (optional - app works without this)
docscanner.openai.api-key=${OPENAI_API_KEY:}
docscanner.openai.model=gpt-4o-mini

# Tesseract OCR (optional - install separately for image OCR)
docscanner.tesseract.data-path=./tessdata

# Chunking
docscanner.chunk.size=500
docscanner.chunk.overlap=100
```

### Enable MySQL (Production)
```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
# Set: DB_USERNAME, DB_PASSWORD environment variables
```

### Enable OpenAI
```bash
$env:OPENAI_API_KEY = "sk-..."
.\mvnw.cmd spring-boot:run
```

### Enable Full OCR (Tesseract)
1. Download Tesseract: https://github.com/UB-Mannheim/tesseract/wiki
2. Set in `application.properties`:
   ```
   docscanner.tesseract.data-path=C:/Program Files/Tesseract-OCR/tessdata
   ```

---

## 📡 REST API Reference

### Documents
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/documents/upload` | Upload a file (multipart/form-data) |
| `GET` | `/api/documents` | List all documents |
| `GET` | `/api/documents/{id}` | Get document details |
| `GET` | `/api/documents/{id}/ocr` | Get extracted OCR text |
| `POST` | `/api/documents/{id}/reprocess` | Re-run processing pipeline |
| `DELETE` | `/api/documents/{id}` | Delete document |

### RAG
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/rag/query` | Ask a question (RAG answer) |
| `POST` | `/api/rag/summarize/{id}` | Generate AI summary |
| `GET` | `/api/rag/status` | Check AI mode (OpenAI / Local) |

### Preprocessing
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/preprocess/{id}/{stage}` | Get preprocessed image (stage: grayscale, denoise, threshold, contrast, full) |

### Search
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/search?q=query` | Keyword full-text search |
| `GET` | `/api/search/semantic?q=query` | Semantic vector search |

### cURL Examples
```bash
# Upload a PDF
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@./sample.pdf"

# Ask a question
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the main topic?", "sessionId": "abc-123"}'

# Summarize document
curl -X POST http://localhost:8080/api/rag/summarize/1

# Search documents  
curl "http://localhost:8080/api/search?q=invoice"
curl "http://localhost:8080/api/search/semantic?q=payment+terms&topK=5"
```

---

## 🧪 Running Tests

```bash
.\mvnw.cmd test
```

Tests included:
- `DocScannerApplicationTests` — Spring context load
- `ImagePreprocessingServiceTest` — Grayscale, blur, Otsu, contrast
- `TextChunkingServiceTest` — Chunking boundaries, overlap, indices
- `VectorStoreServiceTest` — Cosine similarity math, ranking, normalization

---

## 🛠️ Tech Stack

- **Backend**: Java 21 · Spring Boot 3.3 · Spring Data JPA
- **Database**: H2 (embedded) · MySQL (optional)
- **OCR**: Apache PDFBox 3.0 · Tess4J 5.11 (Tesseract wrapper)
- **AI**: OpenAI GPT-4o-mini · text-embedding-3-small
- **Search**: Cosine Similarity Vector Engine · Full-text JPA Query
- **Frontend**: Vanilla JS SPA · Modern CSS (Dark Glass Theme)
- **Build**: Maven Wrapper (no mvn install needed)

---

*Galgotias College of Engineering & Technology — AIML Department*
