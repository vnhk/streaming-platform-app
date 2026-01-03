package com.bervan.streamingapp.view.player;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;

public abstract class AbstractVideoPlayer extends Div {
    public abstract void shiftSubtitles(String en, Double delay);

    public abstract void toggleSubtitles();

    public abstract void togglePlayPause();

    public abstract void seek(double seekStepSeconds);

    public abstract void toggleFullscreen();

    public abstract String getPlayerUniqueId();

    protected void addSubtitleTracks(Element video, String currentVideoFolder) {
        boolean first = true;
        String[] langs = new String[]{"en", "pl", "es"};
        for (String lang : langs) {
            String label = mapLangToLabel(lang);
            addSubtitleTrack(video, lang, label, "/storage/videos/subtitles/" + currentVideoFolder + "/" + lang, first);
            first = false;
        }
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
