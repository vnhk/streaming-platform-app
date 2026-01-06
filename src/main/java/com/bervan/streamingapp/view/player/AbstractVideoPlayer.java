package com.bervan.streamingapp.view.player;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;

import java.util.Set;

public abstract class AbstractVideoPlayer extends Div {
    protected final Set<String> availableSubtitles;

    protected AbstractVideoPlayer(Set<String> availableSubtitles) {
        this.availableSubtitles = availableSubtitles;
    }

    public abstract void shiftSubtitles(String en, Double delay);

    public abstract void toggleSubtitles();

    public abstract void togglePlayPause();

    public abstract void seek(double seekStepSeconds);

    public abstract void toggleFullscreen();

    public abstract String getPlayerUniqueId();

    protected void addSubtitleTracks(Element video, String currentVideoFolder) {
        boolean first = true;
        String[] langs = getAvailableSubtitleLangs();
        for (String lang : langs) {
            String label = mapLangToLabel(lang);
            addSubtitleTrack(video, lang, label, "/storage/videos/subtitles/" + currentVideoFolder + "/" + lang, first);
            first = false;
        }
    }

    protected String[] getAvailableSubtitleLangs() {
        return availableSubtitles.toArray(new String[0]);
    }

    protected String mapLangToLabel(String lang) {
        return lang;
    }

    protected void addSubtitleTrack(Element video, String lang, String label, String src, boolean isDefault) {
        Element track = new Element("track");
        track.setAttribute("kind", "subtitles");
        track.setAttribute("src", src);
        track.setAttribute("srclang", lang);
        track.setAttribute("label", label);
        if (isDefault) {
            track.setAttribute("default", "");
        }
        video.appendChild(track);
    }
}
