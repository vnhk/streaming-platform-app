package com.bervan.streamingapp.view.player;

import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.WatchDetails;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.ProductionDetails;
import com.bervan.streamingapp.view.AbstractProductionDetailsView;
import com.bervan.streamingapp.view.AbstractProductionListView;
import com.bervan.streamingapp.view.AbstractRemoteControlSupportedView;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.RouteParameters;

import java.io.IOException;
import java.util.Map;
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
    private VerticalLayout subtitleSettingsPanel;

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
    private Button downloadButton;

    public AbstractProductionPlayerView(VideoManager videoManager,
                                        Map<String, ProductionData> streamingProductionData) {
        super(ROUTE_NAME, AbstractProductionDetailsView.ROUTE_NAME);
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        cleanupResources();
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
        navigationBar.setAlignItems(FlexComponent.Alignment.CENTER);
        navigationBar.getStyle()
                .set("gap", "6px")
                .set("flex-wrap", "wrap")
                .set("padding", "6px 4px");

        addHomeButton();
        addDetailsButton();

        Div spacer = new Div();
        spacer.getStyle().set("flex", "1");
        navigationBar.add(spacer);

        addSubtitleSettingsToggle();
        addDownloadButton(video);
        addPreviousButton(video);
        addNextButton(video);
    }

    private void addSubtitleSettingsToggle() {
        Button btn = new Button("Subtitle Settings", new Icon(VaadinIcon.LINES_LIST));
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        btn.addClickListener(e -> {
            if (subtitleSettingsPanel != null) {
                subtitleSettingsPanel.setVisible(!subtitleSettingsPanel.isVisible());
            }
        });
        navigationBar.add(btn);
    }

    private void addHomeButton() {
        Button homeBtn = new Button("Home", new Icon(VaadinIcon.HOME));
        homeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        homeBtn.addClickListener(e -> UI.getCurrent().navigate(AbstractProductionListView.ROUTE_NAME));
        navigationBar.add(homeBtn);
    }

    private void addDetailsButton() {
        if (currentProductionData != null) {
            Button detailsBtn = new Button(currentProductionData.getProductionName(), new Icon(VaadinIcon.ARROW_BACKWARD));
            detailsBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            detailsBtn.addClickListener(e -> navigateToDetails(currentProductionData.getProductionId()));
            navigationBar.add(detailsBtn);
        }
    }

    private void addDownloadButton(Metadata video) {
        downloadButton = new Button("Download", new Icon(VaadinIcon.DOWNLOAD_ALT));
        downloadButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        downloadButton.addClickListener(e -> {
            downloadButton.setEnabled(false);
            downloadButton.setText("Preparing...");
            downloadButton.setIcon(new Icon(VaadinIcon.HOURGLASS));
            showPrimaryNotification("Preparing video for download. This may take a few minutes...");

            String url = "/storage/videos/download-and-convert/" + video.getId();
            String filename = video.getFilename() + ".mp4";

            UI.getCurrent().getPage().executeJs("""
                    const viewEl = $0;
                    const url = $1;
                    const filename = $2;
                    fetch(url)
                        .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.blob(); })
                        .then(blob => {
                            const a = document.createElement('a');
                            a.href = URL.createObjectURL(blob);
                            a.download = filename;
                            document.body.appendChild(a);
                            a.click();
                            document.body.removeChild(a);
                            viewEl.$server.onDownloadReady(true);
                        })
                        .catch(err => {
                            console.error('Download failed:', err);
                            viewEl.$server.onDownloadReady(false);
                        });
                    """, getElement(), url, filename);
        });
        navigationBar.add(downloadButton);
    }

    @ClientCallable
    public void onDownloadReady(boolean success) {
        if (downloadButton != null) {
            downloadButton.setEnabled(true);
            downloadButton.setText("Download");
            downloadButton.setIcon(new Icon(VaadinIcon.DOWNLOAD_ALT));
        }
        if (success) {
            showPrimaryNotification("Download complete!");
        } else {
            showErrorNotification("Download failed. Please try again.");
        }
    }

    private void addPreviousButton(Metadata video) {
        if (currentProductionData != null) {
            videoManager.getPrevVideoWithCrossSeasonSupport(currentVideoFolderId, currentProductionData)
                    .ifPresent(prevVideo -> {
                        Button prevBtn = new Button("Previous", new Icon(VaadinIcon.BACKWARDS));
                        prevBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                        prevBtn.addClickListener(e -> navigateToVideo(prevVideo.getId().toString()));
                        navigationBar.add(prevBtn);
                    });
        } else {
            videoManager.getPrevVideo(video).ifPresent(prevVideo -> {
                Button prevBtn = new Button("Previous", new Icon(VaadinIcon.BACKWARDS));
                prevBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                prevBtn.addClickListener(e -> navigateToVideo(prevVideo.getId().toString()));
                navigationBar.add(prevBtn);
            });
        }
    }

    private void addNextButton(Metadata video) {
        if (currentProductionData != null) {
            videoManager.getNextVideoWithCrossSeasonSupport(currentVideoFolderId, currentProductionData)
                    .ifPresent(nextVideo -> {
                        Button nextBtn = new Button("Next", new Icon(VaadinIcon.FORWARD));
                        nextBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                        nextBtn.addClickListener(e -> navigateToVideo(nextVideo.getId().toString()));
                        navigationBar.add(nextBtn);
                    });
        } else {
            videoManager.getNextVideo(video).ifPresent(nextVideo -> {
                Button nextBtn = new Button("Next", new Icon(VaadinIcon.FORWARD));
                nextBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                nextBtn.addClickListener(e -> navigateToVideo(nextVideo.getId().toString()));
                navigationBar.add(nextBtn);
            });
        }
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

    private SubtitleControlPanel createSubtitleControls(WatchDetails watchDetails, Metadata videoFolder) {
        SubtitleControlPanel panel = new SubtitleControlPanel(
                watchDetails.getSubtitleDelayEN(),
                watchDetails.getSubtitleDelayPL(),
                watchDetails.getSubtitleDelayES()
        );

        panel.setEnDelayChangeListener(delay -> {
            videoPlayer.shiftSubtitles("en", delay);
            saveSubtitleDelays(delay, panel.getPlDelay(), panel.getEsDelay());
        });

        panel.setPlDelayChangeListener(delay -> {
            videoPlayer.shiftSubtitles("pl", delay);
            saveSubtitleDelays(panel.getEnDelay(), delay, panel.getEsDelay());
        });

        panel.setEsDelayChangeListener(delay -> {
            videoPlayer.shiftSubtitles("es", delay);
            saveSubtitleDelays(panel.getEnDelay(), panel.getPlDelay(), delay);
        });

        videoManager.findSubtitle(videoFolder, VideoManager.EN).ifPresent(sub ->
                panel.setEnSavePermanentlyListener(() -> savePermanently(sub, panel.getEnDelay()))
        );
        videoManager.findSubtitle(videoFolder, VideoManager.PL).ifPresent(sub ->
                panel.setPlSavePermanentlyListener(() -> savePermanently(sub, panel.getPlDelay()))
        );
        videoManager.findSubtitle(videoFolder, VideoManager.ES).ifPresent(sub ->
                panel.setEsSavePermanentlyListener(() -> savePermanently(sub, panel.getEsDelay()))
        );

        return panel;
    }

    private void savePermanently(Metadata subtitle, double delay) {
        try {
            videoManager.applySubtitleDelayPermanently(subtitle, delay);
            showPrimaryNotification("Subtitle delay saved permanently to file.");
        } catch (IOException e) {
            log.error("Failed to save subtitle delay permanently", e);
            showErrorNotification("Failed to save subtitle delay permanently.");
        }
    }

    protected Component buildSubtitleSettingsContent(String videoId, Metadata video) {
        return null;
    }

    private VerticalLayout buildSubtitleSettingsPanel(Metadata video, WatchDetails watchDetails, Set<String> availableSubtitles) {
        VerticalLayout panel = new VerticalLayout();
        panel.setVisible(false);
        panel.setSpacing(true);
        panel.setPadding(true);
        panel.setWidthFull();
        panel.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "10px")
                .set("margin-top", "8px");

        boolean hasDelayControls = !availableSubtitles.isEmpty();
        if (hasDelayControls) {
            Span delayHeader = new Span("Subtitle Delay");
            delayHeader.getStyle()
                    .set("font-weight", "600")
                    .set("font-size", "var(--lumo-font-size-m)");
            panel.add(delayHeader, createSubtitleControls(watchDetails, video));
        }

        Component extra = buildSubtitleSettingsContent(currentVideoFolderId, video);
        if (extra != null) {
            if (hasDelayControls) {
                panel.add(new Hr());
            }
            panel.add(extra);
        }

        return panel;
    }

    private void setupKeyboardShortcuts() {
        String domId = videoPlayer.getPlayerUniqueId();
        getElement().executeJs(
                "var vid = $0;" +
                        "var seekStep = $1;" +
                        "if(window._playerKeyHandler) { document.removeEventListener('keydown', window._playerKeyHandler, true); }" +
                        "window._playerKeyHandler = function(e) {" +
                        "  var video = document.getElementById(vid);" +
                        "  if(!video || !document.contains(video)) {" +
                        "    document.removeEventListener('keydown', window._playerKeyHandler, true);" +
                        "    window._playerKeyHandler = null;" +
                        "    return;" +
                        "  }" +
                        "  var tag = (e.target && e.target.tagName) ? e.target.tagName.toLowerCase() : '';" +
                        "  if(tag === 'input' || tag === 'textarea' || tag === 'select' || (e.target && e.target.isContentEditable)) return;" +
                        "  switch(e.code) {" +
                        "    case 'Space':" +
                        "      e.preventDefault(); e.stopPropagation();" +
                        "      video.paused ? video.play() : video.pause();" +
                        "      break;" +
                        "    case 'ArrowRight':" +
                        "      e.preventDefault(); e.stopPropagation();" +
                        "      video.currentTime += seekStep;" +
                        "      break;" +
                        "    case 'ArrowLeft':" +
                        "      e.preventDefault(); e.stopPropagation();" +
                        "      video.currentTime -= seekStep;" +
                        "      break;" +
                        "    case 'KeyB':" +
                        "      e.preventDefault();" +
                        "      if(video.textTracks.length >= 1) {" +
                        "        var showing = false;" +
                        "        for(var i=0; i<video.textTracks.length; i++) { if(video.textTracks[i].mode==='showing') { showing=true; break; } }" +
                        "        for(var i=0; i<video.textTracks.length; i++) { video.textTracks[i].mode = showing ? 'hidden' : (i===0 ? 'showing' : 'hidden'); }" +
                        "      }" +
                        "      break;" +
                        "    case 'KeyF':" +
                        "      e.preventDefault();" +
                        "      if(document.fullscreenElement===video) { document.exitFullscreen(); }" +
                        "      else if(video.requestFullscreen) { video.requestFullscreen(); }" +
                        "      else if(video.webkitRequestFullscreen) { video.webkitRequestFullscreen(); }" +
                        "      break;" +
                        "  }" +
                        "};" +
                        "document.addEventListener('keydown', window._playerKeyHandler, true);",
                domId, SEEK_STEP_SECONDS
        );
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

    private void saveSubtitleDelays(double enDelay, double plDelay, double esDelay) {
        WatchDetails watchDetails = loadWatchDetails(currentVideoFolderId);
        videoManager.saveSubtitleDelays(watchDetails, enDelay, plDelay, esDelay);
    }

    private void cleanupResources() {
        getElement().executeJs(
                "if (window._progressTracker) {" +
                        "  clearInterval(window._progressTracker);" +
                        "  window._progressTracker = null;" +
                        "}" +
                        "if (window._playerKeyHandler) {" +
                        "  document.removeEventListener('keydown', window._playerKeyHandler, true);" +
                        "  window._playerKeyHandler = null;" +
                        "}" +
                        "var vid = document.getElementById($0);" +
                        "if (vid) { vid.pause(); }",
                videoPlayer != null ? videoPlayer.getPlayerUniqueId() : ""
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

        Span videoTitle = new Span(video.getFilename());
        videoTitle.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("padding", "4px 0 8px 0")
                .set("display", "block");
        add(videoTitle);

        if (productionData.getProductionDetails().getVideoFormat() == ProductionDetails.VideoFormat.MP4) {
            videoPlayer = createMp4VideoPlayer(watchDetails, productionData, availableSubtitles);
        } else {
            videoPlayer = createHlsVideoPlayer(watchDetails, productionData, availableSubtitles);
        }
        add(videoPlayer);

        subtitleSettingsPanel = buildSubtitleSettingsPanel(video, watchDetails, availableSubtitles);
        add(subtitleSettingsPanel);
    }
}