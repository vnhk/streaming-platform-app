package com.bervan.streamingapp.view.player;

import com.bervan.streamingapp.config.ProductionData;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.dom.Element;

import java.util.UUID;

/**
 * Component encapsulating the HTML5 video player with HLS support via hls.js
 */
public class HLSVideoPlayerComponent extends AbstractVideoPlayer {
    private final Element videoElement;
    private final String currentVideoFolder;
    private final String playerUniqueId;
    private final ProductionData productionData;
    private final String componentId;

    public HLSVideoPlayerComponent(String componentId, String currentVideoFolder, double startTime, ProductionData productionData) {
        this.componentId = componentId;
        this.currentVideoFolder = currentVideoFolder;
        this.productionData = productionData;
        // Create a unique DOM ID for this specific player instance
        this.playerUniqueId = componentId + "_" + UUID.randomUUID().toString().substring(0, 8);

        addClassName("video-container");
        setSizeFull();

        this.videoElement = buildVideoElement();
        getElement().appendChild(videoElement);

        initializeSubtitleShiftFunction();

        // We set the start time, but application happens after metadata load in JS
        if (startTime > 0) {
            // We store it as a data attribute to access it in JS on load
            videoElement.setAttribute("data-start-time", String.valueOf(startTime));
        }
    }

    private Element buildVideoElement() {
        Element video = new Element("video");
        video.setAttribute("id", playerUniqueId);
        video.setAttribute("controls", "");
        video.setAttribute("playsinline", "");
        // Poster image could be dynamic based on videoId
         video.setAttribute("poster", "/storage/video/poster/" + currentVideoFolder);
        video.setAttribute("style", "width: 100%; height: 100%; object-fit: contain;");

        addSubtitleTracks(video, currentVideoFolder);

        return video;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Initialize HLS when the component is attached to the DOM
        initializeHlsPlayer();
    }

    private void initializeHlsPlayer() {
        // Construct the HLS Manifest URL
        // Using an endpoint approach as recommended
        String hlsUrl = "/storage/videos/hls/" + currentVideoFolder + "/master.m3u8";

        // JavaScript to initialize HLS
        // We check for HLS support (hls.js) or native support (Safari)
        String js = "" +
                "var video = document.getElementById($0);" +
                "var src = $1;" +
                "var startTime = parseFloat(video.getAttribute('data-start-time') || '0');" +

                "if (Hls.isSupported()) {" +
                "   var hls = new Hls();" +
                "   hls.loadSource(src);" +
                "   hls.attachMedia(video);" +
                "   hls.on(Hls.Events.MANIFEST_PARSED, function() {" +
                "       if(startTime > 0) video.currentTime = startTime;" +
                // "       video.play();" + // Auto-play if desired
                "   });" +
                "   window['hls_instance_' + $0] = hls;" + // Store reference to destroy later
                "   " +
                "   hls.on(Hls.Events.ERROR, function (event, data) {" +
                "       if (data.fatal) {" +
                "           switch (data.type) {" +
                "               case Hls.ErrorTypes.NETWORK_ERROR:" +
                "                   console.error('HLS Network error, trying to recover...');" +
                "                   hls.startLoad();" +
                "                   break;" +
                "               case Hls.ErrorTypes.MEDIA_ERROR:" +
                "                   console.error('HLS Media error, trying to recover...');" +
                "                   hls.recoverMediaError();" +
                "                   break;" +
                "               default:" +
                "                   hls.destroy();" +
                "                   break;" +
                "           }" +
                "       }" +
                "   });" +
                "} " +
                // Fallback for native HLS support (Safari)
                "else if (video.canPlayType('application/vnd.apple.mpegurl')) {" +
                "   video.src = src;" +
                "   video.addEventListener('loadedmetadata', function() {" +
                "       if(startTime > 0) video.currentTime = startTime;" +
                // "       video.play();" +
                "   });" +
                "}";

        getElement().executeJs(js, playerUniqueId, hlsUrl);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // Clean up HLS instance to prevent memory leaks
        getElement().executeJs(
                "if (window['hls_instance_' + $0]) {" +
                        "   window['hls_instance_' + $0].destroy();" +
                        "   delete window['hls_instance_' + $0];" +
                        "}", playerUniqueId
        );
        super.onDetach(detachEvent);
    }

    // --- Controls and Utilities ---

    private void initializeSubtitleShiftFunction() {
        // This function needs to be global or attached to window to be called from outside
        // Using the specific video ID to ensure we target correct player
        getElement().executeJs(
                "window.shiftSubtitles_" + playerUniqueId + " = (lang, delay) => {" +
                        "  const video = document.getElementById('" + playerUniqueId + "');" +
                        "  if (!video || !video.textTracks) return;" +
                        "  " +
                        "  for (let track of video.textTracks) {" +
                        "    if (track.language === lang || track.label.toLowerCase() === lang) {" +
                        "      if (track.cues) {" +
                        "        for (let cue of track.cues) {" +
                        "          cue.startTime = Math.max(0, cue.startTime + delay);" +
                        "          cue.endTime = Math.max(0, cue.endTime + delay);" +
                        "        }" +
                        "      }" +
                        "    }" +
                        "  }" +
                        "};"
        );
    }

    public void setCurrentTime(double time) {
        getElement().executeJs(
                "const video = document.getElementById($0);" +
                        "if (video) { video.currentTime = $1; }",
                playerUniqueId, time
        );
    }

    public void toggleSubtitles() {
        // Toggle logic: if any track is showing, hide all. Else show first.
        getElement().executeJs(
                "const video = document.getElementById($0);" +
                        "if (video && video.textTracks.length >= 1) {" +
                        "   let anyShowing = false;" +
                        "   for(let i=0; i < video.textTracks.length; i++) {" +
                        "       if(video.textTracks[i].mode === 'showing') {" +
                        "           anyShowing = true; break;" +
                        "       }" +
                        "   }" +
                        "   if(anyShowing) {" +
                        "       for(let i=0; i < video.textTracks.length; i++) video.textTracks[i].mode = 'hidden';" +
                        "   } else {" +
                        "       video.textTracks[0].mode = 'showing';" +
                        "   }" +
                        "}",
                playerUniqueId
        );
    }

    public void togglePlayPause() {
        getElement().executeJs(
                "const video = document.getElementById($0);" +
                        "if (video) {" +
                        "  if (video.paused) { video.play(); } else { video.pause(); }" +
                        "}",
                playerUniqueId
        );
    }

    public void seek(double seconds) {
        getElement().executeJs(
                "const video = document.getElementById($0);" +
                        "if (video) { video.currentTime += $1; }",
                playerUniqueId, seconds
        );
    }

    public void toggleFullscreen() {
        getElement().executeJs(
                "const video = document.getElementById($0);" +
                        "if (video) {" +
                        "  if (document.fullscreenElement === video) {" +
                        "    document.exitFullscreen();" +
                        "  } else if (video.requestFullscreen) {" +
                        "      video.requestFullscreen();" +
                        "  } else if (video.webkitRequestFullscreen) {" +
                        "      video.webkitRequestFullscreen();" + // Safari
                        "  }" +
                        "}",
                playerUniqueId
        );
    }

    public void shiftSubtitles(String language, Double delay) {
        // Call the specific namespaced function
        getElement().executeJs("if(window.shiftSubtitles_" + playerUniqueId + ") window.shiftSubtitles_" + playerUniqueId + "($0, $1)", language, delay);
    }

    // Getter for the unique DOM ID if needed externally
    public String getPlayerUniqueId() {
        return playerUniqueId;
    }
}