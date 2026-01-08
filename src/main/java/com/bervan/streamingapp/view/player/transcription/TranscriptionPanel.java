package com.bervan.streamingapp.view.player.transcription;

import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.view.player.HLSVideoPlayerComponent;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.function.Consumer;

@Getter
@Setter
public class TranscriptionPanel extends VerticalLayout {
    private final String videoPlayerId;
    private final String panelId;
    private final String videoFolderId;
    // UI Components
    private final HorizontalLayout controlsLayout;
    private final RadioButtonGroup<String> columnModeSelector;
    private final ComboBox<TrackOption> column1LanguageSelector;
    private final ComboBox<TrackOption> column2LanguageSelector;
    private final Div transcriptionContainer;
    private final Div column1Container;
    private final Div column2Container;
    private final Button toggleButton;
    private final Map<String, List<SubtitleCue>> subtitlesByLanguage = new HashMap<>();
    private final List<HLSVideoPlayerComponent.TrackInfo> availableTracks = new ArrayList<>();
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    // State
    private boolean twoColumnMode = false;
    private boolean isVisible = true;
    // Callbacks
    private Consumer<Double> onSeekRequest;

    public TranscriptionPanel(String videoPlayerId, String videoFolderId) {
        this.videoPlayerId = videoPlayerId;
        this.panelId = "transcription_" + videoPlayerId;
        this.videoFolderId = videoFolderId;

        setId(panelId);
        addClassName("transcription-panel");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        // Toggle button
        toggleButton = new Button("Hide Transcription", e -> toggleVisibility());
        toggleButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        // Column mode selector
        columnModeSelector = new RadioButtonGroup<>();
        columnModeSelector.setItems("Single Column", "Two Columns");
        columnModeSelector.setValue("Single Column");
        columnModeSelector.addValueChangeListener(e -> {
            twoColumnMode = "Two Columns".equals(e.getValue());
            updateColumnVisibility();
        });

        // Language selectors
        column1LanguageSelector = new ComboBox<>("Left/Main Column");
        column1LanguageSelector.setItemLabelGenerator(TrackOption::getDisplayName);
        column1LanguageSelector.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                loadTranscriptionForColumn(1, e.getValue());
            }
        });

        column2LanguageSelector = new ComboBox<>("Right Column");
        column2LanguageSelector.setItemLabelGenerator(TrackOption::getDisplayName);
        column2LanguageSelector.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                loadTranscriptionForColumn(2, e.getValue());
            }
        });
        column2LanguageSelector.setVisible(false);

        // Controls layout
        controlsLayout = new HorizontalLayout();
        controlsLayout.setWidthFull();
        controlsLayout.setAlignItems(FlexComponent.Alignment.END);
        controlsLayout.setPadding(true);
        controlsLayout.addClassName("transcription-controls");
        controlsLayout.add(toggleButton, columnModeSelector, column1LanguageSelector, column2LanguageSelector);

        // Transcription containers
        column1Container = new Div();
        column1Container.addClassName("transcription-column");
        column1Container.addClassName("column-1");
        column1Container.setId(panelId + "_column1");

        column2Container = new Div();
        column2Container.addClassName("transcription-column");
        column2Container.addClassName("column-2");
        column2Container.setId(panelId + "_column2");
        column2Container.setVisible(false);

        transcriptionContainer = new Div();
        transcriptionContainer.addClassName("transcription-content");
        transcriptionContainer.add(column1Container, column2Container);

        add(controlsLayout, transcriptionContainer);

        injectStyles();
    }

    private void injectStyles() {
        String css = """
                <style id="transcription-panel-styles">
                .transcription-panel {
                    background: var(--lumo-base-color);
                    border: 1px solid var(--lumo-contrast-10pct);
                    border-radius: 8px;
                    overflow: hidden;
                }
                
                .transcription-controls {
                    background: var(--lumo-contrast-5pct);
                    border-bottom: 1px solid var(--lumo-contrast-10pct);
                    flex-wrap: wrap;
                    gap: 16px;
                }
                
                .transcription-content {
                    display: flex;
                    flex: 1;
                    overflow: hidden;
                    min-height: 300px;
                    max-height: 500px;
                }
                
                .transcription-column {
                    flex: 1;
                    overflow-y: auto;
                    padding: 8px;
                    scroll-behavior: smooth;
                }
                
                .transcription-column.column-2 {
                    border-left: 1px solid var(--lumo-contrast-10pct);
                }
                
                .transcription-cue {
                    display: flex;
                    gap: 12px;
                    padding: 10px 12px;
                    margin: 4px 0;
                    border-radius: 6px;
                    cursor: pointer;
                    transition: all 0.2s ease;
                    align-items: flex-start;
                }
                
                .transcription-cue:hover {
                    background: var(--lumo-contrast-5pct);
                }
                
                .transcription-cue.active {
                    background: var(--lumo-primary-color-10pct);
                    border-left: 3px solid var(--lumo-primary-color);
                }
                
                .transcription-cue.active .cue-text {
                    color: var(--lumo-primary-text-color);
                    font-weight: 500;
                }
                
                .cue-timestamp {
                    font-family: monospace;
                    font-size: 12px;
                    color: var(--lumo-secondary-text-color);
                    white-space: nowrap;
                    min-width: 60px;
                    padding-top: 2px;
                }
                
                .cue-text {
                    flex: 1;
                    font-size: 14px;
                    line-height: 1.5;
                    color: white;
                }
                
                .transcription-column::-webkit-scrollbar {
                    width: 6px;
                }
                
                .transcription-column::-webkit-scrollbar-track {
                    background: var(--lumo-contrast-5pct);
                }
                
                .transcription-column::-webkit-scrollbar-thumb {
                    background: var(--lumo-contrast-30pct);
                    border-radius: 3px;
                }
                
                .transcription-column::-webkit-scrollbar-thumb:hover {
                    background: var(--lumo-contrast-50pct);
                }
                
                .transcription-empty {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    height: 100%;
                    color: var(--lumo-secondary-text-color);
                    font-style: italic;
                }
                
                .two-column-mode .transcription-column {
                    flex: 1;
                }
                
                .single-column-mode .column-2 {
                    display: none;
                }
                
                .column-header {
                    font-weight: 600;
                    font-size: 12px;
                    text-transform: uppercase;
                    color: var(--lumo-secondary-text-color);
                    padding: 8px 12px;
                    border-bottom: 1px solid var(--lumo-contrast-10pct);
                    margin-bottom: 8px;
                    position: sticky;
                    top: 0;
                    background: var(--lumo-base-color);
                    z-index: 1;
                }
                </style>
                """;

        getElement().executeJs("""
                (function() {
                    if (!document.getElementById('transcription-panel-styles')) {
                        var div = document.createElement('div');
                        div.innerHTML = $0;
                        document.head.appendChild(div.firstElementChild);
                    }
                })();
                """, css);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        initializeTimeUpdateListener();
    }

    private void initializeTimeUpdateListener() {
        String js = """
                (function() {
                    var videoId = $0;
                    var panelId = $1;
                    var componentEl = $2;
                
                    var video = document.getElementById(videoId);
                    if (!video) {
                        console.warn('[Transcription] Video element not found:', videoId);
                        return;
                    }
                
                    // Store active cue index for each column
                    window['transcription_active_' + panelId] = { col1: -1, col2: -1 };
                
                    video.addEventListener('timeupdate', function() {
                        var currentTime = video.currentTime;
                        updateActiveCues(panelId, currentTime);
                    });
                
                    function updateActiveCues(pid, currentTime) {
                        updateColumnActiveCue(pid, 'column1', currentTime);
                        updateColumnActiveCue(pid, 'column2', currentTime);
                    }
                
                    function updateColumnActiveCue(pid, columnId, currentTime) {
                        var container = document.getElementById(pid + '_' + columnId);
                        if (!container) return;
                
                        var cues = container.querySelectorAll('.transcription-cue');
                        var activeIndex = -1;
                
                        cues.forEach(function(cue, index) {
                            var start = parseFloat(cue.getAttribute('data-start'));
                            var end = parseFloat(cue.getAttribute('data-end'));
                
                            if (currentTime >= start && currentTime < end) {
                                activeIndex = index;
                                if (!cue.classList.contains('active')) {
                                    // Remove active from all
                                    cues.forEach(function(c) { c.classList.remove('active'); });
                                    // Add active to current
                                    cue.classList.add('active');
                                    // Scroll into view
                                    scrollCueIntoView(container, cue);
                                }
                            }
                        });
                
                        // If no cue is active, remove all active states
                        if (activeIndex === -1) {
                            cues.forEach(function(c) { c.classList.remove('active'); });
                        }
                    }
                
                    function scrollCueIntoView(container, cue) {
                        var containerRect = container.getBoundingClientRect();
                        var cueRect = cue.getBoundingClientRect();
                
                        // Check if cue is outside visible area
                        if (cueRect.top < containerRect.top || cueRect.bottom > containerRect.bottom) {
                            cue.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        }
                    }
                
                    console.log('[Transcription] Time update listener initialized for:', videoId);
                })();
                """;

        getElement().executeJs(js, videoPlayerId, panelId, getElement());
    }

    public void setAvailableTracks(List<HLSVideoPlayerComponent.TrackInfo> tracks) {
        availableTracks.clear();
        availableTracks.addAll(tracks);

        List<TrackOption> options = new ArrayList<>();
        for (HLSVideoPlayerComponent.TrackInfo track : tracks) {
            options.add(new TrackOption(
                    track.getIndex(),
                    track.getName(),
                    track.getLang()
            ));
        }

        column1LanguageSelector.setItems(options);
        column2LanguageSelector.setItems(options);

        // Auto-select first track if available
        if (!options.isEmpty()) {
            column1LanguageSelector.setValue(options.get(0));
            if (options.size() > 1) {
                column2LanguageSelector.setValue(options.get(1));
            }
        }
    }

    public void loadSubtitlesFromUrl(String language, String subtitleUrl) {
        String js = """
                (function() {
                    fetch($0)
                        .then(response => response.text())
                        .then(text => {
                            var cues = parseVTT(text);
                            $2.$server.onSubtitlesLoaded($1, JSON.stringify(cues));
                        })
                        .catch(err => console.error('Failed to load subtitles:', err));
                
                    function parseVTT(vttText) {
                        var cues = [];
                        var lines = vttText.split('\\n');
                        var cue = null;
                        var cueIndex = 0;
                
                        for (var i = 0; i < lines.length; i++) {
                            var line = lines[i].trim();
                
                            // Skip WEBVTT header and empty lines
                            if (line === 'WEBVTT' || line === '' || line.startsWith('NOTE')) {
                                if (cue && cue.text) {
                                    cues.push(cue);
                                    cue = null;
                                }
                                continue;
                            }
                
                            // Check for timestamp line
                            if (line.includes('-->')) {
                                var times = line.split('-->');
                                var startTime = parseTimestamp(times[0].trim());
                                var endTime = parseTimestamp(times[1].trim().split(' ')[0]);
                
                                cue = {
                                    index: cueIndex++,
                                    startTime: startTime,
                                    endTime: endTime,
                                    text: ''
                                };
                            } else if (cue) {
                                // This is cue text
                                if (cue.text) {
                                    cue.text += ' ' + line;
                                } else {
                                    cue.text = line;
                                }
                            }
                        }
                
                        // Don't forget the last cue
                        if (cue && cue.text) {
                            cues.push(cue);
                        }
                
                        return cues;
                    }
                
                    function parseTimestamp(timestamp) {
                        var parts = timestamp.split(':');
                        var seconds = 0;
                
                        if (parts.length === 3) {
                            // HH:MM:SS.mmm
                            seconds = parseInt(parts[0]) * 3600 + parseInt(parts[1]) * 60 + parseFloat(parts[2]);
                        } else if (parts.length === 2) {
                            // MM:SS.mmm
                            seconds = parseInt(parts[0]) * 60 + parseFloat(parts[1]);
                        }
                
                        return seconds;
                    }
                })();
                """;

        getElement().executeJs(js, subtitleUrl, language, getElement());
    }

    @ClientCallable
    public void onSubtitlesLoaded(String language, String cuesJson) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            SubtitleCue[] cues = gson.fromJson(cuesJson, SubtitleCue[].class);
            subtitlesByLanguage.put(language, Arrays.asList(cues));

            // Refresh display if this language is currently selected
            TrackOption selected1 = column1LanguageSelector.getValue();
            if (selected1 != null && selected1.getLanguage().equals(language)) {
                displayCuesInColumn(1, Arrays.asList(cues));
            }

            TrackOption selected2 = column2LanguageSelector.getValue();
            if (selected2 != null && selected2.getLanguage().equals(language)) {
                displayCuesInColumn(2, Arrays.asList(cues));
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private void loadTranscriptionForColumn(int columnNumber, TrackOption track) {
        String language = track.getLanguage();

        // Check if already loaded
        if (subtitlesByLanguage.containsKey(language)) {
            displayCuesInColumn(columnNumber, subtitlesByLanguage.get(language));
        } else {
            String subtitleUrl = "/storage/videos/hls/" + getVideoFolderId() + "/subtitles_" + language;
            loadSubtitlesFromUrl(language, subtitleUrl);

            // Show loading state
            Div container = columnNumber == 1 ? column1Container : column2Container;
            container.removeAll();
            container.add(createLoadingIndicator());
        }
    }

    private String getVideoFolderId() {
        return videoFolderId;
    }

    private void displayCuesInColumn(int columnNumber, List<SubtitleCue> cues) {
        Div container = columnNumber == 1 ? column1Container : column2Container;
        container.removeAll();

        if (cues == null || cues.isEmpty()) {
            container.add(createEmptyState());
            return;
        }

        // Add header
        TrackOption selectedTrack = columnNumber == 1 ?
                column1LanguageSelector.getValue() : column2LanguageSelector.getValue();
        if (selectedTrack != null) {
            Div header = new Div();
            header.addClassName("column-header");
            header.setText(selectedTrack.getDisplayName());
            container.add(header);
        }

        // Add cues
        for (SubtitleCue cue : cues) {
            container.add(createCueElement(cue));
        }
    }

    private Component createCueElement(SubtitleCue cue) {
        Div cueDiv = new Div();
        cueDiv.addClassName("transcription-cue");
        cueDiv.getElement().setAttribute("data-start", String.valueOf(cue.getStartTime()));
        cueDiv.getElement().setAttribute("data-end", String.valueOf(cue.getEndTime()));
        cueDiv.getElement().setAttribute("data-index", String.valueOf(cue.getIndex()));

        Span timestamp = new Span(cue.getFormattedStartTime());
        timestamp.addClassName("cue-timestamp");

        Span text = new Span(cue.getText());
        text.addClassName("cue-text");

        cueDiv.add(timestamp, text);

        // Click to seek
        cueDiv.addClickListener(e -> {
            seekToTime(cue.getStartTime());
        });

        return cueDiv;
    }

    private void seekToTime(double time) {
        if (onSeekRequest != null) {
            onSeekRequest.accept(time);
        }

        // Also directly seek the video
        String js = """
                (function() {
                    var video = document.getElementById($0);
                    if (video) {
                        video.currentTime = $1;
                        video.play();
                    }
                })();
                """;
        getElement().executeJs(js, videoPlayerId, time);
    }

    private Component createEmptyState() {
        Div empty = new Div();
        empty.addClassName("transcription-empty");
        empty.setText("No subtitles available for this language");
        return empty;
    }

    private Component createLoadingIndicator() {
        Div loading = new Div();
        loading.addClassName("transcription-empty");
        loading.setText("Loading subtitles...");
        return loading;
    }

    private void updateColumnVisibility() {
        column2LanguageSelector.setVisible(twoColumnMode);
        column2Container.setVisible(twoColumnMode);

        if (twoColumnMode) {
            transcriptionContainer.addClassName("two-column-mode");
            transcriptionContainer.removeClassName("single-column-mode");
        } else {
            transcriptionContainer.removeClassName("two-column-mode");
            transcriptionContainer.addClassName("single-column-mode");
        }
    }

    private void toggleVisibility() {
        isVisible = !isVisible;
        transcriptionContainer.setVisible(isVisible);
        controlsLayout.getChildren()
                .filter(c -> c != toggleButton)
                .forEach(c -> c.setVisible(isVisible));
        toggleButton.setText(isVisible ? "Hide Transcription" : "Show Transcription");
    }

    // Helper class for track options
    @Getter
    @Setter
    public static class TrackOption {
        private final int index;
        private final String name;
        private final String language;

        public TrackOption(int index, String name, String language) {
            this.index = index;
            this.name = name;
            this.language = language;
        }

        public String getDisplayName() {
            if (!"und".equals(language)) {
                return name + " (" + language + ")";
            }
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TrackOption that = (TrackOption) o;
            return index == that.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }
    }
}