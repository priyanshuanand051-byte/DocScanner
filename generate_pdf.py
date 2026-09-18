import sys

def make_pdf(filename):
    lines = [
        "DocScanner AI - Project Presentation Report",
        "Institution: Galgotias College of Engineering and Technology Greater Noida",
        "Department: Artificial Intelligence and Machine Learning",
        "Session: 2026-27 ODD Semester",
        "Course: Mini Project or Internship Assessment BCS-554",
        "Project Title: DocScanner AI Document Management System",
        "Project Guide: Mrs. Dipti Jaiswal",
        "Team Members:",
        "1. Priyanshu kumar - 2400971530088",
        "2. Sneha verma - 2400971530128",
        "3. Pranjal Chaurasia - 2400971530083",
        "4. Sandeep Thakur - 2400971530108",
        "System Overview:",
        "DocScanner is an AI-powered document scanning and management system that converts scanned or image-based documents into searchable digital text using OCR.",
        "With RAG Retrieval-Augmented Generation the system can understand document content and provide relevant answers to user queries.",
        "Key Objectives:",
        "- To digitize physical documents using OCR technology.",
        "- To accurately extract text from scanned document images.",
        "- To use a GPT model for intelligent document understanding.",
        "- To provide summarization and question answering from documents.",
        "- To make document information easy to search access and manage.",
        "System Methodology and Architecture Flow:",
        "Document -> Image Preprocessing -> OCR -> Text Chunking -> Embeddings -> Vector DB -> RAG Retrieval -> GPT Model -> User Response",
        "Implementation Technologies Used:",
        "Backend: Java, Spring Boot, Spring Data JPA, Maven",
        "Database: H2 Embedded Database, MySQL",
        "AI and Extraction: GPT Model, RAG System, Cosine Similarity Vector Database, Apache PDFBox, Tesseract OCR",
        "Frontend: HTML5, CSS3, JavaScript Single Page Application",
        "Results and Conclusion:",
        "DocScanner enables fast accurate retrieval of relevant context and contextual GPT answers for accelerated document analysis."
    ]

    stream_content = "BT\n/F1 12 Tf\n50 740 Td\n15 TL\n"
    for line in lines:
        cleaned = line.replace("(", "").replace(")", "").replace("\\", "")
        stream_content += f"({cleaned}) '\n"
    stream_content += "ET\n"

    stream_bytes = stream_content.encode("latin-1")
    stream_len = len(stream_bytes)

    objects = []
    # 1: Catalog
    objects.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    # 2: Pages
    objects.append(b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    # 3: Page
    objects.append(b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>")
    # 4: Stream
    objects.append(f"<< /Length {stream_len} >>\nstream\n".encode("latin-1") + stream_bytes + b"\nendstream")
    # 5: Font
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    pdf_out = bytearray(b"%PDF-1.4\n")
    offsets = []

    for i, obj in enumerate(objects, 1):
        offsets.append(len(pdf_out))
        pdf_out.extend(f"{i} 0 obj\n".encode("ascii"))
        pdf_out.extend(obj)
        pdf_out.extend(b"\nendobj\n")

    xref_offset = len(pdf_out)
    pdf_out.extend(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode("ascii"))
    for offset in offsets:
        pdf_out.extend(f"{offset:010d} 00000 n \n".encode("ascii"))

    pdf_out.extend(f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode("ascii"))

    with open(filename, "wb") as f:
        f.write(pdf_out)
    print(f"Generated valid PDF ({len(pdf_out)} bytes): {filename}")

if __name__ == "__main__":
    make_pdf(sys.argv[1])
