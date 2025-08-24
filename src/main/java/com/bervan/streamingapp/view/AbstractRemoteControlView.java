package com.bervan.streamingapp.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

public abstract class AbstractRemoteControlView extends AbstractStreamingPage implements BeforeEnterObserver {
    public static final String ROUTE_NAME = "/streaming-platform/remote-control";

    private String roomId;

    public AbstractRemoteControlView() {
        super(ROUTE_NAME, AbstractVideoPlayerView.ROUTE_NAME, AbstractVideoDetailsView.ROUTE_NAME);
        createUI();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        roomId = event.getLocation().getQueryParameters()
                .getSingleParameter("roomId").orElse("default");
    }

    private void createUI() {
        H1 title = new H1("🎮 TV Remote Control");
        title.getStyle().set("text-align", "center");

        Input roomIdInput = new Input();
        roomIdInput.setPlaceholder("Enter room ID");
        if (roomId != null) {
            roomIdInput.setValue(roomId);

        }

        roomIdInput.addValueChangeListener(e -> {
            roomId = e.getValue();
        });

        Button connectButton = new Button("Connect");
        connectButton.addClickListener(e -> {
            if (roomId != null) {
                addRemoteControlScript();
            }
        });

        // Playback controls
        HorizontalLayout playbackControls = new HorizontalLayout();
        playbackControls.setSpacing(true);

        Button playPause = new Button("⏯️ Play/Pause");
        playPause.addClickListener(e -> executeJS("window.remoteControl.sendCommand('TOGGLE_PLAY', 'video', null)"));

        Button seekBack = new Button("⏪ -10s");
        seekBack.addClickListener(e -> executeJS("window.remoteControl.sendCommand('SEEK', 'video', {relative: -10})"));

        Button seekForward = new Button("⏩ +10s");
        seekForward.addClickListener(e -> executeJS("window.remoteControl.sendCommand('SEEK', 'video', {relative: 10})"));

        playbackControls.add(seekBack, playPause, seekForward);

        // Volume controls
        HorizontalLayout volumeControls = new HorizontalLayout();
        volumeControls.setSpacing(true);

        Button volumeDown = new Button("🔉 Vol-");
        volumeDown.addClickListener(e -> executeJS("window.remoteControl.sendCommand('VOLUME', 'video', {relative: -0.1})"));

        Button volumeUp = new Button("🔊 Vol+");
        volumeUp.addClickListener(e -> executeJS("window.remoteControl.sendCommand('VOLUME', 'video', {relative: 0.1})"));

        Button fullscreen = new Button("⛶ Maximize Video");
        fullscreen.addClickListener(e -> executeJS("window.remoteControl.sendCommand('MAXIMIZE', 'video', null)"));

        Button pipMode = new Button("📺 Picture-in-Picture");
        pipMode.addClickListener(e -> executeJS("window.remoteControl.sendCommand('PIP', 'video', null)"));

        Button fullscreenPrompt = new Button("🖥️ Request Fullscreen");
        fullscreenPrompt.addClickListener(e -> executeJS("window.remoteControl.sendCommand('FULLSCREEN_PROMPT', 'video', null)"));

        volumeControls.add(volumeDown, volumeUp, fullscreen, pipMode, fullscreenPrompt);


        volumeControls.add(volumeDown, volumeUp, fullscreen);

        // Navigation
        TextField urlField = new TextField("Navigate to URL");
        urlField.setPlaceholder("Enter URL or path");
        urlField.setWidthFull();

        Button navigate = new Button("Navigate");
        navigate.addClickListener(e -> {
            String url = urlField.getValue();
            if (!url.isEmpty()) {
                executeJS("window.remoteControl.sendCommand('NAVIGATE', 'page', {url: '" + url + "'})");
            }
        });

        // Quick navigation buttons
        HorizontalLayout quickNav = new HorizontalLayout();
        Button home = new Button("🏠 Home");
        home.addClickListener(e -> executeJS("window.remoteControl.sendCommand('NAVIGATE', 'page', {url: '/'})"));

        Button videos = new Button("📺 Videos");
        videos.addClickListener(e -> executeJS("window.remoteControl.sendCommand('NAVIGATE', 'page', {url: '/videos'})"));

        quickNav.add(home, videos);

        add(title, new HorizontalLayout(roomIdInput, connectButton), playbackControls, volumeControls, urlField, navigate, quickNav);
    }

    private void executeJS(String script) {
        getElement().executeJs(script);
    }

    private void addRemoteControlScript() {
        getElement().executeJs("""
                    // Initialize remote control for mobile device
                    class MobileRemoteControl {
                        constructor(roomId) {
                            this.roomId = roomId;
                            this.connect();
                        }
                
                        connect() {
                            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                            const wsUrl = `${protocol}//${window.location.host}/ws/remote-control?deviceType=REMOTE&roomId=${this.roomId}`;
                
                            this.ws = new WebSocket(wsUrl);
                
                            this.ws.onopen = () => {
                                console.log('Mobile remote connected');
                                this.showStatus('Connected to TV', 'success');
                            };
                
                            this.ws.onclose = () => {
                                console.log('Mobile remote disconnected');
                                this.showStatus('Disconnected', 'error');
                                setTimeout(() => this.connect(), 3000);
                            };
                
                            this.ws.onerror = (error) => {
                                console.error('WebSocket error:', error);
                                this.showStatus('Connection error', 'error');
                            };
                        }
                
                        sendCommand(action, target, data) {
                            console.log("sendCommand", action, target, data);
                            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                                const message = {
                                    action: action,
                                    target: target,
                                    data: data,
                                    roomId: this.roomId
                                };
                                this.ws.send(JSON.stringify(message));
                                this.showStatus(`Sent: ${action}`, 'info');
                            } else {
                                this.showStatus('Not connected', 'error');
                            }
                        }
                
                        showStatus(message, type) {
                            // Create or update status indicator
                            let statusDiv = document.getElementById('remote-status');
                            if (!statusDiv) {
                                statusDiv = document.createElement('div');
                                statusDiv.id = 'remote-status';
                                statusDiv.style.cssText = `
                                    position: fixed; top: 60px; right: 10px;
                                    padding: 10px; border-radius: 5px;
                                    z-index: 9999; transition: all 0.3s ease;
                                    font-weight: bold; color: white;
                                `;
                                document.body.appendChild(statusDiv);
                            }
                
                            statusDiv.textContent = message;
                            statusDiv.className = `status-${type}`;
                
                            // Style based on type
                            switch(type) {
                                case 'success':
                                    statusDiv.style.backgroundColor = '#4caf50';
                                    break;
                                case 'error':
                                    statusDiv.style.backgroundColor = '#f44336';
                                    break;
                                case 'info':
                                    statusDiv.style.backgroundColor = '#2196f3';
                                    break;
                            }
                
                            // Auto-hide after 2 seconds
                            setTimeout(() => {
                                if (statusDiv) statusDiv.style.opacity = '0.5';
                            }, 2000);
                        }
                    }
                
                    window.remoteControl = new MobileRemoteControl($0);
                """, roomId);
    }
}