package com.bervan.streamingapp.view;

import com.vaadin.flow.component.AttachEvent;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class AbstractRemoteControlSupportedView extends AbstractStreamingPage {

    public AbstractRemoteControlSupportedView(String route, String... notVisibleButtonRoutes) {
        super(route, notVisibleButtonRoutes);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        boolean roleStreaming = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(e -> e.getAuthority().equals("ROLE_STREAMING"));
        addRemoteControlSupport(roleStreaming);
    }

    protected final void addRemoteControlSupport(boolean isTV) {
        // Add WebSocket connection for TV mode
        getElement().executeJs("""
                    class RemoteControlManager {
                        constructor() {
                            this.ws = null;
                            // this.isTV = this.detectTVMode();
                            this.isTV = $0; //if role streaming
                            this.roomId = this.getRoomId();
                            this.connect();
                            this.setupMediaSessionAPI();
                            this.setupKeyboardControls();
                        }
                
                        detectTVMode() {
                            // Detect if running on TV (large screen, specific user agent, etc.)
                            const userAgent = navigator.userAgent.toLowerCase();
                            const isTV = userAgent.includes('smart-tv') ||
                                       userAgent.includes('samsung') && userAgent.includes('tizen') ||
                                       userAgent.includes('webos') ||
                                       window.screen.width >= 1920;
                            return isTV;
                        }
                
                        getRoomId() {
                            // Generate or get room ID from URL parameter or localStorage
                            const urlParams = new URLSearchParams(window.location.search);
                            let roomId = urlParams.get('roomId') || localStorage.getItem('roomId');
                            if (!roomId || roomId.length > 5) {
                                roomId = 'room_' + Math.floor(Math.random() * 90000 + 10000);                                localStorage.setItem('roomId', roomId);
                            }
                            return roomId;
                        }
                
                        connect() {
                            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                            const wsUrl = `${protocol}//${window.location.host}/ws/remote-control?deviceType=${this.isTV ? 'TV' : 'REMOTE'}&roomId=${this.roomId}`;
                
                            this.ws = new WebSocket(wsUrl);
                
                            this.ws.onopen = () => {
                                console.log('Remote control WebSocket connected');
                                if (this.isTV) {
                                    this.displayConnectionInfo();
                                }
                            };
                
                            this.ws.onmessage = (event) => {
                                const message = JSON.parse(event.data);
                                this.handleRemoteCommand(message);
                            };
                
                            this.ws.onclose = () => {
                                console.log('Remote control WebSocket disconnected');
                                // Reconnect after 3 seconds
                                setTimeout(() => this.connect(), 3000);
                            };
                        }
                
                        displayConnectionInfo() {
                            const connectionDiv = document.createElement('div');
                            connectionDiv.id = 'remote-connection-info';
                            connectionDiv.innerHTML = `
                                <div style="position: fixed; top: 20px; right: 20px; background: rgba(0,0,0,0.8); 
                                            color: white; padding: 15px; border-radius: 10px; z-index: 9999;">
                                    <h4>Remote Control</h4>
                                    <p>Room ID: ${this.roomId}</p>
                                    <button onclick="this.parentElement.parentElement.style.display='none'">×</button>
                                </div>
                            `;
                            document.body.appendChild(connectionDiv);
                
                            // Auto-hide after 60 seconds
                            setTimeout(() => {
                                const infoDiv = document.getElementById('remote-connection-info');
                                if (infoDiv) infoDiv.style.display = 'none';
                            }, 60000);
                        }
                
                        setupMediaSessionAPI() {
                            if ('mediaSession' in navigator) {
                                navigator.mediaSession.setActionHandler('play', () => {
                                    this.sendCommand('PLAY', 'video', null);
                                });
                
                                navigator.mediaSession.setActionHandler('pause', () => {
                                    this.sendCommand('PAUSE', 'video', null);
                                });
                
                                navigator.mediaSession.setActionHandler('seekbackward', () => {
                                    this.sendCommand('SEEK', 'video', {relative: -10});
                                });
                
                                navigator.mediaSession.setActionHandler('seekforward', () => {
                                    this.sendCommand('SEEK', 'video', {relative: 10});
                                });
                            }
                        }
                
                        setupKeyboardControls() {
                            document.addEventListener('keydown', (event) => {
                                if (this.isTV) return; // Only for remote devices
                
                                switch(event.code) {
                                    case 'Space':
                                        event.preventDefault();
                                        this.sendCommand('TOGGLE_PLAY', 'video', null);
                                        break;
                                    case 'ArrowLeft':
                                        event.preventDefault();
                                        this.sendCommand('SEEK', 'video', {relative: -10});
                                        break;
                                    case 'ArrowRight':
                                        event.preventDefault();
                                        this.sendCommand('SEEK', 'video', {relative: 10});
                                        break;
                                    case 'ArrowUp':
                                        event.preventDefault();
                                        this.sendCommand('VOLUME', 'video', {relative: 0.1});
                                        break;
                                    case 'ArrowDown':
                                        event.preventDefault();
                                        this.sendCommand('VOLUME', 'video', {relative: -0.1});
                                        break;
                                    case 'KeyF':
                                        event.preventDefault();
                                        this.sendCommand('FULLSCREEN', 'video', null);
                                        break;
                                }
                            });
                        }
                
                        handleRemoteCommand(message) {
                            const video = document.querySelector('video');
                            if (!video) return;
                
                            switch(message.action) {
                                case 'PLAY':
                                    video.play();
                                    break;
                                case 'PAUSE':
                                    video.pause();
                                    break;
                                case 'TOGGLE_PLAY':
                                    video.paused ? video.play() : video.pause();
                                    break;
                                case 'SEEK':
                                    if (message.data && message.data.relative) {
                                        video.currentTime += message.data.relative;
                                    }
                                    break;
                                case 'VOLUME':
                                    if (message.data && message.data.relative) {
                                        video.volume = Math.max(0, Math.min(1, video.volume + message.data.relative));
                                    }
                                    break;
                                case 'MAXIMIZE':
                                    this.toggleVideoMaximize(video);
                                    break;
                                case 'PIP':
                                    if (document.pictureInPictureEnabled && !video.disablePictureInPicture) {
                                        if (document.pictureInPictureElement) {
                                            document.exitPictureInPicture();
                                        } else {
                                            video.requestPictureInPicture().catch(err => {
                                                console.log('PiP failed:', err);
                                            });
                                        }
                                    }
                                    break;
                                case 'FULLSCREEN_PROMPT':
                                    this.showFullscreenPrompt(video);
                                    break;
                                case 'NAVIGATE':
                                    window.location.href = message.data.url;
                                    break;
                            }
                        }
                
                        toggleVideoMaximize(video) {
                           if (video.classList.contains('maximized')) {
                               video.classList.remove('maximized');
                               video.style.cssText = 'width: 90vw; height: 80vh; max-width: 100%; max-height: 100%;';
                               document.body.style.overflow = '';
                               const container = video.closest('#videoContainer');
                               if (container) {
                                   const siblings = container.parentElement.children;
                                   for (let i = 0; i < siblings.length; i++) {
                                       if (siblings[i] !== container) {
                                           siblings[i].style.display = '';
                                       }
                                   }
                               }
                           } else {
                               video.classList.add('maximized');
                               video.style.cssText = `
                                   position: fixed !important;
                                   top: 0 !important;
                                   left: 0 !important;
                                   width: 100vw !important;
                                   height: 100vh !important;
                                   z-index: 9999 !important;
                                   object-fit: contain !important;
                                   background: black !important;
                               `;
                               document.body.style.overflow = 'hidden';
                               const container = video.closest('#videoContainer');
                               if (container) {
                                   const siblings = container.parentElement.children;
                                   for (let i = 0; i < siblings.length; i++) {
                                       if (siblings[i] !== container) {
                                           siblings[i].style.display = 'none';
                                       }
                                   }
                               }
                           }
                       }
                
                        showFullscreenPrompt(video) {
                            const existingPrompt = document.getElementById('fullscreen-prompt');
                            if (existingPrompt) existingPrompt.remove();
                
                            const prompt = document.createElement('div');
                            prompt.id = 'fullscreen-prompt';
                            prompt.innerHTML = `
                                <div style="position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
                                            background: rgba(0,0,0,0.95); color: white; padding: 30px;
                                            border-radius: 15px; z-index: 10000; text-align: center;
                                            box-shadow: 0 10px 30px rgba(0,0,0,0.5);">
                                    <h3 style="margin: 0 0 20px 0;">📱 Remote Control Request</h3>
                                    <p style="margin: 0 0 20px 0;">Remote wants to enter fullscreen mode</p>
                                    <div>
                                        <button id="fullscreen-accept" style="background: #4caf50; color: white; border: none;\s
                                                padding: 15px 25px; margin: 10px; border-radius: 8px; cursor: pointer; font-size: 16px;">
                                            ✓ Enter Fullscreen
                                        </button>
                                        <button id="fullscreen-cancel" style="background: #f44336; color: white; border: none;\s
                                                padding: 15px 25px; margin: 10px; border-radius: 8px; cursor: pointer; font-size: 16px;">
                                            ✗ Cancel
                                        </button>
                                    </div>
                                </div>
                            `;
                
                            document.body.appendChild(prompt);
                
                            document.getElementById('fullscreen-accept').onclick = () => {
                                if (video.requestFullscreen) {
                                    video.requestFullscreen();
                                } else if (video.webkitRequestFullscreen) {
                                    video.webkitRequestFullscreen();
                                }
                                prompt.remove();
                            };
                
                            document.getElementById('fullscreen-cancel').onclick = () => {
                                prompt.remove();
                            };
                
                            setTimeout(() => {
                                if (document.getElementById('fullscreen-prompt')) {
                                    prompt.remove();
                                }
                            }, 15000);
                        }
                
                
                
                        sendCommand(action, target, data) {
                            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                                const message = {
                                    action: action,
                                    target: target,
                                    data: data,
                                    roomId: this.roomId
                                };
                                this.ws.send(JSON.stringify(message));
                            }
                        }
                    }
                
                    // Initialize remote control when page loads
                    window.remoteControl = new RemoteControlManager();
                """, isTV);
    }
}