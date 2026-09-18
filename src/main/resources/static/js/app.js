/**
 * DocScanner AI — Complete Frontend Application (Fixed & Production-Quality)
 * Galgotias College AIML Project
 */

// ================================================================
// GLOBAL STATE
// ================================================================
const state = {
    documents: [],
    sessionId: generateUUID(),
    searchType: 'keyword',
    aiEnabled: false,
    pollingInterval: null,
    currentModalDocId: null,
};

// ================================================================
// BOOT
// ================================================================
document.addEventListener('DOMContentLoaded', async () => {
    initTabs();
    initUpload();
    initModalTabs();
    await loadAiStatus();
    await loadDocuments();
    startPolling();
});

// ================================================================
// TAB NAVIGATION
// ================================================================
function initTabs() {
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });
}

function switchTab(tabName) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
    const btn = document.querySelector(`[data-tab="${tabName}"]`);
    const panel = document.getElementById(`tab-${tabName}`);
    if (btn) btn.classList.add('active');
    if (panel) panel.classList.add('active');
    if (tabName === 'repository') loadDocuments();
    if (tabName === 'dashboard') refreshDashboard();
    if (tabName === 'chat') populateChatDocSelect();
}

// ================================================================
// POLLING — watches for processing completion
// ================================================================
function startPolling() {
    if (state.pollingInterval) clearInterval(state.pollingInterval);
    state.pollingInterval = setInterval(async () => {
        const inProgress = state.documents.some(
            d => !['READY', 'ERROR'].includes(d.status));
        if (inProgress) {
            await loadDocuments();
        }
    }, 3000); // poll every 3 seconds while documents are processing
}

// ================================================================
// AI STATUS
// ================================================================
async function loadAiStatus() {
    try {
        const res = await fetch('/api/rag/status');
        if (!res.ok) throw new Error('Status endpoint error: ' + res.status);
        const data = await res.json();
        state.aiEnabled = data.aiPowered;

        const statusText = document.getElementById('aiStatusText');
        if (statusText) statusText.textContent = data.aiPowered
            ? `AI: ${data.model}` : 'Local Mode';

        const statMode = document.getElementById('statMode');
        if (statMode) statMode.textContent = data.aiPowered ? 'OpenAI' : 'Local';

        const modeText = document.getElementById('aiModeText');
        if (modeText) modeText.textContent = data.aiPowered
            ? `OpenAI: ${data.model}` : 'Local Extractive Mode';

        const dot = document.querySelector('#aiModeBadge .status-dot');
        if (dot) dot.style.background = data.aiPowered ? '#0f9d58' : '#f57c00';

        const settingModel = document.getElementById('settingModel');
        if (settingModel && data.model && data.model !== 'local-extractive') {
            settingModel.value = data.model;
        }

        const settingBaseUrl = document.getElementById('settingBaseUrl');
        if (settingBaseUrl && data.baseUrl) {
            settingBaseUrl.value = data.baseUrl;
        }

    } catch (e) {
        console.error('AI status load failed:', e);
        const statusText = document.getElementById('aiStatusText');
        if (statusText) statusText.textContent = 'Status unavailable';
    }
}

// ================================================================
// AI SETTINGS MODAL
// ================================================================
function openSettingsModal() {
    document.getElementById('settingsModal')?.classList.remove('hidden');
}

function closeSettingsModal() {
    document.getElementById('settingsModal')?.classList.add('hidden');
}

async function saveAiSettings() {
    const apiKey = document.getElementById('settingApiKey')?.value?.trim() || '';
    const model = document.getElementById('settingModel')?.value || 'gpt-4o-mini';
    const baseUrl = document.getElementById('settingBaseUrl')?.value?.trim() || 'https://api.openai.com/v1';

    if (!apiKey) {
        showToast('Please enter an API key, or click "Switch to Local Mode".', true);
        return;
    }

    try {
        const res = await fetch('/api/rag/config', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ apiKey, model, baseUrl })
        });

        if (res.ok) {
            const data = await res.json();
            showToast(data.message || 'AI settings saved successfully!');
            closeSettingsModal();
            await loadAiStatus();
        } else {
            showToast('Failed to update AI settings.', true);
        }
    } catch (e) {
        showToast('Network error: ' + e.message, true);
    }
}

async function resetToLocalMode() {
    try {
        const res = await fetch('/api/rag/config', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ apiKey: '', model: 'local-extractive' })
        });

        if (res.ok) {
            const apiKeyInput = document.getElementById('settingApiKey');
            if (apiKeyInput) apiKeyInput.value = '';
            showToast('Switched to local offline semantic RAG mode.');
            closeSettingsModal();
            await loadAiStatus();
        }
    } catch (e) {
        showToast('Error: ' + e.message, true);
    }
}

// ================================================================
// DOCUMENT LOADING
// ================================================================
async function loadDocuments() {
    try {
        const res = await fetch('/api/documents');
        if (!res.ok) throw new Error('HTTP ' + res.status);
        state.documents = await res.json();
        renderDocumentList();
        renderRecentDocuments();
        updateDashboardStats();
        populateChatDocSelect();
    } catch (e) {
        console.error('Failed to load documents:', e);
    }
}

async function refreshDashboard() {
    await loadDocuments();
    await loadAiStatus();
}

function updateDashboardStats() {
    const docs = state.documents;
    const setEl = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
    setEl('statTotal', docs.length);
    setEl('statReady', docs.filter(d => d.status === 'READY').length);
    setEl('statProcessing', docs.filter(d => !['READY','ERROR'].includes(d.status)).length);
}

function renderRecentDocuments() {
    const container = document.getElementById('recentDocuments');
    if (!container) return;
    const recent = state.documents.slice(0, 6);
    if (recent.length === 0) {
        container.innerHTML = `<div class="no-results">No documents yet. 
            <a href="#" onclick="switchTab('upload')">Upload your first document →</a></div>`;
        return;
    }
    container.innerHTML = recent.map(doc => `
        <div class="doc-card" onclick="${doc.status === 'READY' ? `openDocument(${doc.id})` : ''}">
            <div class="doc-card-name">${fileIcon(doc.mimeType)} ${escHtml(doc.originalFilename)}</div>
            <div class="doc-card-meta">
                ${statusBadge(doc.status)}
                <span>${formatSize(doc.fileSize)}</span>
                <span>${doc.chunkCount > 0 ? doc.chunkCount + ' chunks' : ''}</span>
            </div>
            ${!['READY','ERROR'].includes(doc.status) ? '<div class="processing-bar"><div class="processing-fill"></div></div>' : ''}
        </div>
    `).join('');
}

function renderDocumentList() {
    const container = document.getElementById('documentList');
    if (!container) return;
    if (state.documents.length === 0) {
        container.innerHTML = `
            <div class="no-results">
                <p>No documents uploaded yet.</p>
                <button class="btn btn-primary" onclick="switchTab('upload')">📤 Upload First Document</button>
            </div>`;
        return;
    }
    container.innerHTML = state.documents.map(doc => `
        <div class="doc-list-item" id="doc-item-${doc.id}">
            <div class="doc-icon">${fileIcon(doc.mimeType)}</div>
            <div class="doc-info">
                <div class="doc-name">${escHtml(doc.originalFilename)}</div>
                <div class="doc-meta">
                    ${statusBadge(doc.status)}
                    <span>${formatSize(doc.fileSize)}</span>
                    ${doc.pageCount > 0 ? `<span>${doc.pageCount} page(s)</span>` : ''}
                    ${doc.chunkCount > 0 ? `<span>${doc.chunkCount} chunks</span>` : ''}
                    ${doc.extractedTextLength > 0 ? `<span>${doc.extractedTextLength.toLocaleString()} chars</span>` : ''}
                    <span>${formatDate(doc.uploadedAt)}</span>
                </div>
                ${doc.status === 'ERROR' ? `<div class="error-msg">⚠️ ${escHtml(doc.errorMessage || 'Processing failed')}</div>` : ''}
                ${!['READY','ERROR'].includes(doc.status) ? 
                    `<div class="processing-bar" style="margin-top:6px"><div class="processing-fill"></div></div>
                     <div style="font-size:11px;color:var(--text-muted);margin-top:4px">Processing: ${doc.status}...</div>` : ''}
            </div>
            <div class="doc-actions">
                ${doc.status === 'READY' ? `
                    <button class="btn btn-secondary btn-sm" onclick="openDocument(${doc.id})">👁 View OCR</button>
                    <button class="btn btn-secondary btn-sm" onclick="askAboutDoc(${doc.id})">🤖 Ask AI</button>
                    <a class="btn btn-secondary btn-sm" href="/api/documents/${doc.id}/download">⬇️</a>` : ''}
                ${['ERROR','UPLOADED'].includes(doc.status) ?
                    `<button class="btn btn-secondary btn-sm" onclick="reprocessDoc(${doc.id})">🔄 Retry</button>` : ''}
                <button class="btn btn-danger btn-sm" onclick="deleteDoc(${doc.id})">🗑</button>
            </div>
        </div>
    `).join('');
}

function populateChatDocSelect() {
    const sel = document.getElementById('chatDocumentSelect');
    if (!sel) return;
    const ready = state.documents.filter(d => d.status === 'READY');
    sel.innerHTML = `<option value="">🌐 All Documents (${ready.length} ready)</option>` +
        ready.map(d => `<option value="${d.id}">${fileIcon(d.mimeType)} ${escHtml(d.originalFilename)}</option>`).join('');
}

async function reprocessDoc(id) {
    try {
        const res = await fetch(`/api/documents/${id}/reprocess`, { method: 'POST' });
        if (res.ok) {
            showToast('Reprocessing started...');
            await loadDocuments();
        } else {
            showToast('Failed to start reprocessing.', true);
        }
    } catch (e) { showToast('Error: ' + e.message, true); }
}

async function deleteDoc(id) {
    if (!confirm('Delete this document and all its data? This cannot be undone.')) return;
    try {
        const res = await fetch(`/api/documents/${id}`, { method: 'DELETE' });
        if (res.ok) {
            showToast('Document deleted.');
            await loadDocuments();
        } else {
            showToast('Delete failed.', true);
        }
    } catch (e) { showToast('Error: ' + e.message, true); }
}

function askAboutDoc(docId) {
    const sel = document.getElementById('chatDocumentSelect');
    if (sel) sel.value = docId;
    switchTab('chat');
    document.getElementById('chatInput')?.focus();
}

// ================================================================
// OCR VIEWER MODAL
// ================================================================
async function openDocument(docId) {
    state.currentModalDocId = docId;
    const doc = state.documents.find(d => d.id === docId);
    if (!doc) return;

    document.getElementById('ocrModalTitle').textContent = doc.originalFilename;
    document.getElementById('ocrModal').classList.remove('hidden');
    setModalTab('ocr-text');

    // Load OCR text
    document.getElementById('ocrTextContent').textContent = 'Loading...';
    document.getElementById('summaryContent').innerHTML =
        `<button class="btn btn-primary" onclick="generateSummary()">🤖 Generate AI Summary</button>`;

    try {
        const res = await fetch(`/api/documents/${docId}/ocr`);
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const ocr = await res.json();
        const text = ocr.extractedText || '(No text could be extracted from this document)';
        document.getElementById('ocrTextContent').textContent = text;
        document.getElementById('ocrStats').textContent =
            `${(ocr.characterCount || 0).toLocaleString()} chars  ·  ` +
            `${(ocr.wordCount || 0).toLocaleString()} words  ·  ` +
            `${ocr.lineCount || 0} lines`;
    } catch (e) {
        document.getElementById('ocrTextContent').textContent = 'Failed to load OCR text: ' + e.message;
    }

    // Preprocessing preview (images only)
    if (doc.mimeType && doc.mimeType.startsWith('image/')) {
        loadPreprocessPreview(docId);
    } else {
        const orig = document.getElementById('originalImage');
        const proc = document.getElementById('preprocessedImage');
        if (orig) orig.src = '';
        if (proc) proc.src = '';
        document.getElementById('preprocessNote').textContent =
            'Preprocessing preview is only available for image documents (PNG, JPG, etc.)';
    }
}

function closeOcrModal() {
    document.getElementById('ocrModal').classList.add('hidden');
    state.currentModalDocId = null;
}

function initModalTabs() {
    document.querySelectorAll('.modal-tab').forEach(tab => {
        tab.addEventListener('click', () => setModalTab(tab.dataset.mtab));
    });
    document.getElementById('ocrModal')?.addEventListener('click', e => {
        if (e.target === e.currentTarget) closeOcrModal();
    });
    document.getElementById('settingsModal')?.addEventListener('click', e => {
        if (e.target === e.currentTarget) closeSettingsModal();
    });
}

function setModalTab(tabName) {
    document.querySelectorAll('.modal-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.modal-tab-panel').forEach(p => p.classList.remove('active'));
    document.querySelector(`[data-mtab="${tabName}"]`)?.classList.add('active');
    document.getElementById(tabName)?.classList.add('active');
}

function loadPreprocessPreview(docId) {
    const stage = document.getElementById('preprocessStage')?.value || 'full';
    const processed = document.getElementById('preprocessedImage');
    if (processed) {
        processed.src = '';
        processed.src = `/api/preprocess/${docId}/${stage}?t=${Date.now()}`;
        processed.onerror = () => {
            processed.src = '';
            document.getElementById('preprocessNote').textContent =
                'Preview unavailable for this document.';
        };
    }
}

function updatePreprocessPreview() {
    if (state.currentModalDocId) loadPreprocessPreview(state.currentModalDocId);
}

async function generateSummary() {
    const container = document.getElementById('summaryContent');
    container.innerHTML = `<div style="text-align:center;padding:40px">
        <div class="loading-spinner"></div>
        <p style="margin-top:12px;color:var(--text-muted)">Generating summary…</p>
    </div>`;

    try {
        const res = await fetch(`/api/rag/summarize/${state.currentModalDocId}`, { method: 'POST' });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error || 'HTTP ' + res.status);
        }
        const s = await res.json();

        const highlights = (s.keyHighlights || [])
            .map(h => `<li>${escHtml(h)}</li>`).join('');
        const actions = (s.actionItems || []).length > 0
            ? `<div class="summary-section">
                 <h4>Action Items</h4>
                 <ul>${(s.actionItems).map(a => `<li>${escHtml(a)}</li>`).join('')}</ul>
               </div>` : '';

        container.innerHTML = `
            <div class="summary-section">
                <h4>Executive Summary</h4>
                <p>${escHtml(s.executiveSummary || 'N/A')}</p>
            </div>
            <div class="summary-section">
                <h4>Key Highlights</h4>
                <ul>${highlights || '<li>No highlights extracted.</li>'}</ul>
            </div>
            ${actions}
            <p style="font-size:11px;color:var(--text-dim);margin-top:16px">
                ${(s.wordCount || 0).toLocaleString()} words · 
                ${s.aiPowered ? '🤖 OpenAI-powered' : '📋 Local extractive'} · 
                ${s.processingTimeMs}ms
            </p>`;
    } catch (e) {
        container.innerHTML = `
            <div class="alert alert-error">Failed to generate summary: ${escHtml(e.message)}</div>
            <button class="btn btn-primary" onclick="generateSummary()" style="margin-top:12px">
                Retry
            </button>`;
    }
}

function copyOcrText() {
    const text = document.getElementById('ocrTextContent').textContent;
    navigator.clipboard.writeText(text).then(() => showToast('Copied to clipboard!'));
}

function downloadOcrText() {
    const text = document.getElementById('ocrTextContent').textContent;
    const doc = state.documents.find(d => d.id === state.currentModalDocId);
    const fname = (doc?.originalFilename || 'document').replace(/\.[^.]+$/, '') + '_ocr.txt';
    const a = Object.assign(document.createElement('a'), {
        href: 'data:text/plain;charset=utf-8,' + encodeURIComponent(text),
        download: fname
    });
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

// ================================================================
// FILE UPLOAD — drag-drop + file picker
// ================================================================
function initUpload() {
    const area = document.getElementById('uploadArea');
    const fileInput = document.getElementById('fileInput');
    if (!area || !fileInput) return;

    ['dragenter','dragover'].forEach(evt =>
        area.addEventListener(evt, e => { e.preventDefault(); area.classList.add('drag-over'); }));
    ['dragleave','dragend'].forEach(evt =>
        area.addEventListener(evt, () => area.classList.remove('drag-over')));

    area.addEventListener('drop', e => {
        e.preventDefault();
        area.classList.remove('drag-over');
        const files = e.dataTransfer.files;
        if (files.length > 0) uploadFile(files[0]);
    });

    // Click on area triggers file picker (not just the button)
    area.addEventListener('click', e => {
        if (e.target.tagName !== 'BUTTON') fileInput.click();
    });

    fileInput.addEventListener('change', () => {
        if (fileInput.files.length > 0) uploadFile(fileInput.files[0]);
        fileInput.value = '';
    });
}

async function uploadFile(file) {
    const progressEl   = document.getElementById('uploadProgress');
    const resultEl     = document.getElementById('uploadResult');
    const bar          = document.getElementById('progressBar');
    const statusEl     = document.getElementById('uploadStatus');
    const successEl    = document.getElementById('uploadSuccess');
    const errorEl      = document.getElementById('uploadError');

    // Show progress
    if (progressEl) progressEl.classList.remove('hidden');
    if (resultEl)   resultEl.classList.remove('hidden');
    if (successEl)  successEl.classList.add('hidden');
    if (errorEl)    errorEl.classList.add('hidden');
    if (statusEl)   statusEl.textContent = `Uploading "${file.name}"…`;

    // Fake progress animation
    let pct = 0;
    const progressTimer = setInterval(() => {
        pct = Math.min(pct + Math.random() * 12, 85);
        if (bar) bar.style.width = pct + '%';
    }, 200);

    const formData = new FormData();
    formData.append('file', file);

    try {
        const res = await fetch('/api/documents/upload', {
            method: 'POST',
            body: formData
        });

        clearInterval(progressTimer);
        if (bar) bar.style.width = '100%';

        const data = await res.json();

        if (res.ok) {
            if (statusEl) statusEl.textContent = 'Upload complete! Processing started…';
            if (successEl) {
                successEl.classList.remove('hidden');
                successEl.innerHTML = `
                    ✅ <strong>${escHtml(file.name)}</strong> uploaded successfully! (ID: ${data.documentId})<br/>
                    <small style="color:var(--text-muted)">
                        Processing pipeline running: OCR → Chunking → Embedding.<br/>
                        The document will appear as <strong>READY</strong> in the repository in a few seconds.
                    </small>
                    <div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap">
                        <button class="btn btn-secondary btn-sm" onclick="switchTab('repository')">📁 View Repository</button>
                        <button class="btn btn-secondary btn-sm" onclick="switchTab('chat')">🤖 Ask AI</button>
                    </div>`;
            }
            await loadDocuments();
        } else {
            if (statusEl) statusEl.textContent = 'Upload failed.';
            if (errorEl) {
                errorEl.classList.remove('hidden');
                errorEl.textContent = '❌ ' + (data.error || 'Unknown error');
            }
        }
    } catch (e) {
        clearInterval(progressTimer);
        if (statusEl) statusEl.textContent = 'Upload failed.';
        if (errorEl) {
            errorEl.classList.remove('hidden');
            errorEl.textContent = '❌ Network error: ' + e.message;
        }
    }

    setTimeout(() => { if (bar) bar.style.width = '0%'; }, 3000);
}

// ================================================================
// AI CHAT — RAG Q&A
// ================================================================
function askPreset(promptText) {
    const input = document.getElementById('chatInput');
    if (input) {
        input.value = promptText;
        sendChatMessage();
    }
}

function handleChatKeydown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendChatMessage();
    }
}

async function sendChatMessage() {
    const input   = document.getElementById('chatInput');
    const sendBtn = document.getElementById('sendBtn');
    const question = input?.value?.trim();
    if (!question) return;

    const readyDocs = state.documents.filter(d => d.status === 'READY');
    if (readyDocs.length === 0) {
        appendSystemMessage('⚠️ No documents are ready yet. Please upload a document first and wait for it to reach READY status.');
        return;
    }

    input.value = '';
    if (sendBtn) sendBtn.disabled = true;

    const docIdVal = document.getElementById('chatDocumentSelect')?.value;
    const documentId = docIdVal ? parseInt(docIdVal) : null;

    appendUserMessage(question);
    const thinkingId = appendThinkingMessage();

    try {
        const body = { question, sessionId: state.sessionId, documentId, topK: 5 };
        const res = await fetch('/api/rag/query', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        removeThinkingMessage(thinkingId);

        if (res.ok) {
            const data = await res.json();
            appendAiMessage(data);
        } else {
            const err = await res.json().catch(() => ({}));
            appendErrorMessage('Query failed: ' + (err.error || 'HTTP ' + res.status));
        }
    } catch (e) {
        removeThinkingMessage(thinkingId);
        appendErrorMessage('Network error: ' + e.message);
    } finally {
        if (sendBtn) sendBtn.disabled = false;
        input?.focus();
    }
}

function appendUserMessage(text) {
    removeWelcome();
    appendMessage(`
        <div class="chat-msg user">
            <div class="msg-avatar">👤</div>
            <div class="msg-content">
                <div class="msg-bubble">${escHtml(text)}</div>
            </div>
        </div>`);
}

function appendAiMessage(data) {
    const sources = (data.contextChunks || []).slice(0, 5).map((c, i) => `
        <span class="source-chip" title="Score: ${c.similarityScore}">
            📄 ${escHtml(c.documentName || 'Doc')} #${c.chunkIndex} (${c.similarityScore})
        </span>`).join('');

    const modeBadge = data.aiPowered
        ? `<span class="mode-badge openai">🤖 ${escHtml(data.modelUsed)}</span>`
        : `<span class="mode-badge local">📋 Local</span>`;

    appendMessage(`
        <div class="chat-msg ai">
            <div class="msg-avatar">🤖</div>
            <div class="msg-content">
                <div class="msg-bubble">${formatMarkdown(data.answer || 'No answer generated.')}</div>
                ${sources ? `<div class="msg-sources">
                    <div class="sources-label">Sources used:</div>
                    <div class="source-chips">${sources}</div>
                </div>` : ''}
                <div class="msg-meta">${modeBadge} · ${data.processingTimeMs}ms · ${(data.contextChunks||[]).length} chunks retrieved</div>
            </div>
        </div>`);
}

function appendThinkingMessage() {
    const id = 'thinking-' + Date.now();
    appendMessage(`
        <div class="chat-msg ai" id="${id}">
            <div class="msg-avatar">🤖</div>
            <div class="msg-content">
                <div class="msg-bubble thinking-dots">Thinking<span>.</span><span>.</span><span>.</span></div>
            </div>
        </div>`);
    return id;
}

function removeThinkingMessage(id) {
    document.getElementById(id)?.remove();
}

function appendErrorMessage(msg) {
    appendMessage(`
        <div class="chat-msg ai">
            <div class="msg-avatar">⚠️</div>
            <div class="msg-content">
                <div class="msg-bubble" style="color:#ef5350;border-color:rgba(198,40,40,0.3)">${escHtml(msg)}</div>
            </div>
        </div>`);
}

function appendSystemMessage(msg) {
    removeWelcome();
    appendMessage(`
        <div class="chat-msg ai">
            <div class="msg-avatar">ℹ️</div>
            <div class="msg-content">
                <div class="msg-bubble" style="color:var(--warning)">${escHtml(msg)}</div>
            </div>
        </div>`);
}

function appendMessage(html) {
    const messages = document.getElementById('chatMessages');
    if (!messages) return;
    messages.insertAdjacentHTML('beforeend', html);
    messages.scrollTop = messages.scrollHeight;
}

function removeWelcome() {
    document.querySelector('.chat-welcome')?.remove();
}

function clearChat() {
    const messages = document.getElementById('chatMessages');
    if (messages) messages.innerHTML = `
        <div class="chat-welcome">
            <div class="welcome-icon">🤖</div>
            <h3>DocScanner AI Assistant</h3>
            <p>Upload documents and ask questions about their content. I'll find the most relevant passages and answer based on what's in your documents.</p>
        </div>`;
    state.sessionId = generateUUID();
}

// ================================================================
// SEARCH
// ================================================================
function setSearchType(type) {
    state.searchType = type;
    document.getElementById('kwBtn')?.classList.toggle('active', type === 'keyword');
    document.getElementById('semBtn')?.classList.toggle('active', type === 'semantic');
    const inp = document.getElementById('searchInput');
    if (inp) inp.placeholder = type === 'semantic'
        ? 'Describe what you\'re looking for (semantic)...'
        : 'Search in document text and filenames...';
}

async function performSearch() {
    const query = document.getElementById('searchInput')?.value?.trim();
    if (!query) { showToast('Please enter a search query.', true); return; }

    const container = document.getElementById('searchResults');
    if (container) container.innerHTML = `<div style="text-align:center;padding:40px"><div class="loading-spinner"></div><p style="color:var(--text-muted);margin-top:12px">Searching…</p></div>`;

    try {
        const url = state.searchType === 'semantic'
            ? `/api/search/semantic?q=${encodeURIComponent(query)}&topK=8`
            : `/api/search?q=${encodeURIComponent(query)}`;

        const res = await fetch(url);
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error || 'HTTP ' + res.status);
        }
        const data = await res.json();
        const results = data.results || [];

        if (!container) return;
        if (results.length === 0) {
            container.innerHTML = `<div class="no-results">No results found for "<strong>${escHtml(query)}</strong>"</div>`;
            return;
        }

        container.innerHTML = `<p style="color:var(--text-muted);font-size:13px;margin-bottom:12px">
            ${data.resultCount || results.length} result(s) for "<strong>${escHtml(query)}</strong>"
            — <em>${state.searchType} search</em>
        </p>` + results.map(r => `
            <div class="search-hit">
                <div class="search-hit-header">
                    <div class="search-hit-name">${fileIcon(r.mimeType)} ${escHtml(r.documentName || r.originalFilename || 'Document')}</div>
                    ${r.similarityScore !== undefined
                        ? `<div class="search-hit-score">Score: ${r.similarityScore}</div>` : ''}
                </div>
                <div class="search-hit-text">${escHtml(
                    ((r.text || r.textPreview || '')).substring(0, 350)
                )}${(r.text || r.textPreview || '').length > 350 ? '...' : ''}</div>
            </div>`).join('');
    } catch (e) {
        if (container) container.innerHTML = `<div class="alert alert-error">Search error: ${escHtml(e.message)}</div>`;
    }
}

// ================================================================
// UTILITIES
// ================================================================
function escHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
        .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}

function formatMarkdown(text) {
    if (!text) return '';
    return escHtml(text)
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<em>$1</em>')
        .replace(/`([^`]+)`/g, '<code style="background:var(--bg);padding:2px 5px;border-radius:4px;font-family:var(--mono);font-size:0.9em">$1</code>')
        .replace(/\n/g, '<br/>');
}

function formatSize(bytes) {
    if (!bytes) return '0 B';
    const k = 1024, sizes = ['B','KB','MB','GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i];
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    try {
        return new Date(dateStr).toLocaleDateString('en-IN', {
            day: '2-digit', month: 'short', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    } catch { return dateStr; }
}

function fileIcon(mime) {
    if (!mime) return '📄';
    if (mime === 'application/pdf') return '📕';
    if (mime.startsWith('image/')) return '🖼️';
    if (mime.startsWith('text/')) return '📝';
    return '📄';
}

function statusBadge(status) {
    return `<span class="status-badge status-${status}">${status}</span>`;
}

function generateUUID() {
    return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g,c=>
        (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16));
}

function showToast(msg, isError = false) {
    const existing = document.getElementById('toast');
    if (existing) existing.remove();
    const toast = document.createElement('div');
    toast.id = 'toast';
    toast.textContent = msg;
    toast.style.cssText = `
        position:fixed;bottom:24px;right:24px;z-index:9999;
        padding:12px 20px;border-radius:10px;font-size:14px;font-family:var(--font);
        background:${isError ? 'rgba(198,40,40,0.95)' : 'rgba(15,157,88,0.95)'};
        color:#fff;box-shadow:0 4px 24px rgba(0,0,0,0.5);
        transition:opacity 0.3s;cursor:pointer;
    `;
    toast.onclick = () => toast.remove();
    document.body.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 3500);
}
