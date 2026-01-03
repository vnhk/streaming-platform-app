package com.bervan.streamingapp.view.player;

import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.WatchDetails;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.view.AbstractProductionDetailsView;
import com.bervan.streamingapp.view.AbstractRemoteControlSupportedView;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractProductionPlayerView extends AbstractRemoteControlSupportedView
        implements HasUrlParameter<String> {

    // Constants
    public static final String ROUTE_NAME = "/streaming-platform/video-player";
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
    private MP4VideoPlayerComponent videoPlayer;

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

            Metadata video = videoManager.findMp4VideoById(videoId, streamingProductionData)
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

    private MP4VideoPlayerComponent createVideoPlayer(WatchDetails watchDetails) {
        return new MP4VideoPlayerComponent(
                VIDEO_PLAYER_ID,
                currentVideoId,
                watchDetails.getCurrentVideoTime()
        );
    }

    private SubtitleControlPanel createSubtitleControls(WatchDetails watchDetails) {
        SubtitleControlPanel panel = new SubtitleControlPanel(
                watchDetails.getSubtitleDelayEN(),
                watchDetails.getSubtitleDelayPL(),
                watchDetails.getSubtitleDelayES()
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
}