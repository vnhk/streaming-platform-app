package com.bervan.streamingapp.view.player;

import com.bervan.streamingapp.config.ProductionData;
import com.vaadin.flow.dom.Element;

import java.util.Set;
import java.util.UUID;

/**
 * Component encapsulating the HTML5 video player with subtitle support
 */
public class MP4VideoPlayerComponent extends AbstractVideoPlayer {
    private final Element videoElement;
    private final String videoId;
    private final String VIDEO_PLAYER_ID;
    private final String playerUniqueId;

    public MP4VideoPlayerComponent(String VIDEO_PLAYER_ID, String videoId, double startTime, ProductionData productionData, Set<String> availableSubtitles) {
        super(availableSubtitles);
        this.videoId = videoId;
        this.VIDEO_PLAYER_ID = VIDEO_PLAYER_ID;
        this.playerUniqueId = VIDEO_PLAYER_ID + "_" + UUID.randomUUID().toString().substring(0, 8);
        this.videoElement = buildVideoElement();

        addClassName("video-container");
        getElement().appendChild(videoElement);

        initializeSubtitleShiftFunction();
        setCurrentTime(startTime);
    }

    @Override
    public String getPlayerUniqueId() {
        return playerUniqueId;
    }

    private Element buildVideoElement() {
        Element video = new Element("video");
        video.setAttribute("id", VIDEO_PLAYER_ID);
        video.setAttribute("controls", "");
        video.setAttribute("playsinline", "");
        video.setAttribute("preload", "auto");
        video.setAttribute("width", "640");
        video.setAttribute("height", "360");

        addVideoSource(video);
        addSubtitleTrack(video, "en", "English", "/storage/videos/subtitles/" + videoId + "/en", true);
        addSubtitleTrack(video, "pl", "Polish", "/storage/videos/subtitles/" + videoId + "/pl", false);

        return video;
    }

    private void addVideoSource(Element video) {
        Element source = new Element("source");
        source.setAttribute("src", "/storage/videos/video-folder/" + videoId);
        source.setAttribute("type", "video/mp4");
        video.appendChild(source);
    }

    private void initializeSubtitleShiftFunction() {
        getElement().executeJs(
                "window.shiftSubtitles = (lang, delay) => {" +
                        "  const video = document.getElementById('" + VIDEO_PLAYER_ID + "');" +
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
                "const video = document.getElementById('" + VIDEO_PLAYER_ID + "');" +
                        "if (video) { video.currentTime = $0; }",
                time
        );
    }

    public void toggleSubtitles() {
        getElement().executeJs(
                "const video = document.getElementById('" + VIDEO_PLAYER_ID + "');" +
                        "if (video && video.textTracks.length >= 2) {" +
                        "  if (video.textTracks[0].mode === 'hidden') {" +
                        "    video.textTracks[0].mode = 'showing';" +
                        "    video.textTracks[1].mode = 'hidden';" +
                        "  } else {" +
                        "    video.textTracks[0].mode = 'hidden';" +
                        "    video.textTracks[1].mode = 'showing';" +
                        "  }" +
                        "}"
        );
    }

    public void togglePlayPause() {
        getElement().executeJs(
                "const video = document.getElementById('" + VIDEO_PLAYER_ID + "');" +
                        "if (video) {" +
                        "  if (video.paused) {" +
                        "    video.play();" +
                        "  } else {" +
                        "    video.pause();" +
                        "  }" +
                        "}"
        );
    }

    public void seek(double seconds) {
        getElement().executeJs(
                "const video = document.getElementById('" + VIDEO_PLAYER_ID + "');" +
                        "if (video) { video.currentTime += $0; }",
                seconds
        );
    }

    public void toggleFullscreen() {
        getElement().executeJs(
                "const video = document.getElementById('" + VIDEO_PLAYER_ID + "');" +
                        "if (video) {" +
                        "  if (document.fullscreenElement === video) {" +
                        "    document.exitFullscreen();" +
                        "  } else {" +
                        "    if (video.requestFullscreen) {" +
                        "      video.requestFullscreen();" +
                        "    } else if (video.webkitRequestFullscreen) {" +
                        "      video.webkitRequestFullscreen();" +
                        "    } else if (video.msRequestFullscreen) {" +
                        "      video.msRequestFullscreen();" +
                        "    }" +
                        "  }" +
                        "}"
        );
    }

    @Override
    public void shiftSubtitles(String language, Double delay) {
        getElement().executeJs("window.shiftSubtitles($0, $1)", language, delay);
    }
}