package com.bervan.streamingapp.view.player.transcription;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubtitleCue {
    private int index;
    private double startTime;
    private double endTime;
    private String text;

    public SubtitleCue() {
    }

    public SubtitleCue(int index, double startTime, double endTime, String text) {
        this.index = index;
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text;
    }

    public String getFormattedStartTime() {
        return formatTime(startTime);
    }

    public String getFormattedEndTime() {
        return formatTime(endTime);
    }

    private String formatTime(double seconds) {
        int hours = (int) (seconds / 3600);
        int mins = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, mins, secs);
        }
        return String.format("%d:%02d", mins, secs);
    }
}