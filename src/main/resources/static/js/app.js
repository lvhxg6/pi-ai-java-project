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
        groupedModels: [],
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

        // Load models (grouped by brand)
        async loadModels() {
            try {
                const response = await fetch('/api/models?grouped=true');
                if (response.ok) {
                    this.groupedModels = await response.json();
                    // Build flat models list for backward compatibility
                    this.models = [];
                    for (const group of this.groupedModels) {
                        for (const model of group.models) {
                            this.models.push({...model, brandName: group.brandName});
                        }
                    }
                    // Select first model if none selected
                    if (this.models.length > 0 && !this.selectedModel) {
                        this.selectedModel = this.models[0].provider + ':' + this.models[0].id;
                    }
                }
            } catch (error) {
                console.error('Failed to load grouped models:', error);
                // Fallback to flat list
                try {
                    const response = await fetch('/api/models');
                    if (response.ok) {
                        this.models = await response.json();
                        if (this.models.length > 0 && !this.selectedModel) {
                            this.selectedModel = this.models[0].id;
                        }
                    }
                } catch (e) {
                    console.error('Fallback also failed:', e);
                }
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
                // Parse provider:modelId format for the SSE URL
                let modelIdParam = this.selectedModel;
                if (this.selectedModel.includes(':')) {
                    modelIdParam = this.selectedModel.split(':').slice(1).join(':');
                }

                this.eventSource = new EventSource(
                    `/api/chat/${this.currentSessionId}/message?content=${encodeURIComponent(message)}&modelId=${modelIdParam}`
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
            
            // Parse provider:modelId format
            let provider = '';
            let modelId = this.selectedModel;
            if (this.selectedModel.includes(':')) {
                const parts = this.selectedModel.split(':');
                provider = parts[0];
                modelId = parts.slice(1).join(':');
            }
            
            try {
                await fetch(`/api/chat/${this.currentSessionId}/model`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ provider: provider, modelId: modelId })
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

// Settings Application - Brand Management
function settingsApp() {
    return {
        // State
        brands: [],
        selectedBrandId: null,
        selectedBrand: null,
        editApiKey: '',
        editBaseUrl: '',
        editEnabled: true,
        showApiKey: false,
        loading: false,
        message: '',
        messageType: '',

        // New model form
        newModelId: '',
        newModelName: '',
        newModelContextWindow: 128000,
        newModelMaxTokens: 4096,
        showAddModel: false,

        // New custom brand form
        showAddBrand: false,
        newBrandName: '',
        newBrandBaseUrl: '',
        newBrandApiKey: '',

        // Lifecycle
        init() {
            this.loadBrands();
        },

        // Load all brands
        async loadBrands() {
            try {
                const response = await fetch('/api/brands');
                if (response.ok) {
                    this.brands = await response.json();
                }
            } catch (error) {
                console.error('Failed to load brands:', error);
            }
        },

        // Select a brand and load its details
        async selectBrand(brandId) {
            try {
                const response = await fetch('/api/brands/' + brandId);
                if (response.ok) {
                    this.selectedBrand = await response.json();
                    this.selectedBrandId = brandId;
                    this.editApiKey = '';
                    this.editBaseUrl = this.selectedBrand.baseUrl || '';
                    this.editEnabled = this.selectedBrand.enabled;
                    this.showApiKey = false;
                    this.showAddModel = false;
                    this.message = '';
                }
            } catch (error) {
                console.error('Failed to load brand:', error);
            }
        },

        // Save brand configuration (API Key, Base URL, enabled)
        async saveBrand() {
            if (!this.selectedBrand) return;
            this.loading = true;
            this.message = '';

            try {
                const body = {
                    baseUrl: this.editBaseUrl,
                    enabled: this.editEnabled
                };
                // Only send apiKey if user typed something new
                if (this.editApiKey) {
                    body.apiKey = this.editApiKey;
                }

                const response = await fetch('/api/brands/' + this.selectedBrand.id, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });

                if (response.ok) {
                    this.showMessage('Configuration saved successfully', 'success');
                    await this.loadBrands();
                    await this.selectBrand(this.selectedBrand.id);
                } else {
                    const error = await response.json();
                    this.showMessage(error.message || 'Failed to save', 'error');
                }
            } catch (error) {
                console.error('Failed to save brand:', error);
                this.showMessage('Failed to save configuration', 'error');
            } finally {
                this.loading = false;
            }
        },

        // Toggle enabled status
        async toggleEnabled(brandId, enabled) {
            try {
                const response = await fetch('/api/brands/' + brandId, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ enabled: enabled })
                });

                if (response.ok) {
                    await this.loadBrands();
                    await this.selectBrand(brandId);
                } else {
                    const error = await response.json();
                    this.showMessage(error.message || 'Failed to toggle', 'error');
                }
            } catch (error) {
                console.error('Failed to toggle enabled:', error);
            }
        },

        // Create a custom brand
        async createCustomBrand() {
            try {
                const response = await fetch('/api/brands', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        name: this.newBrandName,
                        baseUrl: this.newBrandBaseUrl,
                        apiKey: this.newBrandApiKey
                    })
                });

                if (response.ok) {
                    const brand = await response.json();
                    this.showAddBrand = false;
                    this.newBrandName = '';
                    this.newBrandBaseUrl = '';
                    this.newBrandApiKey = '';
                    await this.loadBrands();
                    await this.selectBrand(brand.id);
                } else {
                    const error = await response.json();
                    alert('Error: ' + (error.message || 'Failed to create brand'));
                }
            } catch (error) {
                console.error('Failed to create brand:', error);
                alert('Failed to create brand');
            }
        },

        // Delete a custom brand
        async deleteCustomBrand(brandId) {
            if (!confirm('Delete this brand and all its configuration?')) return;

            try {
                const response = await fetch('/api/brands/' + brandId, {
                    method: 'DELETE'
                });

                if (response.ok) {
                    this.selectedBrand = null;
                    this.selectedBrandId = null;
                    await this.loadBrands();
                } else {
                    const error = await response.json();
                    this.showMessage(error.message || 'Failed to delete', 'error');
                }
            } catch (error) {
                console.error('Failed to delete brand:', error);
            }
        },

        // Add a custom model to the selected brand
        async addModel() {
            if (!this.selectedBrand || !this.newModelId || !this.newModelName) return;

            const newModel = {
                id: this.newModelId,
                name: this.newModelName,
                contextWindow: this.newModelContextWindow || 128000,
                maxTokens: this.newModelMaxTokens || 4096,
                custom: true
            };

            const models = [...(this.selectedBrand.models || []), newModel];
            await this.saveModels(models);

            // Reset form
            this.newModelId = '';
            this.newModelName = '';
            this.newModelContextWindow = 128000;
            this.newModelMaxTokens = 4096;
            this.showAddModel = false;
        },

        // Remove a custom model from the selected brand
        async removeModel(modelId) {
            if (!this.selectedBrand) return;
            const models = (this.selectedBrand.models || []).filter(m => m.id !== modelId);
            await this.saveModels(models);
        },

        // Save the model list for the selected brand
        async saveModels(models) {
            if (!this.selectedBrand) return;

            try {
                const response = await fetch('/api/brands/' + this.selectedBrand.id + '/models', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ models: models })
                });

                if (response.ok) {
                    await this.selectBrand(this.selectedBrand.id);
                    this.showMessage('Models updated', 'success');
                } else {
                    const error = await response.json();
                    this.showMessage(error.message || 'Failed to update models', 'error');
                }
            } catch (error) {
                console.error('Failed to save models:', error);
                this.showMessage('Failed to update models', 'error');
            }
        },

        // Show a temporary message
        showMessage(msg, type) {
            this.message = msg;
            this.messageType = type;
            setTimeout(() => { this.message = ''; }, 3000);
        }
    };
}
