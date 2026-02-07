package com.bervan.streamingapp.view;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
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

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        cleanupRemoteControl();
    }

    private void cleanupRemoteControl() {
        getElement().executeJs("""
            if (window.remoteControl) {
                window.remoteControl.cleanup();
                window.remoteControl = null;
            }
        """);
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
                                roomId = '' + Math.floor(Math.random() * 90000 + 10000);                                localStorage.setItem('roomId', roomId);
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
                            // First show activation overlay that must be clicked
                            this.showActivationOverlay();
                        }

                        showActivationOverlay() {
                            const overlay = document.createElement('div');
                            overlay.id = 'remote-activation-overlay';
                            overlay.innerHTML = `
                                <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0;
                                            background: rgba(0,0,0,0.9); z-index: 10000;
                                            display: flex; flex-direction: column;
                                            justify-content: center; align-items: center;">
                                    <div style="text-align: center; color: white; padding: 30px;">
                                        <h2 style="font-size: 2rem; margin-bottom: 1rem;">📱 Remote Control</h2>
                                        <p style="font-size: 1.2rem; margin-bottom: 0.5rem;">Room ID: <strong style="font-size: 2rem; color: #4CAF50;">${this.roomId}</strong></p>
                                        <p style="color: #aaa; margin-bottom: 2rem;">Enter this code on your phone to connect</p>
                                        <button id="enable-remote-btn" style="
                                            background: linear-gradient(135deg, #4CAF50, #45a049);
                                            color: white; border: none; padding: 20px 50px;
                                            font-size: 1.5rem; border-radius: 50px;
                                            cursor: pointer; box-shadow: 0 4px 15px rgba(76,175,80,0.4);
                                            transition: transform 0.2s, box-shadow 0.2s;">
                                            ▶ Click to Enable Playback
                                        </button>
                                        <p style="color: #888; margin-top: 1.5rem; font-size: 0.9rem;">
                                            Browser requires a click before remote play works
                                        </p>
                                    </div>
                                </div>
                            `;
                            document.body.appendChild(overlay);

                            const btn = document.getElementById('enable-remote-btn');
                            btn.onmouseover = () => {
                                btn.style.transform = 'scale(1.05)';
                                btn.style.boxShadow = '0 6px 20px rgba(76,175,80,0.6)';
                            };
                            btn.onmouseout = () => {
                                btn.style.transform = 'scale(1)';
                                btn.style.boxShadow = '0 4px 15px rgba(76,175,80,0.4)';
                            };

                            btn.onclick = () => {
                                // Try to play video briefly to enable autoplay permission
                                const video = document.querySelector('video');
                                if (video) {
                                    video.muted = true;
                                    video.play().then(() => {
                                        video.pause();
                                        video.muted = false;
                                        video.currentTime = video.currentTime; // Reset position
                                    }).catch(() => {});
                                }

                                overlay.remove();
                                this.showConnectionInfo();
                            };
                        }

                        showConnectionInfo() {
                            const connectionDiv = document.createElement('div');
                            connectionDiv.id = 'remote-connection-info';
                            connectionDiv.innerHTML = `
                                <div style="position: fixed; top: 20px; right: 20px; background: rgba(0,0,0,0.8);
                                            color: white; padding: 15px; border-radius: 10px; z-index: 9999;">
                                    <div style="display: flex; align-items: center; gap: 10px;">
                                        <span style="width: 10px; height: 10px; background: #4CAF50; border-radius: 50%;"></span>
                                        <span>Remote Enabled</span>
                                    </div>
                                    <p style="margin: 5px 0 0 0; font-size: 0.9rem;">Room: ${this.roomId}</p>
                                    <button onclick="this.parentElement.parentElement.style.display='none'"
                                            style="position: absolute; top: 5px; right: 10px; background: none;
                                                   border: none; color: white; cursor: pointer; font-size: 1.2rem;">×</button>
                                </div>
                            `;
                            document.body.appendChild(connectionDiv);

                            // Auto-hide after 10 seconds
                            setTimeout(() => {
                                const infoDiv = document.getElementById('remote-connection-info');
                                if (infoDiv) infoDiv.style.display = 'none';
                            }, 10000);
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
                            // Store handler reference for cleanup
                            this.keydownHandler = (event) => {
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
                            };
                            document.addEventListener('keydown', this.keydownHandler);
                        }

                        cleanup() {
                            // Remove keyboard listener
                            if (this.keydownHandler) {
                                document.removeEventListener('keydown', this.keydownHandler);
                                this.keydownHandler = null;
                            }
                            // Close WebSocket
                            if (this.ws) {
                                this.ws.onclose = null; // Prevent reconnect
                                this.ws.close();
                                this.ws = null;
                            }
                            // Remove overlays and info divs
                            const overlay = document.getElementById('remote-activation-overlay');
                            if (overlay) overlay.remove();
                            const infoDiv = document.getElementById('remote-connection-info');
                            if (infoDiv) infoDiv.remove();
                        }
                
                        handleRemoteCommand(message) {
                            const video = document.querySelector('video');

                            switch(message.action) {
                                case 'PLAY':
                                    if (video) video.play();
                                    break;
                                case 'PAUSE':
                                    if (video) video.pause();
                                    break;
                                case 'TOGGLE_PLAY':
                                    if (video) video.paused ? video.play() : video.pause();
                                    break;
                                case 'SEEK':
                                    if (video && message.data && message.data.relative) {
                                        video.currentTime += message.data.relative;
                                    }
                                    break;
                                case 'VOLUME':
                                    if (video && message.data && message.data.relative) {
                                        video.volume = Math.max(0, Math.min(1, video.volume + message.data.relative));
                                    }
                                    break;
                                case 'MAXIMIZE':
                                    if (video) this.toggleVideoMaximize(video);
                                    break;
                                case 'PIP':
                                    if (video && document.pictureInPictureEnabled && !video.disablePictureInPicture) {
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
                                    if (video) this.showFullscreenPrompt(video);
                                    break;
                                case 'NAVIGATE':
                                    window.location.href = message.data.url;
                                    break;
                                case 'GET_TRACKS':
                                    this.sendTracksToRemote(video, message.data?.trackType);
                                    break;
                                case 'SET_AUDIO_TRACK':
                                    this.setAudioTrack(message.data?.index);
                                    break;
                                case 'SET_SUBTITLE_TRACK':
                                    this.setSubtitleTrack(video, message.data?.index);
                                    break;
                                case 'NEXT_EPISODE':
                                    this.navigateEpisode('next');
                                    break;
                                case 'PREV_EPISODE':
                                    this.navigateEpisode('prev');
                                    break;
                            }
                        }

                        sendTracksToRemote(video, trackType) {
                            const response = {
                                type: 'TRACKS_RESPONSE',
                                trackType: trackType,
                                tracks: []
                            };

                            // Find player ID from video element
                            const playerId = video ? video.id : null;

                            if (trackType === 'audio' || !trackType) {
                                // Try HLS audio tracks first
                                if (playerId && window['hls_audio_tracks_' + playerId]) {
                                    response.tracks = window['hls_audio_tracks_' + playerId];
                                    response.currentIndex = window['hls_current_audio_' + playerId] || 0;
                                } else if (video && video.audioTracks) {
                                    // Native audio tracks
                                    for (let i = 0; i < video.audioTracks.length; i++) {
                                        const track = video.audioTracks[i];
                                        response.tracks.push({
                                            index: i,
                                            name: track.label || ('Audio ' + (i + 1)),
                                            lang: track.language || 'und',
                                            enabled: track.enabled
                                        });
                                        if (track.enabled) response.currentIndex = i;
                                    }
                                }
                            }

                            if (trackType === 'subtitles' || !trackType) {
                                response.trackType = 'subtitles';
                                response.tracks = [{index: -1, name: 'Off', lang: 'off'}];

                                // Try HLS subtitle tracks first
                                if (playerId && window['hls_subtitle_tracks_' + playerId]) {
                                    response.tracks = response.tracks.concat(window['hls_subtitle_tracks_' + playerId]);
                                    response.currentIndex = window['hls_current_subtitle_' + playerId];
                                    if (response.currentIndex === undefined) response.currentIndex = -1;
                                } else if (video && video.textTracks) {
                                    // Native text tracks
                                    for (let i = 0; i < video.textTracks.length; i++) {
                                        const track = video.textTracks[i];
                                        if (track.kind === 'subtitles' || track.kind === 'captions') {
                                            response.tracks.push({
                                                index: i,
                                                name: track.label || ('Subtitle ' + (i + 1)),
                                                lang: track.language || 'und'
                                            });
                                            if (track.mode === 'showing') response.currentIndex = i;
                                        }
                                    }
                                    if (response.currentIndex === undefined) response.currentIndex = -1;
                                }
                            }

                            // Send response back through WebSocket
                            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                                this.ws.send(JSON.stringify(response));
                            }
                        }

                        setAudioTrack(index) {
                            const video = document.querySelector('video');
                            const playerId = video ? video.id : null;

                            // Try HLS first
                            if (playerId && window['hls_instance_' + playerId]) {
                                window['hls_instance_' + playerId].audioTrack = index;
                            } else if (video && video.audioTracks) {
                                // Native audio tracks
                                for (let i = 0; i < video.audioTracks.length; i++) {
                                    video.audioTracks[i].enabled = (i === index);
                                }
                            }
                        }

                        setSubtitleTrack(video, index) {
                            const playerId = video ? video.id : null;

                            if (index === -1) {
                                // Turn off all subtitles
                                if (playerId && window['hls_instance_' + playerId]) {
                                    window['hls_instance_' + playerId].subtitleTrack = -1;
                                }
                                if (video && video.textTracks) {
                                    for (let i = 0; i < video.textTracks.length; i++) {
                                        video.textTracks[i].mode = 'hidden';
                                    }
                                }
                                if (playerId) window['hls_current_subtitle_' + playerId] = -1;
                            } else if (index >= 1000) {
                                // Native text track (index offset by 1000)
                                const nativeIndex = index - 1000;
                                if (playerId && window['hls_instance_' + playerId]) {
                                    window['hls_instance_' + playerId].subtitleTrack = -1;
                                }
                                if (video && video.textTracks) {
                                    for (let i = 0; i < video.textTracks.length; i++) {
                                        video.textTracks[i].mode = (i === nativeIndex) ? 'showing' : 'hidden';
                                    }
                                }
                                if (playerId) window['hls_current_subtitle_' + playerId] = index;
                            } else {
                                // HLS subtitle track
                                if (playerId && window['hls_instance_' + playerId]) {
                                    window['hls_instance_' + playerId].subtitleTrack = index;
                                }
                                if (playerId) window['hls_current_subtitle_' + playerId] = index;
                            }

                            // Update UI if available
                            if (typeof window.updateTrackUI === 'function' && playerId) {
                                window.updateTrackUI(playerId);
                            }
                        }

                        navigateEpisode(direction) {
                            // Find navigation buttons on page
                            const buttons = document.querySelectorAll('.option-button');
                            for (const btn of buttons) {
                                const text = btn.textContent.toLowerCase();
                                if (direction === 'next' && text.includes('next')) {
                                    btn.click();
                                    return;
                                }
                                if (direction === 'prev' && text.includes('previous')) {
                                    btn.click();
                                    return;
                                }
                            }
                            console.log('No ' + direction + ' episode button found');
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