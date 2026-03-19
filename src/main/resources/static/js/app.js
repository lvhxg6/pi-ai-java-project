/**
 * AI Chat Web - Main Application JavaScript
 * Uses Alpine.js for reactive state management
 */

// Chat Application
function chatApp() {
    return {
        // State
        sessions: [],
        currentSessionId: null,
        currentSession: null,
        messages: [],
        models: [],
        selectedModel: '',
        inputMessage: '',
        isStreaming: false,
        streamingContent: '',
        eventSource: null,
        contextUsage: {
            currentTokens: 0,
            contextWindow: 128000,
            usagePercent: 0
        },

        // Initialize
        async init() {
            await this.loadSessions();
            await this.loadModels();
            
            // Select first session if available
            if (this.sessions.length > 0) {
                await this.selectSession(this.sessions[0].id);
            }
        },

        // Load sessions
        async loadSessions() {
            try {
                const response = await fetch('/api/sessions');
                if (response.ok) {
                    this.sessions = await response.json();
                }
            } catch (error) {
                console.error('Failed to load sessions:', error);
            }
        },

        // Load models
        async loadModels() {
            try {
                const response = await fetch('/api/models');
                if (response.ok) {
                    this.models = await response.json();
                    if (this.models.length > 0 && !this.selectedModel) {
                        this.selectedModel = this.models[0].id;
                    }
                }
            } catch (error) {
                console.error('Failed to load models:', error);
            }
        },

        // Create new session
        async createSession() {
            try {
                const response = await fetch('/api/sessions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name: 'New Chat' })
                });
                if (response.ok) {
                    const session = await response.json();
                    this.sessions.unshift(session);
                    await this.selectSession(session.id);
                }
            } catch (error) {
                console.error('Failed to create session:', error);
            }
        },

        // Select session
        async selectSession(sessionId) {
            this.currentSessionId = sessionId;
            this.currentSession = this.sessions.find(s => s.id === sessionId);
            await this.loadMessages(sessionId);
            await this.loadContextUsage(sessionId);
        },

        // Load messages for session
        async loadMessages(sessionId) {
            try {
                const response = await fetch(`/api/sessions/${sessionId}/messages`);
                if (response.ok) {
                    this.messages = await response.json();
                    this.scrollToBottom();
                }
            } catch (error) {
                console.error('Failed to load messages:', error);
            }
        },

        // Load context usage
        async loadContextUsage(sessionId) {
            try {
                const response = await fetch(`/api/chat/${sessionId}/context-usage`);
                if (response.ok) {
                    this.contextUsage = await response.json();
                }
            } catch (error) {
                console.error('Failed to load context usage:', error);
            }
        },

        // Delete session
        async deleteSession(sessionId) {
            if (!confirm('Delete this chat?')) return;
            
            try {
                const response = await fetch(`/api/sessions/${sessionId}`, {
                    method: 'DELETE'
                });
                if (response.ok) {
                    this.sessions = this.sessions.filter(s => s.id !== sessionId);
                    if (this.currentSessionId === sessionId) {
                        this.currentSessionId = null;
                        this.currentSession = null;
                        this.messages = [];
                        if (this.sessions.length > 0) {
                            await this.selectSession(this.sessions[0].id);
                        }
                    }
                }
            } catch (error) {
                console.error('Failed to delete session:', error);
            }
        },

        // Send message
        async sendMessage() {
            if (!this.inputMessage.trim() || this.isStreaming || !this.currentSessionId) return;

            const message = this.inputMessage.trim();
            this.inputMessage = '';
            
            // Add user message to UI
            this.messages.push({
                id: Date.now(),
                role: 'user',
                content: message
            });
            this.scrollToBottom();

            // Start streaming
            this.isStreaming = true;
            this.streamingContent = '';

            try {
                this.eventSource = new EventSource(
                    `/api/chat/${this.currentSessionId}/message?content=${encodeURIComponent(message)}&modelId=${this.selectedModel}`
                );

                this.eventSource.onmessage = (event) => {
                    const data = JSON.parse(event.data);
                    this.handleChatEvent(data);
                };

                this.eventSource.onerror = (error) => {
                    console.error('SSE error:', error);
                    this.finishStreaming();
                };
            } catch (error) {
                console.error('Failed to send message:', error);
                this.finishStreaming();
            }
        },

        // Handle chat events
        handleChatEvent(event) {
            switch (event.type) {
                case 'text':
                    this.streamingContent += event.content;
                    this.scrollToBottom();
                    break;
                case 'done':
                    this.messages.push({
                        id: Date.now(),
                        role: 'assistant',
                        content: this.streamingContent
                    });
                    this.finishStreaming();
                    this.loadContextUsage(this.currentSessionId);
                    break;
                case 'error':
                    console.error('Chat error:', event.message);
                    this.finishStreaming();
                    alert('Error: ' + event.message);
                    break;
            }
        },

        // Finish streaming
        finishStreaming() {
            this.isStreaming = false;
            this.streamingContent = '';
            if (this.eventSource) {
                this.eventSource.close();
                this.eventSource = null;
            }
        },

        // Abort chat
        async abortChat() {
            if (!this.currentSessionId) return;
            
            try {
                await fetch(`/api/chat/${this.currentSessionId}/abort`, {
                    method: 'POST'
                });
            } catch (error) {
                console.error('Failed to abort:', error);
            }
            this.finishStreaming();
        },

        // Switch model
        async switchModel() {
            if (!this.currentSessionId || !this.selectedModel) return;
            
            try {
                await fetch(`/api/chat/${this.currentSessionId}/model`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ modelId: this.selectedModel })
                });
                await this.loadContextUsage(this.currentSessionId);
            } catch (error) {
                console.error('Failed to switch model:', error);
            }
        },

        // Render markdown
        renderMarkdown(content) {
            if (!content) return '';
            try {
                return marked.parse(content);
            } catch (error) {
                return content;
            }
        },

        // Format date
        formatDate(dateString) {
            if (!dateString) return '';
            const date = new Date(dateString);
            const now = new Date();
            const diff = now - date;
            
            if (diff < 60000) return 'Just now';
            if (diff < 3600000) return Math.floor(diff / 60000) + 'm ago';
            if (diff < 86400000) return Math.floor(diff / 3600000) + 'h ago';
            return date.toLocaleDateString();
        },

        // Scroll to bottom
        scrollToBottom() {
            this.$nextTick(() => {
                const container = document.getElementById('messages-container');
                if (container) {
                    container.scrollTop = container.scrollHeight;
                }
            });
        }
    };
}

// Settings Application
function settingsApp() {
    return {
        // State
        providers: [],
        showAddForm: false,
        editingProvider: null,
        formData: {
            name: '',
            providerId: '',
            apiKey: ''
        },

        // Initialize
        async init() {
            await this.loadProviders();
        },

        // Load providers
        async loadProviders() {
            try {
                const response = await fetch('/api/providers');
                if (response.ok) {
                    this.providers = await response.json();
                }
            } catch (error) {
                console.error('Failed to load providers:', error);
            }
        },

        // Edit provider
        editProvider(provider) {
            this.editingProvider = provider;
            this.formData = {
                name: provider.name,
                providerId: provider.providerId,
                apiKey: ''
            };
        },

        // Save provider
        async saveProvider() {
            try {
                const url = this.editingProvider 
                    ? `/api/providers/${this.editingProvider.id}`
                    : '/api/providers';
                const method = this.editingProvider ? 'PUT' : 'POST';
                
                const body = {
                    name: this.formData.name,
                    providerId: this.formData.providerId
                };
                if (this.formData.apiKey) {
                    body.apiKey = this.formData.apiKey;
                }

                const response = await fetch(url, {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });

                if (response.ok) {
                    await this.loadProviders();
                    this.closeForm();
                } else {
                    const error = await response.json();
                    alert('Error: ' + error.message);
                }
            } catch (error) {
                console.error('Failed to save provider:', error);
                alert('Failed to save provider');
            }
        },

        // Delete provider
        async deleteProvider(providerId) {
            if (!confirm('Delete this provider?')) return;
            
            try {
                const response = await fetch(`/api/providers/${providerId}`, {
                    method: 'DELETE'
                });
                if (response.ok) {
                    await this.loadProviders();
                }
            } catch (error) {
                console.error('Failed to delete provider:', error);
            }
        },

        // Close form
        closeForm() {
            this.showAddForm = false;
            this.editingProvider = null;
            this.formData = {
                name: '',
                providerId: '',
                apiKey: ''
            };
        }
    };
}
