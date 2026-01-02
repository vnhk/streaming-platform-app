package com.bervan.streamingapp.view;

import com.bervan.common.component.BervanButton;
import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.WatchDetails;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.structure.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;

import java.util.*;

public abstract class AbstractProductionPlayerView extends AbstractRemoteControlSupportedView
        implements HasUrlParameter<String> {

    // Constants
    public static final String ROUTE_NAME = "/streaming-platform/video-player";
    private static final double SUBTITLE_DELAY_STEP = 0.5;
    private static final double SEEK_STEP_SECONDS = 5.0;
    private static final int WATCH_PROGRESS_INTERVAL_MS = 10000;
    private static final double MIN_TIME_CHANGE_TO_SAVE = 5.0;
    private static final String VIDEO_PLAYER_ID = "videoPlayer";
    // UI Components
    protected final HorizontalLayout navigationBar = new HorizontalLayout();
    protected final Map<String, ProductionData> streamingProductionData;
    // Dependencies
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final VideoManager videoManager;
    private VideoPlayerComponent videoPlayer;

    // State
    private String currentVideoId;
    private double lastSavedTime = 0;

    public AbstractProductionPlayerView(VideoManager videoManager,
                                        Map<String, ProductionData> streamingProductionData) {
        super(ROUTE_NAME, AbstractProductionDetailsView.ROUTE_NAME);
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        String videoId = event.getRouteParameters()
                .get("___url_parameter")
                .orElse(UUID.randomUUID().toString());
        initializePlayer(videoId);
    }

    private void initializePlayer(String videoId) {
        try {
            this.currentVideoId = videoId;

            Metadata video = findVideoById(videoId)
                    .orElseThrow(() -> new IllegalArgumentException("Video not found: " + videoId));

            WatchDetails watchDetails = loadWatchDetails(videoId);

            buildUserInterface(video, watchDetails);
            setupKeyboardShortcuts();
            startProgressTracking();

        } catch (Exception e) {
            log.error("Could not load video!", e);
            showErrorNotification("Could not load video!");
        }
    }

    //instead of searching tree for all production, video player needs to know production id (send by parameter)
    private Optional<Metadata> findVideoById(String videoId) {
        Collection<ProductionData> productions = streamingProductionData.values();
        for (ProductionData productionData : productions) {
            BaseRootProductionStructure productionStructure = productionData.getProductionStructure();
            if (productionStructure instanceof MovieRootProductionStructure) {
                List<Metadata> videos = ((MovieRootProductionStructure) productionStructure).getVideos();
                Optional<Metadata> videoOpt = videos.stream().filter(video -> video.getId().toString().equals(videoId)).findFirst();
                if (videoOpt.isPresent()) {
                    return videoOpt;
                }
            } else if (productionStructure instanceof TvSeriesRootProductionStructure) {
                List<SeasonStructure> seasons = ((TvSeriesRootProductionStructure) productionStructure).getSeasons();
                for (SeasonStructure season : seasons) {
                    for (EpisodeStructure episode : season.getEpisodes()) {
                        if (episode.getVideo().getId().toString().equals(videoId)) {
                            return Optional.of(episode.getVideo());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private WatchDetails loadWatchDetails(String videoId) {
        return videoManager.getOrCreateWatchDetails(
                AuthService.getLoggedUserId().toString(),
                videoId
        );
    }

    private void configurePage() {
        setSizeFull();
        setSpacing(false);
        setPadding(true);
    }

    private void buildNavigationBar(Metadata video) {
        addDetailsButton(video);
        addPreviousButton(video);
        addNextButton(video);
    }

    private void addDetailsButton(Metadata video) {
        findProductionData(video).ifPresent(productionData -> {
            Button detailsBtn = createStyledButton(
                    productionData.getProductionName() + " - Details"
            );
            detailsBtn.addClickListener(e ->
                    navigateToDetails(productionData.getProductionId())
            );
            navigationBar.add(detailsBtn);
        });
    }

    private void addPreviousButton(Metadata video) {
        videoManager.getPrevVideo(video).ifPresent(prevVideo -> {
            Button prevBtn = createStyledButton("Previous episode");
            prevBtn.addClickListener(e ->
                    navigateToVideo(prevVideo.getId().toString())
            );
            navigationBar.add(prevBtn);
        });
    }

    private void addNextButton(Metadata video) {
        videoManager.getNextVideo(video).ifPresent(nextVideo -> {
            Button nextBtn = createStyledButton("Next episode");
            nextBtn.addClickListener(e ->
                    navigateToVideo(nextVideo.getId().toString())
            );
            navigationBar.add(nextBtn);
        });
    }

    private Button createStyledButton(String text) {
        Button button = new Button(text);
        button.addClassName("option-button");
        return button;
    }

    private Optional<ProductionData> findProductionData(Metadata video) {
        return streamingProductionData.values().stream()
                .filter(pd -> pd.getMainFolder().getId().equals(video.getId()))
                .findFirst();
    }

    private void navigateToDetails(String productionId) {
        UI.getCurrent().navigate("/streaming-platform/details/" + productionId);
    }

    private void navigateToVideo(String videoId) {
        UI.getCurrent().getPage().setLocation(
                "/streaming-platform/video-player/" + videoId
        );
    }

    private VideoPlayerComponent createVideoPlayer(WatchDetails watchDetails) {
        return new VideoPlayerComponent(
                currentVideoId,
                watchDetails.getCurrentVideoTime()
        );
    }

    private SubtitleControlPanel createSubtitleControls(WatchDetails watchDetails) {
        SubtitleControlPanel panel = new SubtitleControlPanel(
                watchDetails.getSubtitleDelayEN(),
                watchDetails.getSubtitleDelayPL()
        );

        panel.setEnDelayChangeListener(delay -> {
            videoPlayer.shiftSubtitles("en", delay);
            saveSubtitleDelays(delay, panel.getPlDelay());
        });

        panel.setPlDelayChangeListener(delay -> {
            videoPlayer.shiftSubtitles("pl", delay);
            saveSubtitleDelays(panel.getEnDelay(), delay);
        });

        return panel;
    }

    private void setupKeyboardShortcuts() {
        // Using Vaadin's Shortcuts API
        Shortcuts.addShortcutListener(this, () -> videoPlayer.toggleSubtitles(), Key.KEY_B)
                .listenOn(this);
        Shortcuts.addShortcutListener(this, () -> videoPlayer.togglePlayPause(), Key.SPACE)
                .listenOn(this);
        Shortcuts.addShortcutListener(this, () -> videoPlayer.seek(SEEK_STEP_SECONDS), Key.ARROW_RIGHT)
                .listenOn(this);
        Shortcuts.addShortcutListener(this, () -> videoPlayer.seek(-SEEK_STEP_SECONDS), Key.ARROW_LEFT)
                .listenOn(this);
        Shortcuts.addShortcutListener(this, () -> videoPlayer.toggleFullscreen(), Key.KEY_F)
                .listenOn(this);
    }

    private void startProgressTracking() {
        getElement().executeJs(
                "const video = document.getElementById($0);" +
                        "if (video) {" +
                        "  let lastReportedTime = 0;" +
                        "  window._progressTracker = setInterval(() => {" +
                        "    if (!isNaN(video.currentTime) && " +
                        "        Math.abs(video.currentTime - lastReportedTime) >= $1) {" +
                        "      lastReportedTime = video.currentTime;" +
                        "      $2.$server.reportWatchProgress(video.currentTime);" +
                        "    }" +
                        "  }, $3);" +
                        "}",
                VIDEO_PLAYER_ID,
                MIN_TIME_CHANGE_TO_SAVE,
                getElement(),
                WATCH_PROGRESS_INTERVAL_MS
        );
    }

    @ClientCallable
    public void reportWatchProgress(double currentTime) {
        if (shouldSaveProgress(currentTime)) {
            lastSavedTime = currentTime;
            WatchDetails watchDetails = loadWatchDetails(currentVideoId);
            videoManager.saveWatchProgress(watchDetails, currentTime);
        }
    }

    private boolean shouldSaveProgress(double currentTime) {
        return Math.abs(currentTime - lastSavedTime) >= MIN_TIME_CHANGE_TO_SAVE;
    }

    private void saveSubtitleDelays(double enDelay, double plDelay) {
        WatchDetails watchDetails = loadWatchDetails(currentVideoId);
        videoManager.saveSubtitleDelays(watchDetails, enDelay, plDelay);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        cleanupResources();
    }

    private void cleanupResources() {
        getElement().executeJs(
                "if (window._progressTracker) {" +
                        "  clearInterval(window._progressTracker);" +
                        "  window._progressTracker = null;" +
                        "}"
        );
    }

    /**
     * Hook method for subclasses to add custom navigation buttons
     * Called after standard navigation buttons are added
     */
    protected void addCustomNavigationButtons(String videoId, Metadata video) {
        // Default: no custom buttons
    }

    /**
     * Hook method for subclasses to add custom components
     * Called after video player and subtitle controls are added
     */
    protected void addCustomComponents(String videoId, Metadata video) {
        // Default: no custom components
    }

    // Modify buildUserInterface method to call the hooks:
    private void buildUserInterface(Metadata video, WatchDetails watchDetails) {
        configurePage();

        add(navigationBar);
        buildNavigationBar(video);
        addCustomNavigationButtons(currentVideoId, video); // ADD THIS LINE

        add(new Hr(), new H4("Video: " + video.getFilename()));

        videoPlayer = createVideoPlayer(watchDetails);
        add(videoPlayer);

        SubtitleControlPanel subtitleControls = createSubtitleControls(watchDetails);
        add(subtitleControls);

        addCustomComponents(currentVideoId, video); // ADD THIS LINE
    }

    /**
     * Component encapsulating the HTML5 video player with subtitle support
     */
    private static class VideoPlayerComponent extends Div {
        private final Element videoElement;
        private final String videoId;

        public VideoPlayerComponent(String videoId, double startTime) {
            this.videoId = videoId;
            this.videoElement = buildVideoElement();

            addClassName("video-container");
            getElement().appendChild(videoElement);

            initializeSubtitleShiftFunction();
            setCurrentTime(startTime);
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
            source.setAttribute("src", "/storage/videos/video/" + videoId);
            source.setAttribute("type", "video/mp4");
            video.appendChild(source);
        }

        private void addSubtitleTrack(Element video, String lang, String label,
                                      String src, boolean isDefault) {
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

        public void shiftSubtitles(String language, double delay) {
            getElement().executeJs("window.shiftSubtitles($0, $1)", language, delay);
        }
    }

    /**
     * Component for controlling subtitle delays
     */
    private static class SubtitleControlPanel extends VerticalLayout {
        private final NumberField enDelayField;
        private final NumberField plDelayField;

        public SubtitleControlPanel(double initialEnDelay, double initialPlDelay) {
            setAlignItems(Alignment.CENTER);
            setSpacing(true);

            enDelayField = createDelayField("Subtitle Delay (EN) [s]", initialEnDelay);
            plDelayField = createDelayField("Subtitle Delay (PL) [s]", initialPlDelay);

            Button resetButton = new BervanButton(
                    "Reset Delays",
                    e -> resetDelays(initialEnDelay, initialPlDelay)
            );

            add(enDelayField, plDelayField, resetButton);
        }

        private NumberField createDelayField(String label, double initialValue) {
            NumberField field = new NumberField(label);
            field.setStep(SUBTITLE_DELAY_STEP);
            field.setWidth("300px");
            field.setValue(initialValue);
            return field;
        }

        private void resetDelays(double enDelay, double plDelay) {
            enDelayField.setValue(enDelay);
            plDelayField.setValue(plDelay);
        }

        public void setEnDelayChangeListener(java.util.function.Consumer<Double> listener) {
            enDelayField.addValueChangeListener(e -> {
                double value = Optional.ofNullable(e.getValue()).orElse(0.0);
                listener.accept(value);
            });
        }

        public void setPlDelayChangeListener(java.util.function.Consumer<Double> listener) {
            plDelayField.addValueChangeListener(e -> {
                double value = Optional.ofNullable(e.getValue()).orElse(0.0);
                listener.accept(value);
            });
        }

        public double getEnDelay() {
            return Optional.ofNullable(enDelayField.getValue()).orElse(0.0);
        }

        public double getPlDelay() {
            return Optional.ofNullable(plDelayField.getValue()).orElse(0.0);
        }
    }
}