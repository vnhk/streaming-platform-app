package com.bervan.streamingapp.view;

import com.bervan.common.service.AuthService;
import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.WatchDetails;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractVideoPlayerView extends AbstractStreamingPage implements HasUrlParameter<String> {
    public static final String ROUTE_NAME = "/streaming-platform/video-player";
    private final BervanLogger logger;
    private final VideoManager videoManager;

    public AbstractVideoPlayerView(BervanLogger logger, VideoManager videoManager) {
        super(ROUTE_NAME, AbstractVideoDetailsView.ROUTE_NAME);
        this.logger = logger;
        this.videoManager = videoManager;
    }

    @Override
    public void setParameter(BeforeEvent event, String s) {
        String videoId = event.getRouteParameters().get("___url_parameter").orElse(UUID.randomUUID().toString());
        init(videoId);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        boolean roleStreaming = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(e -> e.getAuthority().equals("ROLE_STREAMING"));
        addRemoteControlSupport(roleStreaming);
    }

    private void init(String videoId) {
        try {
            List<Metadata> video = videoManager.loadById(videoId);
            if (video.size() != 1) {
                logger.error("Could not find video based on provided id!");
                showErrorNotification("Could not find video!");
                return;
            }

            Metadata mainDirectory = videoManager.getMainMovieFolder(video.get(0));

            if (mainDirectory != null) {
                Button detailsButton = new Button(mainDirectory.getFilename() + " - Details");
                detailsButton.addClassName("option-button");
                detailsButton.addClickListener(click ->
                        UI.getCurrent().navigate("/streaming-platform/details/" + mainDirectory.getId())
                );
                add(detailsButton);
            }

            Optional<Metadata> prevVideo = videoManager.getPrevVideo(video.get(0));
            Optional<Metadata> nextVideo = videoManager.getNextVideo(video.get(0));

            if (prevVideo.isPresent()) {
                Button prevButton = new Button("Previous episode");
                prevButton.addClassName("option-button");
                prevButton.addClickListener(click ->
                        UI.getCurrent().getPage().setLocation("/streaming-platform/video-player/" + prevVideo.get().getId())
                );
                add(prevButton);
            }

            if (nextVideo.isPresent()) {
                Button nextButton = new Button("Next episode");
                nextButton.addClassName("option-button");
                nextButton.addClickListener(click ->
                        UI.getCurrent().getPage().setLocation("/streaming-platform/video-player/" + nextVideo.get().getId())
                );
                add(nextButton);
            }

            add(new Hr(), new H4("Video: " + video.get(0).getFilename()));

            setSizeFull();
            setSpacing(false);
            setPadding(true);

            WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(
                    AuthService.getLoggedUserId().toString(), videoId
            );

            double enDelay = watchDetails.getSubtitleDelayEN();
            double plDelay = watchDetails.getSubtitleDelayPL();

            String videoSrc = "/storage/videos/video/" + videoId;
            Div videoContainer = new Div();
            videoContainer.setId("videoContainer");

            getElement().executeJs(
                    "const style = document.createElement('style');" +
                            "style.textContent = `" +
                            "  #videoContainer {" +
                            "    display: flex;" +
                            "    justify-content: center;" +
                            "    align-items: center;" +
                            "    flex-direction: column;" +
                            "    width: 100%; " +
                            "  }" +
                            "  #videoPlayer {" +
                            "    width: 90vw; " +
                            "    height: 80vh;" +
                            "    max-width: 100%; " +
                            "    max-height: 100%; " +
                            "  }" +
                            "  .custom-button {" +
                            "    margin: 10px;" +
                            "    padding: 10px 20px;" +
                            "    border: none;" +
                            "    background-color: #007bff;" +
                            "    color: white;" +
                            "    font-size: 16px;" +
                            "    border-radius: 5px;" +
                            "    cursor: pointer;" +
                            "    transition: background-color 0.3s ease;" +
                            "  }" +
                            "  .custom-button:hover {" +
                            "    background-color: #0056b3;" +
                            "  }" +
                            "`;" +
                            "document.head.appendChild(style);"
            );

            videoContainer.getElement().setProperty(
                    "innerHTML",
                    "<div id='videoContainer'>" +
                            "  <video id='videoPlayer' controls playsinline preload='auto'>" +
                            "    <source src='" + videoSrc + "' type='video/mp4'>" +
                            "    <track id='trackEN' kind='subtitles' src='/storage/videos/subtitles/" + videoId + "/en' srclang='en' label='English' default>" +
                            "    <track id='trackPL' kind='subtitles' src='/storage/videos/subtitles/" + videoId + "/pl' srclang='pl' label='Polish'>" +
                            "  </video>" +
                            "  <div style='margin-top: 10px; display: flex; flex-direction: column; align-items: center;'>" +
                            "    <label for='subtitleDelayInputEN'>Subtitle Delay (EN) [s]:</label>" +
                            "    <input id='subtitleDelayInputEN' type='number' step='0.5' style='width: 100px; text-align: center;'/>" +
                            "    <label for='subtitleDelayInputPL'>Subtitle Delay (PL) [s]:</label>" +
                            "    <input id='subtitleDelayInputPL' type='number' step='0.5' style='width: 100px; text-align: center;'/>" +
                            "  </div>" +
                            "</div>"
            );

            add(videoContainer);

            double currentVideoTime = watchDetails.getCurrentVideoTime();

            getElement().executeJs(
                    "let videoPlayer = document.getElementById('videoPlayer');" +
                            " if (videoPlayer) {" +
                            "    videoPlayer.currentTime = $2;" +
                            " }" +
                            "document.getElementById('subtitleDelayInputEN').value = $3;" +
                            "document.getElementById('subtitleDelayInputPL').value = $4;" +

                            " document.addEventListener('keydown', function(event) {" +
                            "    if (event.key === 'b') {" +
                            toggleSubtitles() +
                            "    } else if (event.key === ' ' || event.key === 'Spacebar') {" +
                            toggleStartStop() +
                            "    } else if (event.key === 'ArrowRight') {" +
                            plusTimeVideo() +
                            "    } else if (event.key === 'ArrowLeft') {" +
                            minusTimeVideo() +
                            "    } else if (event.key === 'f') {" +
                            toggleFullscreen() +
                            "    } else {return;}" +
                            " }); " +

                            " if (videoPlayer) {" +
                            "    let lastSentTime = 0;" +
                            "    setInterval(() => {" +
                            "        if (!isNaN(videoPlayer.currentTime) && Math.abs(videoPlayer.currentTime - lastSentTime) >= 5) {" +
                            "            lastSentTime = videoPlayer.currentTime;" +
                            "            $0.$server.saveWatchProgress($1, videoPlayer.currentTime);" +
                            "        }" +
                            "    }, 10000);" +
                            " } " +

                            " let enDelay = $3; " +
                            " let plDelay = $4; " +

                            " function adjustSubtitleTiming(track, delay) {" +
                            "    if (!track || !track.cues) return;" +
                            "    for (let i = 0; i < track.cues.length; i++) {" +
                            "        let cue = track.cues[i]; " +
                            "        cue.startTime += delay; " +
                            "        cue.endTime += delay; " +
                            "    }" +
                            " } " +

                            " document.getElementById('subtitleDelayInputEN').addEventListener('input', function(event) {" +
                            "    const textTracks = videoPlayer.textTracks;" +
                            "    const newEn = parseFloat(event.target.value) || 0;" +
                            "    let diffEn = newEn - enDelay;" +
                            "    enDelay = newEn;" +
                            "    for (let i = 0; i < textTracks.length; i++) {" +
                            "        if (textTracks[i].language === 'en' || textTracks[i].label.toLowerCase() === 'english') {" +
                            "            adjustSubtitleTiming(textTracks[i], diffEn);" +
                            "        }" +
                            "    }" +
                            "    $0.$server.saveActualDelay($1, enDelay, plDelay);" +
                            " }); "
                            +
                            " document.getElementById('subtitleDelayInputPL').addEventListener('input', function(event) {" +
                            "    const textTracks = videoPlayer.textTracks;" +
                            "    const newPl = parseFloat(event.target.value) || 0;" +
                            "    let diffPl = newPl - plDelay;" +
                            "    plDelay = newPl;" +
                            "    for (let i = 0; i < textTracks.length; i++) {" +
                            "        if (textTracks[i].language === 'pl' || textTracks[i].label.toLowerCase() === 'polish') {" +
                            "            adjustSubtitleTiming(textTracks[i], diffPl);" +
                            "        }" +
                            "    }" +
                            "    $0.$server.saveActualDelay($1, enDelay, plDelay);" +
                            " }); "
                            +
                            "    window.setTimeout(() => {" +
                            "     if (videoPlayer) {" +
                            "        const textTracks = videoPlayer.textTracks; " +
                            "        for (let i = 0; i < textTracks.length; i++) { " +
                            "           if (textTracks[i].language === 'pl' || textTracks[i].label.toLowerCase() === 'polish') {" +
                            "               adjustSubtitleTiming(textTracks[i], plDelay);" +
                            "           } else if (textTracks[i].language === 'en' || textTracks[i].label.toLowerCase() === 'english') { " +
                            "               adjustSubtitleTiming(textTracks[i], enDelay);" +
                            "           }" +
                            "        }" +
                            "     } else {" +
                            "        console.error('Video player element not found.');" +
                            "     }" +
                            "    }, 10000);"
                    ,
                    getElement(),     // $0
                    videoId,          // $1
                    currentVideoTime, // $2
                    enDelay,          // $3
                    plDelay           // $4
            );
        } catch (Exception e) {
            logger.error("Could not load video!", e);
            showErrorNotification("Could not load video!");
        }
    }


    private String toggleFullscreen() {
        return " "
                + "if (!videoPlayer) return; "
                + "if(document.fullscreenElement === videoPlayer) {"
                + "  document.exitFullscreen(); "
                + "} else {"
                + "  if (videoPlayer.requestFullscreen) { "
                + "      videoPlayer.requestFullscreen(); "
                + "  } else if (videoPlayer.webkitRequestFullscreen) { /* Safari */ "
                + "      videoPlayer.webkitRequestFullscreen(); "
                + "  } else if ( videoPlayer.msRequestFullscreen) { /* IE11 */ "
                + "      videoPlayer.msRequestFullscreen(); "
                + "  }"
                + "}";
    }

    private String plusTimeVideo() {
        return " "
                + "if (!videoPlayer) return; "
                + "videoPlayer.currentTime += 5;";
    }

    private String minusTimeVideo() {
        return " "
                + "if (!videoPlayer) return; "
                + "videoPlayer.currentTime -= 5;";
    }

    private String toggleStartStop() {
        return " "
                + "if (videoPlayer.paused) {"
                + "  videoPlayer.play();"
                + "} else {"
                + "  videoPlayer.pause();"
                + "}";
    }

    private String toggleSubtitles() {
        return " "
                + "if (videoPlayer.textTracks.length < 2) return;"
                + "if (videoPlayer.textTracks[0].mode === 'hidden') {"
                + "  videoPlayer.textTracks[0].mode = 'showing'; "
                + "  videoPlayer.textTracks[1].mode = 'hidden'; "
                + "} else {"
                + "  videoPlayer.textTracks[0].mode = 'hidden';"
                + "  videoPlayer.textTracks[1].mode = 'showing';"
                + "}";
    }

    @ClientCallable
    public void saveWatchProgress(String videoId, double lastWatchedTime) {
        WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(
                AuthService.getLoggedUserId().toString(), videoId
        );
        videoManager.saveWatchProgress(watchDetails, lastWatchedTime);
    }

    @ClientCallable
    public void saveActualDelay(String videoId, double enDelay, double plDelay) {
        WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(
                AuthService.getLoggedUserId().toString(), videoId
        );
        videoManager.saveSubtitleDelays(watchDetails, enDelay, plDelay);
    }


    protected void addRemoteControlSupport(boolean isTV) {

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