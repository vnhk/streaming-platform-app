package com.bervan.streamingapp.view.player;

import com.vaadin.flow.component.html.Div;

public abstract class AbstractVideoPlayer extends Div {
    public abstract void shiftSubtitles(String en, Double delay);

    public abstract void toggleSubtitles();

    public abstract void togglePlayPause();

    public abstract void seek(double seekStepSeconds);

    public abstract void toggleFullscreen();

    public abstract String getPlayerUniqueId();
}
