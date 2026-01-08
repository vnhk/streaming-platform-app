package com.bervan.streamingapp.view.player;

import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.view.player.transcription.TranscriptionPanel;
import com.google.gson.Gson;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.dom.Element;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Getter
@Setter
public class HLSVideoPlayerComponent extends AbstractVideoPlayer {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final Element videoElement;
    private final Element controlsOverlay;
    private final String currentVideoFolder;
    private final String playerUniqueId;
    private final ProductionData productionData;
    private final String componentId;

    // Track information
    private final List<TrackInfo> audioTracks = new ArrayList<>();
    private final List<TrackInfo> subtitleTracks = new ArrayList<>();
    private int currentAudioTrackIndex = 0;
    private int currentSubtitleTrackIndex = -1;

    // Callbacks
    private Consumer<List<TrackInfo>> onAudioTracksLoaded;
    private Consumer<List<TrackInfo>> onSubtitleTracksLoaded;
    private Consumer<Integer> onAudioTrackChanged;
    private Consumer<Integer> onSubtitleTrackChanged;

    public HLSVideoPlayerComponent(String componentId, String currentVideoFolder,
                                   double startTime, ProductionData productionData,
                                   Set<String> availableSubtitles) {
        super(availableSubtitles);
        this.componentId = componentId;
        this.currentVideoFolder = currentVideoFolder;
        this.productionData = productionData;
        this.playerUniqueId = componentId + "_" + UUID.randomUUID().toString().substring(0, 8);

        addClassName("video-container");
        setSizeFull();
        getElement().getStyle().set("position", "relative");

        this.videoElement = buildVideoElement();
        this.controlsOverlay = buildControlsOverlay();

        getElement().appendChild(videoElement);
        getElement().appendChild(controlsOverlay);

        injectStyles();
        initializeSubtitleShiftFunction();

        if (startTime > 0) {
            videoElement.setAttribute("data-start-time", String.valueOf(startTime));
        }
    }

    private Element buildVideoElement() {
        Element video = new Element("video");
        video.setAttribute("id", playerUniqueId);
        video.setAttribute("controls", "");
        video.setAttribute("playsinline", "");
        video.setAttribute("poster", "/storage/video/poster/" + currentVideoFolder);
        video.setAttribute("style", "width: 100%; height: 100%; object-fit: contain;");

        addSubtitleTracks(video, currentVideoFolder);

        return video;
    }

    private Element buildControlsOverlay() {
        Element overlay = new Element("div");
        overlay.setAttribute("id", playerUniqueId + "_overlay");
        overlay.setAttribute("class", "hls-controls-overlay");
        return overlay;
    }

    private void injectStyles() {
        String css = """
                <style>
                .hls-controls-overlay {
                    position: absolute;
                    bottom: 60px;
                    right: 12px;
                    z-index: 2147483647;
                }
                .hls-settings-btn {
                    background: rgba(0,0,0,0.7);
                    border: none;
                    border-radius: 4px;
                    padding: 8px 12px;
                    cursor: pointer;
                    color: white;
                    font-size: 20px;
                    transition: background 0.2s;
                }
                .hls-settings-btn:hover {
                    background: rgba(0,0,0,0.9);
                }
                .hls-settings-panel {
                    display: none;
                    position: absolute;
                    bottom: 100%;
                    right: 0;
                    background: rgba(20,20,20,0.95);
                    border-radius: 8px;
                    padding: 0;
                    min-width: 280px;
                    margin-bottom: 8px;
                    box-shadow: 0 4px 20px rgba(0,0,0,0.5);
                    max-height: 400px;
                    overflow-y: auto;
                }
                .hls-settings-panel.open {
                    display: block;
                }
                .hls-panel-section {
                    border-bottom: 1px solid rgba(255,255,255,0.1);
                }
                .hls-panel-section:last-child {
                    border-bottom: none;
                }
                .hls-panel-header {
                    padding: 12px 16px;
                    font-weight: 600;
                    color: #fff;
                    font-size: 14px;
                    background: rgba(255,255,255,0.05);
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .hls-panel-header svg {
                    width: 18px;
                    height: 18px;
                }
                .hls-track-list {
                    list-style: none;
                    margin: 0;
                    padding: 8px 0;
                }
                .hls-track-item {
                    padding: 10px 16px;
                    cursor: pointer;
                    color: #ccc;
                    font-size: 13px;
                    transition: all 0.15s;
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .hls-track-item:hover {
                    background: rgba(255,255,255,0.1);
                    color: #fff;
                }
                .hls-track-item.active {
                    color: #3ea6ff;
                    background: rgba(62,166,255,0.1);
                }
                .hls-track-item.active::before {
                    content: '✓';
                    font-weight: bold;
                }
                .hls-track-item:not(.active)::before {
                    content: '';
                    width: 14px;
                    display: inline-block;
                }
                .hls-no-tracks {
                    padding: 12px 16px;
                    color: #888;
                    font-size: 13px;
                    font-style: italic;
                }
                </style>
                """;

        String injectCssJs = """
                (function() {
                    if (!document.getElementById('hls-player-styles')) {
                        var styleEl = document.createElement('div');
                        styleEl.id = 'hls-player-styles';
                        styleEl.innerHTML = $0;
                        document.head.appendChild(styleEl.firstElementChild);
                    }
                })();
                """;

        getElement().executeJs(injectCssJs, css);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        initializeHlsPlayer();
        initializeControlsOverlay();
    }

    private void initializeHlsPlayer() {
        String hlsUrl = "/storage/videos/hls/" + currentVideoFolder + "/master.m3u8";

        String js = """
                (function() {
                    var video = document.getElementById($0);
                    var src = $1;
                    var startTime = parseFloat(video.getAttribute('data-start-time') || '0');
                    var componentEl = $2;
                    var playerId = $0;
                    var tracksNotifiedSuccessfully = false;
                
                    function notifyTracksLoaded(hls, source) {
                        console.log('[HLS] ===== notifyTracksLoaded from: ' + source + ' =====');
                        console.log('[HLS] hls object:', hls);
                        console.log('[HLS] hls.audioTracks:', hls.audioTracks);
                        console.log('[HLS] hls.audioTracks length:', hls.audioTracks ? hls.audioTracks.length : 'null/undefined');
                        console.log('[HLS] hls.subtitleTracks:', hls.subtitleTracks);
                        console.log('[HLS] hls.levels:', hls.levels);
                        var audioTracks = [];
                        var subtitleTracks = [];
                        // Try to get audio tracks
                        if (hls.audioTracks && hls.audioTracks.length > 0) {
                            console.log('[HLS] Found ' + hls.audioTracks.length + ' audio tracks!');
                            for (var i = 0; i < hls.audioTracks.length; i++) {
                                var track = hls.audioTracks[i];
                                console.log('[HLS] Audio track ' + i + ':', JSON.stringify(track));
                                audioTracks.push({
                                    id: track.id !== undefined ? track.id : i,
                                    index: i,
                                    name: track.name || track.lang || ('Audio ' + (i + 1)),
                                    lang: track.lang || 'und',
                                    type: 'audio'
                                });
                            }
                            tracksNotifiedSuccessfully = true;
                        } else {
                            console.log('[HLS] No audio tracks found yet');
                        }
                
                        // Try to get subtitle tracks
                        if (hls.subtitleTracks && hls.subtitleTracks.length > 0) {
                            for (var i = 0; i < hls.subtitleTracks.length; i++) {
                                var track = hls.subtitleTracks[i];
                                subtitleTracks.push({
                                    id: track.id !== undefined ? track.id : i,
                                    index: i,
                                    name: track.name || track.lang || ('Subtitle ' + (i + 1)),
                                    lang: track.lang || 'und',
                                    type: 'subtitle'
                                });
                            }
                        }
                
                        // Check HTML5 textTracks
                        if (video.textTracks && video.textTracks.length > 0) {
                            var existingLangs = subtitleTracks.map(function(t) { return t.lang; });
                            for (var i = 0; i < video.textTracks.length; i++) {
                                var track = video.textTracks[i];
                                if ((track.kind === 'subtitles' || track.kind === 'captions') 
                                    && existingLangs.indexOf(track.language) === -1) {
                                    subtitleTracks.push({
                                        id: 1000 + i,
                                        index: 1000 + i,
                                        name: track.label || ('Subtitle ' + (i + 1)),
                                        lang: track.language || 'und',
                                        type: 'subtitle',
                                        isTextTrack: true
                                    });
                                }
                            }
                        }
                
                        console.log('[HLS] Final audioTracks:', JSON.stringify(audioTracks));
                        console.log('[HLS] Final subtitleTracks:', JSON.stringify(subtitleTracks));
                
                        window['hls_audio_tracks_' + playerId] = audioTracks;
                        window['hls_subtitle_tracks_' + playerId] = subtitleTracks;
                        window['hls_current_audio_' + playerId] = hls.audioTrack >= 0 ? hls.audioTrack : 0;
                        window['hls_current_subtitle_' + playerId] = hls.subtitleTrack >= 0 ? hls.subtitleTrack : -1;
                
                        try {
                            componentEl.$server.onAudioTracksLoaded(JSON.stringify(audioTracks));
                            componentEl.$server.onSubtitleTracksLoaded(JSON.stringify(subtitleTracks));
                        } catch(e) {
                            console.error('[HLS] Error calling server:', e);
                        }
                
                        if (typeof window.updateTrackUI === 'function') {
                            window.updateTrackUI(playerId);
                        }
                
                        return audioTracks.length > 0;
                    }
                
                    // Polling function to check for tracks
                    function pollForTracks(hls, maxAttempts, attempt) {
                        attempt = attempt || 1;
                        console.log('[HLS] Polling for tracks, attempt ' + attempt + '/' + maxAttempts);
                
                        if (tracksNotifiedSuccessfully) {
                            console.log('[HLS] Tracks already loaded, stopping poll');
                            return;
                        }
                
                        if (hls.audioTracks && hls.audioTracks.length > 0) {
                            console.log('[HLS] Poll found tracks!');
                            notifyTracksLoaded(hls, 'POLL attempt ' + attempt);
                            return;
                        }
                
                        if (attempt < maxAttempts) {
                            setTimeout(function() {
                                pollForTracks(hls, maxAttempts, attempt + 1);
                            }, 500);
                        } else {
                            console.log('[HLS] Max polling attempts reached, no audio tracks found');
                            // Still update UI to show "No audio tracks"
                            notifyTracksLoaded(hls, 'POLL final (no tracks)');
                        }
                    }
                
                    if (typeof Hls !== 'undefined' && Hls.isSupported()) {
                        console.log('[HLS] Using HLS.js version:', Hls.version);
                
                        var hls = new Hls({
                            enableWorker: true,
                            lowLatencyMode: false,
                            debug: false
                        });
                
                        // Store reference immediately
                        window['hls_instance_' + playerId] = hls;
                
                        hls.loadSource(src);
                        hls.attachMedia(video);
                
                        // Log all events for debugging
                        var importantEvents = [
                            'MANIFEST_LOADING',
                            'MANIFEST_LOADED', 
                            'MANIFEST_PARSED',
                            'AUDIO_TRACKS_UPDATED',
                            'AUDIO_TRACK_LOADED',
                            'AUDIO_TRACK_SWITCHING',
                            'AUDIO_TRACK_SWITCHED',
                            'SUBTITLE_TRACKS_UPDATED',
                            'LEVEL_LOADED'
                        ];
                
                        importantEvents.forEach(function(eventName) {
                            if (Hls.Events[eventName]) {
                                hls.on(Hls.Events[eventName], function(event, data) {
                                    console.log('[HLS Event] ' + eventName, data);
                
                                    // Check audioTracks on every important event
                                    if (hls.audioTracks && hls.audioTracks.length > 0 && !tracksNotifiedSuccessfully) {
                                        console.log('[HLS] Found tracks during ' + eventName);
                                        notifyTracksLoaded(hls, eventName);
                                    }
                                });
                            }
                        });
                
                        hls.on(Hls.Events.MANIFEST_PARSED, function(event, data) {
                            console.log('[HLS] MANIFEST_PARSED');
                            console.log('[HLS] data.levels:', data.levels);
                            console.log('[HLS] data.audioTracks:', data.audioTracks);
                            console.log('[HLS] hls.audioTracks at MANIFEST_PARSED:', hls.audioTracks);
                
                            if (startTime > 0) {
                                video.currentTime = startTime;
                            }
                
                            // Try immediately
                            if (hls.audioTracks && hls.audioTracks.length > 0) {
                                notifyTracksLoaded(hls, 'MANIFEST_PARSED immediate');
                            } else {
                                // Start polling
                                console.log('[HLS] No tracks yet, starting polling...');
                                pollForTracks(hls, 10, 1);
                            }
                        });
                
                        hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, function(event, data) {
                            console.log('[HLS] AUDIO_TRACKS_UPDATED:', data);
                            if (!tracksNotifiedSuccessfully) {
                                notifyTracksLoaded(hls, 'AUDIO_TRACKS_UPDATED');
                            }
                        });
                
                        hls.on(Hls.Events.AUDIO_TRACK_SWITCHED, function(event, data) {
                            console.log('[HLS] AUDIO_TRACK_SWITCHED to:', data.id);
                            window['hls_current_audio_' + playerId] = data.id;
                            try {
                                componentEl.$server.onAudioTrackChanged(data.id);
                            } catch(e) {}
                            if (typeof window.updateTrackUI === 'function') {
                                window.updateTrackUI(playerId);
                            }
                        });
                
                        hls.on(Hls.Events.SUBTITLE_TRACK_SWITCH, function(event, data) {
                            console.log('[HLS] SUBTITLE_TRACK_SWITCH to:', data.id);
                            window['hls_current_subtitle_' + playerId] = data.id;
                            try {
                                componentEl.$server.onSubtitleTrackChanged(data.id);
                            } catch(e) {}
                            if (typeof window.updateTrackUI === 'function') {
                                window.updateTrackUI(playerId);
                            }
                        });
                
                        hls.on(Hls.Events.ERROR, function(event, data) {
                            console.error('[HLS] Error:', data.type, data.details, data);
                            if (data.fatal) {
                                switch (data.type) {
                                    case Hls.ErrorTypes.NETWORK_ERROR:
                                        hls.startLoad();
                                        break;
                                    case Hls.ErrorTypes.MEDIA_ERROR:
                                        hls.recoverMediaError();
                                        break;
                                    default:
                                        hls.destroy();
                                        break;
                                }
                            }
                        });
                
                    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                        console.log('[HLS] Using native HLS (Safari)');
                        video.src = src;
                        video.addEventListener('loadedmetadata', function() {
                            if (startTime > 0) {
                                video.currentTime = startTime;
                            }
                            if (typeof window.handleNativeHLSTracks === 'function') {
                                window.handleNativeHLSTracks(video, componentEl, playerId);
                            }
                        });
                    } else {
                        console.error('[HLS] HLS not supported!');
                    }
                })();
                """;

        getElement().executeJs(js, playerUniqueId, hlsUrl, getElement());
    }

    private void initializeControlsOverlay() {
        String overlayHtml = """
                <button class="hls-settings-btn" id="%s_settings_btn" title="Settings">⚙</button>
                <div class="hls-settings-panel" id="%s_settings_panel">
                    <div class="hls-panel-section">
                        <div class="hls-panel-header">
                            <svg viewBox="0 0 24 24" fill="currentColor">
                                <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/>
                            </svg>
                            Audio
                        </div>
                        <ul class="hls-track-list" id="%s_audio_list">
                            <li class="hls-no-tracks">Loading...</li>
                        </ul>
                    </div>
                    <div class="hls-panel-section">
                        <div class="hls-panel-header">
                            <svg viewBox="0 0 24 24" fill="currentColor">
                                <path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V6h16v12zM6 10h2v2H6v-2zm0 4h8v2H6v-2zm10 0h2v2h-2v-2zm-6-4h8v2h-8v-2z"/>
                            </svg>
                            Subtitles
                        </div>
                        <ul class="hls-track-list" id="%s_subtitle_list">
                            <li class="hls-no-tracks">Loading...</li>
                        </ul>
                    </div>
                </div>
                """.formatted(playerUniqueId, playerUniqueId, playerUniqueId, playerUniqueId);

        String overlayJs = """
                (function() {
                    var playerId = $0;
                    var overlay = document.getElementById(playerId + '_overlay');
                    if (!overlay) return;
                
                    overlay.innerHTML = $1;
                
                    var settingsBtn = document.getElementById(playerId + '_settings_btn');
                    var settingsPanel = document.getElementById(playerId + '_settings_panel');
                
                    if (settingsBtn && settingsPanel) {
                        settingsBtn.addEventListener('click', function(e) {
                            e.stopPropagation();
                            settingsPanel.classList.toggle('open');
                        });
                
                        document.addEventListener('click', function(e) {
                            if (!overlay.contains(e.target)) {
                                settingsPanel.classList.remove('open');
                            }
                        });
                    }
                
                    window.updateTrackUI = function(pid) {
                        var audioTracks = window['hls_audio_tracks_' + pid] || [];
                        var subtitleTracks = window['hls_subtitle_tracks_' + pid] || [];
                        var currentAudio = window['hls_current_audio_' + pid];
                
                        var currentSubtitle = window['hls_current_subtitle_' + pid];
                
                        if (currentSubtitle === -1 || currentSubtitle === undefined) {
                            var video = document.getElementById(pid);
                            if (video && video.textTracks) {
                                for (var k = 0; k < video.textTracks.length; k++) {
                                    if (video.textTracks[k].mode === 'showing') {
                                        var assumedId = 1000 + k;
                
                                        var found = false;
                                        for(var t = 0; t < subtitleTracks.length; t++) {
                                            if (subtitleTracks[t].index === assumedId) {
                                                currentSubtitle = assumedId;
                                                found = true;
                                                break;
                                            }
                                        }
                                        if (found) break;
                                    }
                                }
                            }
                        }
                        // ---------------------------------------------------------
                
                        var audioList = document.getElementById(pid + '_audio_list');
                        var subtitleList = document.getElementById(pid + '_subtitle_list');
                
                        if (audioList) {
                            if (audioTracks.length === 0) {
                                audioList.innerHTML = '<li class="hls-no-tracks">No audio tracks available</li>';
                            } else {
                                var audioHtml = '';
                                for (var i = 0; i < audioTracks.length; i++) {
                                    var track = audioTracks[i];
                                    var isActive = track.index === currentAudio;
                                    var langStr = track.lang !== 'und' ? ' (' + track.lang + ')' : '';
                                    audioHtml += '<li class="hls-track-item' + (isActive ? ' active' : '') + '" ';
                                    audioHtml += 'data-track-type="audio" data-track-index="' + track.index + '">';
                                    audioHtml += track.name + langStr + '</li>';
                                }
                                audioList.innerHTML = audioHtml;
                            }
                        }
                
                        if (subtitleList) {
                            var isOff = (currentSubtitle === -1 || currentSubtitle === undefined);
                
                            var subtitleHtml = '<li class="hls-track-item' + (isOff ? ' active' : '') + '" ';
                            subtitleHtml += 'data-track-type="subtitle" data-track-index="-1">Off</li>';
                
                            for (var i = 0; i < subtitleTracks.length; i++) {
                                var track = subtitleTracks[i];
                                var isActive = track.index === currentSubtitle;
                                var langStr = track.lang !== 'und' ? ' (' + track.lang + ')' : '';
                                subtitleHtml += '<li class="hls-track-item' + (isActive ? ' active' : '') + '" ';
                                subtitleHtml += 'data-track-type="subtitle" data-track-index="' + track.index + '">';
                                subtitleHtml += track.name + langStr + '</li>';
                            }
                            subtitleList.innerHTML = subtitleHtml;
                        }
                
                        var allItems = document.querySelectorAll('#' + pid + '_settings_panel .hls-track-item');
                        for (var i = 0; i < allItems.length; i++) {
                            (function(item) {
                                item.onclick = function() {
                                    var type = this.getAttribute('data-track-type');
                                    var index = parseInt(this.getAttribute('data-track-index'));
                                    var hls = window['hls_instance_' + pid];
                                    var video = document.getElementById(pid);
                
                                    if (type === 'audio') {
                                        if (hls) hls.audioTrack = index;
                                    } else if (type === 'subtitle') {
                                        window['hls_current_subtitle_' + pid] = index;
                
                                        if (index >= 1000) {
                                            if (hls) hls.subtitleTrack = -1;
                
                                            var nativeIndex = index - 1000;
                                            if (video && video.textTracks) {
                                                for (var j = 0; j < video.textTracks.length; j++) {
                                                    video.textTracks[j].mode = (j === nativeIndex) ? 'showing' : 'hidden';
                                                }
                                            }
                                        } else {
                                            if (hls) hls.subtitleTrack = index;
                
                                            if (index === -1 && video && video.textTracks) {
                                                for (var j = 0; j < video.textTracks.length; j++) {
                                                    video.textTracks[j].mode = 'hidden';
                                                }
                                            }
                                        }
                                    }
                
                                    window.updateTrackUI(pid);
                                };
                            })(allItems[i]);
                        }
                    };
                
                    window.handleNativeHLSTracks = function(video, componentEl, pid) {
                        var audioTracks = [];
                        var subtitleTracks = [];
                
                        if (video.audioTracks) {
                            for (var i = 0; i < video.audioTracks.length; i++) {
                                var track = video.audioTracks[i];
                                audioTracks.push({
                                    id: track.id || i,
                                    index: i,
                                    name: track.label || ('Audio ' + (i + 1)),
                                    lang: track.language || 'und',
                                    type: 'audio'
                                });
                            }
                        }
                
                        if (video.textTracks) {
                            for (var i = 0; i < video.textTracks.length; i++) {
                                var track = video.textTracks[i];
                                if (track.kind === 'subtitles' || track.kind === 'captions') {
                                    subtitleTracks.push({
                                        id: 1000 + i,
                                        index: 1000 + i,
                                        name: track.label || ('Subtitle ' + (i + 1)),
                                        lang: track.language || 'und',
                                        type: 'subtitle'
                                    });
                                }
                            }
                        }
                
                        window['hls_audio_tracks_' + pid] = audioTracks;
                        window['hls_subtitle_tracks_' + pid] = subtitleTracks;
                        window['hls_current_audio_' + pid] = 0;
                        window['hls_current_subtitle_' + pid] = -1;
                
                        componentEl.$server.onAudioTracksLoaded(JSON.stringify(audioTracks));
                        componentEl.$server.onSubtitleTracksLoaded(JSON.stringify(subtitleTracks));
                
                        window.updateTrackUI(pid);
                    };
                })();
                """;

        getElement().executeJs(overlayJs, playerUniqueId, overlayHtml);
    }

    // ========== Client Callable Methods ==========

    @ClientCallable
    public void onAudioTracksLoaded(String tracksJson) {
        audioTracks.clear();
        try {
            Gson gson = new Gson();
            TrackInfo[] tracks = gson.fromJson(tracksJson, TrackInfo[].class);
            for (TrackInfo track : tracks) {
                audioTracks.add(track);
            }
            if (onAudioTracksLoaded != null) {
                onAudioTracksLoaded.accept(new ArrayList<>(audioTracks));
            }
        } catch (Exception e) {
            log.error("Error parsing audio tracks: " + e.getMessage());
        }
    }

    @ClientCallable
    public void onSubtitleTracksLoaded(String tracksJson) {
        subtitleTracks.clear();
        try {
            Gson gson = new Gson();
            TrackInfo[] tracks = gson.fromJson(tracksJson, TrackInfo[].class);
            for (TrackInfo track : tracks) {
                subtitleTracks.add(track);
            }
            if (onSubtitleTracksLoaded != null) {
                onSubtitleTracksLoaded.accept(new ArrayList<>(subtitleTracks));
            }
        } catch (Exception e) {
            log.error("Error parsing subtitle tracks: " + e.getMessage());
        }
    }

    @ClientCallable
    public void onAudioTrackChanged(int trackIndex) {
        this.currentAudioTrackIndex = trackIndex;
        if (onAudioTrackChanged != null) {
            onAudioTrackChanged.accept(trackIndex);
        }
    }

    @ClientCallable
    public void onSubtitleTrackChanged(int trackIndex) {
        this.currentSubtitleTrackIndex = trackIndex;
        if (onSubtitleTrackChanged != null) {
            onSubtitleTrackChanged.accept(trackIndex);
        }
    }

    // ========== Public API Methods ==========

    public void setAudioTrack(int index) {
        String js = """
                (function() {
                    var hls = window['hls_instance_' + $0];
                    if (hls && hls.audioTracks && hls.audioTracks.length > $1) {
                        hls.audioTrack = $1;
                    }
                })();
                """;
        getElement().executeJs(js, playerUniqueId, index);
    }

    public void setSubtitleTrack(int index) {
        String js = """
                (function() {
                    var hls = window['hls_instance_' + $0];
                    if (hls) {
                        hls.subtitleTrack = $1;
                    }
                    var video = document.getElementById($0);
                    if (video && video.textTracks) {
                        for (var i = 0; i < video.textTracks.length; i++) {
                            video.textTracks[i].mode = (i === $1) ? 'showing' : 'hidden';
                        }
                    }
                })();
                """;
        getElement().executeJs(js, playerUniqueId, index);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        String js = """
                (function() {
                    var playerId = $0;
                    if (window['hls_instance_' + playerId]) {
                        window['hls_instance_' + playerId].destroy();
                        delete window['hls_instance_' + playerId];
                    }
                    delete window['hls_audio_tracks_' + playerId];
                    delete window['hls_subtitle_tracks_' + playerId];
                    delete window['hls_current_audio_' + playerId];
                    delete window['hls_current_subtitle_' + playerId];
                })();
                """;
        getElement().executeJs(js, playerUniqueId);
        super.onDetach(detachEvent);
    }

    private void initializeSubtitleShiftFunction() {
        String js = """
                window['shiftSubtitles_%s'] = function(lang, delay) {
                    var video = document.getElementById('%s');
                    if (!video || !video.textTracks) return;
                    for (var i = 0; i < video.textTracks.length; i++) {
                        var track = video.textTracks[i];
                        if (track.language === lang || track.label.toLowerCase() === lang) {
                            if (track.cues) {
                                for (var j = 0; j < track.cues.length; j++) {
                                    var cue = track.cues[j];
                                    cue.startTime = Math.max(0, cue.startTime + delay);
                                    cue.endTime = Math.max(0, cue.endTime + delay);
                                }
                            }
                        }
                    }
                };
                """.formatted(playerUniqueId, playerUniqueId);
        getElement().executeJs(js);
    }

    public void setCurrentTime(double time) {
        String js = """
                (function() {
                    var video = document.getElementById($0);
                    if (video) {
                        video.currentTime = $1;
                    }
                })();
                """;
        getElement().executeJs(js, playerUniqueId, time);
    }

    public void toggleSubtitles() {
        String js = """
                (function() {
                    var video = document.getElementById($0);
                    if (video && video.textTracks.length >= 1) {
                        var anyShowing = false;
                        for (var i = 0; i < video.textTracks.length; i++) {
                            if (video.textTracks[i].mode === 'showing') {
                                anyShowing = true;
                                break;
                            }
                        }
                        if (anyShowing) {
                            for (var i = 0; i < video.textTracks.length; i++) {
                                video.textTracks[i].mode = 'hidden';
                            }
                        } else {
                            video.textTracks[0].mode = 'showing';
                        }
                    }
                })();
                """;
        getElement().executeJs(js, playerUniqueId);
    }

    public void togglePlayPause() {
        String js = """
                (function() {
                    var video = document.getElementById($0);
                    if (video) {
                        if (video.paused) {
                            video.play();
                        } else {
                            video.pause();
                        }
                    }
                })();
                """;
        getElement().executeJs(js, playerUniqueId);
    }

    public void seek(double seconds) {
        String js = """
                (function() {
                    var video = document.getElementById($0);
                    if (video) {
                        video.currentTime += $1;
                    }
                })();
                """;
        getElement().executeJs(js, playerUniqueId, seconds);
    }

    public void toggleFullscreen() {
        String js = """
                (function() {
                    var video = document.getElementById($0);
                    if (video) {
                        if (document.fullscreenElement === video) {
                            document.exitFullscreen();
                        } else if (video.requestFullscreen) {
                            video.requestFullscreen();
                        } else if (video.webkitRequestFullscreen) {
                            video.webkitRequestFullscreen();
                        }
                    }
                })();
                """;
        getElement().executeJs(js, playerUniqueId);
    }

    public void shiftSubtitles(String language, Double delay) {
        String js = """
                (function() {
                    var fn = window['shiftSubtitles_%s'];
                    if (fn) {
                        fn($0, $1);
                    }
                })();
                """.formatted(playerUniqueId);
        getElement().executeJs(js, language, delay);
    }

    // ========== Track Info Class ==========

    @Setter
    @Getter
    public static final class TrackInfo {
        private int id;
        private int index;
        private String name;
        private String lang;
        private String type;

        public TrackInfo() {
        }

        public TrackInfo(int id, int index, String name, String lang, String type) {
            this.id = id;
            this.index = index;
            this.name = name;
            this.lang = lang;
            this.type = type;
        }

        @Override
        public String toString() {
            return name + (!"und".equals(lang) ? " (" + lang + ")" : "");
        }
    }
}