// State
let currentSessionId = null;
let currentUserId = 'user-001';
let sessions = [];

// DOM Elements
const userIdInput = document.getElementById('userId');
const createSessionBtn = document.getElementById('createSessionBtn');
const sessionList = document.getElementById('sessionList');
const sessionIdDisplay = document.getElementById('sessionIdDisplay');
const chatMessages = document.getElementById('chatMessages');
const userInput = document.getElementById('userInput');
const sendBtn = document.getElementById('sendBtn');
const summarizeBtn = document.getElementById('summarizeBtn');
const closeSessionBtn = document.getElementById('closeSessionBtn');
const profileContent = document.getElementById('profileContent');
const memoriesContent = document.getElementById('memoriesContent');
const summaryContent = document.getElementById('summaryContent');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    createSessionBtn.addEventListener('click', createSession);
    sendBtn.addEventListener('click', sendMessage);
    closeSessionBtn.addEventListener('click', closeSession);
    summarizeBtn.addEventListener('click', summarizeSession);

    userInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    userInput.addEventListener('input', () => {
        sendBtn.disabled = !currentSessionId || !userInput.value.trim();
    });

    // Load sessions on startup
    loadSessions();
});

// Load Sessions
async function loadSessions() {
    currentUserId = userIdInput.value.trim() || 'user-001';

    try {
        const response = await fetch(`/api/session/list/${encodeURIComponent(currentUserId)}`);
        sessions = await response.json();
        displaySessionList();
    } catch (error) {
        console.error('Failed to load sessions:', error);
        sessionList.innerHTML = '<div class="empty-state">Failed to load sessions.</div>';
    }
}

// Display Session List
function displaySessionList() {
    if (!sessions || sessions.length === 0) {
        sessionList.innerHTML = '<div class="empty-state">No sessions yet.</div>';
        return;
    }

    let html = '';
    sessions.forEach(session => {
        const isActive = session.id === currentSessionId;
        const status = session.status || 'ACTIVE';
        const title = session.title || `Session ${session.id.substring(0, 8)}`;
        const date = session.startedAt ? formatDate(session.startedAt) : '';

        html += `
            <div class="session-item ${isActive ? 'active' : ''}"
                 data-session-id="${session.id}"
                 onclick="selectSession('${session.id}')">
                <div class="session-title">${escapeHtml(title)}</div>
                <div class="session-meta">
                    <span class="session-status ${status.toLowerCase()}">${status}</span>
                    <span>${date}</span>
                </div>
            </div>
        `;
    });

    sessionList.innerHTML = html;
}

// Select Session
async function selectSession(sessionId) {
    currentSessionId = sessionId;

    // Update UI
    sessionIdDisplay.textContent = `Session: ${sessionId.substring(0, 8)}...`;

    // Highlight active session
    document.querySelectorAll('.session-item').forEach(item => {
        item.classList.toggle('active', item.dataset.sessionId === sessionId);
    });

    // Enable buttons
    sendBtn.disabled = !userInput.value.trim();
    summarizeBtn.disabled = false;
    closeSessionBtn.disabled = false;

    // Load chat history
    await loadChatHistory(sessionId);

    // Load profile and memories
    refreshMemoryPanel();
}

// Load Chat History
async function loadChatHistory(sessionId) {
    try {
        const response = await fetch(`/api/chat/history?sessionId=${sessionId}`);
        const messages = await response.json();

        chatMessages.innerHTML = '';

        if (!messages || messages.length === 0) {
            chatMessages.innerHTML = `
                <div class="welcome-message">
                    <p>No messages yet.</p>
                    <p>Start typing to begin the conversation.</p>
                </div>
            `;
            return;
        }

        messages.forEach(msg => {
            addMessage(msg.role.toLowerCase(), msg.content);
        });
    } catch (error) {
        console.error('Failed to load chat history:', error);
        chatMessages.innerHTML = '<div class="welcome-message"><p>Failed to load messages.</p></div>';
    }
}

// Create Session
async function createSession() {
    currentUserId = userIdInput.value.trim() || 'user-001';

    try {
        const response = await fetch(`/api/session?userId=${encodeURIComponent(currentUserId)}`, {
            method: 'POST'
        });

        const data = await response.json();
        currentSessionId = data.sessionId;

        sessionIdDisplay.textContent = `Session: ${currentSessionId.substring(0, 8)}...`;

        // Clear chat
        chatMessages.innerHTML = '';
        addSystemMessage('Session created. Start chatting!');

        // Enable buttons
        sendBtn.disabled = !userInput.value.trim();
        summarizeBtn.disabled = false;
        closeSessionBtn.disabled = false;

        // Reload session list
        await loadSessions();

        // Load profile and memories
        refreshMemoryPanel();
    } catch (error) {
        console.error('Failed to create session:', error);
        addSystemMessage('Failed to create session: ' + error.message);
    }
}

// Send Message
async function sendMessage() {
    const content = userInput.value.trim();
    if (!content || !currentSessionId) return;

    // Add user message to UI
    addMessage('user', content);
    userInput.value = '';
    sendBtn.disabled = true;

    try {
        const response = await fetch('/api/chat/message', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                sessionId: currentSessionId,
                content: content
            })
        });

        const data = await response.json();
        addMessage('assistant', data.content);

        // Refresh memory panel and session list
        refreshMemoryPanel();
        loadSessions();
    } catch (error) {
        addSystemMessage('Failed to send message: ' + error.message);
    }

    sendBtn.disabled = !userInput.value.trim();
}

// Close Session
async function closeSession() {
    if (!currentSessionId) return;

    try {
        const response = await fetch(`/api/session/${currentSessionId}`, {
            method: 'DELETE'
        });

        const data = await response.json();

        addSystemMessage(`Session closed. Status: ${data.status}`);

        currentSessionId = null;
        sessionIdDisplay.textContent = '';

        sendBtn.disabled = true;
        summarizeBtn.disabled = true;
        closeSessionBtn.disabled = true;

        // Reload session list
        await loadSessions();

        // Refresh memory panel
        refreshMemoryPanel();
    } catch (error) {
        addSystemMessage('Failed to close session: ' + error.message);
    }
}

// Summarize Session
async function summarizeSession() {
    if (!currentSessionId) return;

    summaryContent.innerHTML = '<p class="empty-state">Summarizing...</p>';

    try {
        const response = await fetch(`/api/chat/summarize?sessionId=${currentSessionId}`, {
            method: 'POST'
        });

        const data = await response.json();
        displaySummary(data);
    } catch (error) {
        summaryContent.innerHTML = `<p class="empty-state">Error: ${error.message}</p>`;
    }
}

// Refresh Memory Panel
async function refreshMemoryPanel() {
    if (!currentUserId) return;

    // Load profile
    try {
        const profileResponse = await fetch(`/api/profile/${currentUserId}`);
        const profileData = await profileResponse.json();
        displayProfile(profileData);
    } catch (error) {
        profileContent.innerHTML = `<p class="empty-state">Error loading profile</p>`;
    }

    // Load memories
    try {
        const memoriesResponse = await fetch(`/api/memory/${currentUserId}`);
        const memoriesData = await memoriesResponse.json();
        displayMemories(memoriesData);
    } catch (error) {
        memoriesContent.innerHTML = `<p class="empty-state">Error loading memories</p>`;
    }
}

// Display Functions
function addMessage(role, content) {
    const messageDiv = createMessageDiv(role);
    messageDiv.querySelector('.message-content').textContent = content;
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
}

function createMessageDiv(role) {
    const div = document.createElement('div');
    div.className = `message ${role}`;
    div.innerHTML = `<div class="message-content"></div>`;
    chatMessages.appendChild(div);
    scrollToBottom();
    return div;
}

function addSystemMessage(text) {
    const div = document.createElement('div');
    div.className = 'message system';
    div.innerHTML = `<div class="message-content" style="background: #f8f9fa; color: #666; font-style: italic;">${text}</div>`;
    chatMessages.appendChild(div);
    scrollToBottom();
}

function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function displayProfile(data) {
    const entries = data.profileEntries || [];
    if (entries.length === 0) {
        profileContent.innerHTML = '<p class="empty-state">No profile data yet.</p>';
        return;
    }

    let html = '';
    entries.forEach(entry => {
        html += `<div class="profile-item">${escapeHtml(entry.content || entry)}</div>`;
    });
    profileContent.innerHTML = html;
}

function displayMemories(data) {
    const memories = data.memories || [];
    if (memories.length === 0) {
        memoriesContent.innerHTML = '<p class="empty-state">No memories yet.</p>';
        return;
    }

    let html = '';
    memories.forEach(memory => {
        html += `
            <div class="memory-item">
                <div class="type">${escapeHtml(memory.type || 'MEMORY')}</div>
                <div class="content">${escapeHtml(memory.content)}</div>
            </div>
        `;
    });
    memoriesContent.innerHTML = html;
}

function displaySummary(data) {
    let html = `
        <div class="field">
            <div class="field-label">Core Intent</div>
            <div class="field-value">${escapeHtml(data.coreIntent || 'N/A')}</div>
        </div>
        <div class="field">
            <div class="field-label">Emotional Tone</div>
            <div class="field-value">${escapeHtml(data.emotionalTone || 'N/A')}</div>
        </div>
    `;

    if (data.keyTopics && data.keyTopics.length > 0) {
        html += `
            <div class="field">
                <div class="field-label">Key Topics</div>
                <div class="field-value">${data.keyTopics.map(t => escapeHtml(t)).join(', ')}</div>
            </div>
        `;
    }

    if (data.actionItems && data.actionItems.length > 0) {
        html += `
            <div class="field">
                <div class="field-label">Action Items</div>
                <div class="field-value">${data.actionItems.map(a => escapeHtml(a)).join(', ')}</div>
            </div>
        `;
    }

    if (data.fullSummary) {
        html += `
            <div class="field">
                <div class="field-label">Full Summary</div>
                <div class="field-value">${escapeHtml(data.fullSummary)}</div>
            </div>
        `;
    }

    summaryContent.innerHTML = html;
}

function formatDate(dateStr) {
    try {
        const date = new Date(dateStr);
        const now = new Date();
        const diff = now - date;

        // Less than 1 minute
        if (diff < 60000) {
            return 'just now';
        }
        // Less than 1 hour
        if (diff < 3600000) {
            const mins = Math.floor(diff / 60000);
            return `${mins}m ago`;
        }
        // Less than 24 hours
        if (diff < 86400000) {
            const hours = Math.floor(diff / 3600000);
            return `${hours}h ago`;
        }
        // Less than 7 days
        if (diff < 604800000) {
            const days = Math.floor(diff / 86400000);
            return `${days}d ago`;
        }
        // Default
        return date.toLocaleDateString();
    } catch (e) {
        return '';
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
