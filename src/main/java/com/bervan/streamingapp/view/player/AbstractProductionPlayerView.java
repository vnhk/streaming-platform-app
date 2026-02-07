package com.bervan.streamingapp.view.player;

import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.WatchDetails;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.ProductionDetails;
import com.bervan.streamingapp.view.AbstractProductionDetailsView;
import com.bervan.streamingapp.view.AbstractRemoteControlSupportedView;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.RouteParameters;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@JavaScript("https://cdn.jsdelivr.net/npm/hls.js@latest")
public abstract class AbstractProductionPlayerView extends AbstractRemoteControlSupportedView
        implements BeforeEnterObserver {
    public static final String ROUTE_NAME = "/streaming-platform/video-player/:productionName/:videoFolderId";
    private static final double SEEK_STEP_SECONDS = 5.0;
    private static final int WATCH_PROGRESS_INTERVAL_MS = 10000;
    private static final double MIN_TIME_CHANGE_TO_SAVE = 5.0;
    private static final String VIDEO_PLAYER_ID_PREFIX = "videoPlayer"; // Changed to prefix

    // UI Components
    protected final HorizontalLayout navigationBar = new HorizontalLayout();
    protected final Map<String, ProductionData> streamingProductionData;

    // Dependencies
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final VideoManager videoManager;

    // Changed: Using the new HLS component
    private AbstractVideoPlayer videoPlayer;

    // State
    private String currentVideoFolderId;
    private String currentProductionName;
    private ProductionData currentProductionData;
    private double lastSavedTime = 0;
    private ShortcutRegistration toggleSubtitlesShortcut;
    private ShortcutRegistration togglePlayPauseShortcut;
    private ShortcutRegistration seekForwardShortcut;
    private ShortcutRegistration seekBackwardShortcut;
    private ShortcutRegistration toggleFullscreenShortcut;

    public AbstractProductionPlayerView(VideoManager videoManager,
                                        Map<String, ProductionData> streamingProductionData) {
        super(ROUTE_NAME, AbstractProductionDetailsView.ROUTE_NAME);
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;
    }

    private void removeKeyboardShortcuts() {
        if (toggleSubtitlesShortcut != null) toggleSubtitlesShortcut.remove();
        if (togglePlayPauseShortcut != null) togglePlayPauseShortcut.remove();
        if (seekForwardShortcut != null) seekForwardShortcut.remove();
        if (seekBackwardShortcut != null) seekBackwardShortcut.remove();
        if (toggleFullscreenShortcut != null) toggleFullscreenShortcut.remove();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        cleanupResources();
        removeKeyboardShortcuts();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        RouteParameters p = event.getRouteParameters();

        String productionName = p.get("productionName")
                .orElseThrow(NotFoundException::new);

        String videoFolderId = p.get("videoFolderId")
                .orElseThrow(NotFoundException::new);

        initializePlayer(productionName, videoFolderId);
    }

    private void initializePlayer(String productionName, String videoFolderId) {
        try {
            ProductionData productionData = streamingProductionData.get(productionName);
            this.currentVideoFolderId = videoFolderId;
            this.currentProductionName = productionName;
            this.currentProductionData = productionData;
            removeAll();
            navigationBar.removeAll();

            Metadata videoFolder = videoManager.findVideoFolderById(currentVideoFolderId, productionData)
                    .orElseThrow(() -> new IllegalArgumentException("Video not found: " + videoFolderId));

            Set<String> availableSubtitles = videoManager.availableSubtitles(videoFolder);
            WatchDetails watchDetails = loadWatchDetails(videoFolderId);

            buildUserInterface(productionData, videoFolder, watchDetails, availableSubtitles);
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
            Button detailsBtn = createStyledButton(productionData.getProductionName() + " - Details");
            detailsBtn.addClickListener(e -> navigateToDetails(productionData.getProductionId()));
            navigationBar.add(detailsBtn);
        });
    }

    private void addPreviousButton(Metadata video) {
        // Use cross-season navigation if production data is available
        if (currentProductionData != null) {
            videoManager.getPrevVideoWithCrossSeasonSupport(currentVideoFolderId, currentProductionData)
                    .ifPresent(prevVideo -> {
                        Button prevBtn = createStyledButton("Previous episode");
                        prevBtn.addClickListener(e -> navigateToVideo(prevVideo.getId().toString()));
                        navigationBar.add(prevBtn);
                    });
        } else {
            // Fallback to old method
            videoManager.getPrevVideo(video).ifPresent(prevVideo -> {
                Button prevBtn = createStyledButton("Previous episode");
                prevBtn.addClickListener(e -> navigateToVideo(prevVideo.getId().toString()));
                navigationBar.add(prevBtn);
            });
        }
    }

    private void addNextButton(Metadata video) {
        // Use cross-season navigation if production data is available
        if (currentProductionData != null) {
            videoManager.getNextVideoWithCrossSeasonSupport(currentVideoFolderId, currentProductionData)
                    .ifPresent(nextVideo -> {
                        Button nextBtn = createStyledButton("Next episode");
                        nextBtn.addClickListener(e -> navigateToVideo(nextVideo.getId().toString()));
                        navigationBar.add(nextBtn);
                    });
        } else {
            // Fallback to old method
            videoManager.getNextVideo(video).ifPresent(nextVideo -> {
                Button nextBtn = createStyledButton("Next episode");
                nextBtn.addClickListener(e -> navigateToVideo(nextVideo.getId().toString()));
                navigationBar.add(nextBtn);
            });
        }
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
        // Force reload/navigation to ensure player clean-up and init
        String url = "/streaming-platform/video-player/" + currentProductionName + "/" + videoId;
        UI.getCurrent().getPage().setLocation(url);
    }

    private HLSVideoPlayerComponent createHlsVideoPlayer(WatchDetails watchDetails, ProductionData productionData, Set<String> availableSubtitles) {
        return new HLSVideoPlayerComponent(
                VIDEO_PLAYER_ID_PREFIX,
                currentVideoFolderId,
                watchDetails.getCurrentVideoTime(),
                productionData,
                availableSubtitles
        );
    }

    private MP4VideoPlayerComponent createMp4VideoPlayer(WatchDetails watchDetails, ProductionData productionData, Set<String> availableSubtitles) {
        return new MP4VideoPlayerComponent(
                VIDEO_PLAYER_ID_PREFIX,
                currentVideoFolderId,
                watchDetails.getCurrentVideoTime(),
                productionData,
                availableSubtitles
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

        panel.setEsDelayChangeListener(delay -> {
            videoPlayer.shiftSubtitles("pl", delay);
            saveSubtitleDelays(panel.getEnDelay(), delay);
        });

        return panel;
    }

    private void setupKeyboardShortcuts() {
        toggleSubtitlesShortcut = Shortcuts.addShortcutListener(this, () -> videoPlayer.toggleSubtitles(), Key.KEY_B).listenOn(this);
        togglePlayPauseShortcut = Shortcuts.addShortcutListener(this, () -> videoPlayer.togglePlayPause(), Key.SPACE).listenOn(this);
        seekForwardShortcut = Shortcuts.addShortcutListener(this, () -> videoPlayer.seek(SEEK_STEP_SECONDS), Key.ARROW_RIGHT).listenOn(this);
        seekBackwardShortcut = Shortcuts.addShortcutListener(this, () -> videoPlayer.seek(-SEEK_STEP_SECONDS), Key.ARROW_LEFT).listenOn(this);
        toggleFullscreenShortcut = Shortcuts.addShortcutListener(this, () -> videoPlayer.toggleFullscreen(), Key.KEY_F).listenOn(this);
    }

    private void startProgressTracking() {
        String domId = videoPlayer.getPlayerUniqueId();

        getElement().executeJs(
                "const video = document.getElementById($0);" +
                        "if (video) {" +
                        "  let lastReportedTime = 0;" +
                        "  if(window._progressTracker) clearInterval(window._progressTracker);" +
                        "  " +
                        "  window._progressTracker = setInterval(() => {" +
                        "    if (!isNaN(video.currentTime) && " +
                        "        Math.abs(video.currentTime - lastReportedTime) >= $1) {" +
                        "      lastReportedTime = video.currentTime;" +
                        "      $2.$server.reportWatchProgress(video.currentTime);" +
                        "    }" +
                        "  }, $3);" +
                        "}",
                domId,
                MIN_TIME_CHANGE_TO_SAVE,
                getElement(),
                WATCH_PROGRESS_INTERVAL_MS
        );
    }

    @ClientCallable
    public void reportWatchProgress(double currentTime) {
        if (shouldSaveProgress(currentTime)) {
            lastSavedTime = currentTime;
            WatchDetails watchDetails = loadWatchDetails(currentVideoFolderId);
            videoManager.saveWatchProgress(watchDetails, currentTime);
        }
    }

    private boolean shouldSaveProgress(double currentTime) {
        return Math.abs(currentTime - lastSavedTime) >= MIN_TIME_CHANGE_TO_SAVE;
    }

    private void saveSubtitleDelays(double enDelay, double plDelay) {
        WatchDetails watchDetails = loadWatchDetails(currentVideoFolderId);
        videoManager.saveSubtitleDelays(watchDetails, enDelay, plDelay);
    }

    private void cleanupResources() {
        getElement().executeJs(
                "if (window._progressTracker) {" +
                        "  clearInterval(window._progressTracker);" +
                        "  window._progressTracker = null;" +
                        "}"
        );
    }

    protected void addCustomNavigationButtons(String videoId, Metadata video) {
        // Hook
    }

    protected void addCustomComponents(String videoId, Metadata video) {
        // Hook
    }

    private void buildUserInterface(ProductionData productionData, Metadata video, WatchDetails watchDetails, Set<String> availableSubtitles) {
        configurePage();

        add(navigationBar);
        buildNavigationBar(video);
        addCustomNavigationButtons(currentVideoFolderId, video);

        add(new Hr(), new H4("Video: " + video.getFilename()));
        if (productionData.getProductionDetails().getVideoFormat() == ProductionDetails.VideoFormat.MP4) {
            videoPlayer = createMp4VideoPlayer(watchDetails, productionData, availableSubtitles);
            add(videoPlayer);
            SubtitleControlPanel subtitleControls = createSubtitleControls(watchDetails);
            add(subtitleControls);
        } else {
            videoPlayer = createHlsVideoPlayer(watchDetails, productionData, availableSubtitles);
            add(videoPlayer);
        }

        addCustomComponents(currentVideoFolderId, video);
    }
}