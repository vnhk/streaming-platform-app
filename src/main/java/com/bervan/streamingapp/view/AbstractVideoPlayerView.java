package com.bervan.streamingapp.view;

import com.bervan.common.component.BervanButton;
import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.WatchDetails;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractVideoPlayerView extends AbstractRemoteControlSupportedView implements HasUrlParameter<String> {
    public static final String ROUTE_NAME = "/streaming-platform/video-player";
    protected final HorizontalLayout topLayout = new HorizontalLayout();
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final VideoManager videoManager;

    public AbstractVideoPlayerView(VideoManager videoManager) {
        super(ROUTE_NAME, AbstractVideoDetailsView.ROUTE_NAME);
        this.videoManager = videoManager;
    }

    @Override
    public void setParameter(BeforeEvent event, String s) {
        String videoId = event.getRouteParameters().get("___url_parameter").orElse(UUID.randomUUID().toString());
        init(videoId);
    }

    private void init(String videoId) {
        try {
            List<Metadata> video = videoManager.loadById(videoId);
            if (video.size() != 1) {
                log.error("Could not find video based on provided id!");
                showErrorNotification("Could not find video!");
                return;
            }

            Metadata mainDirectory = videoManager.getMainMovieFolder(video.get(0));

            add(topLayout);
            if (mainDirectory != null) {
                Button detailsButton = new Button(mainDirectory.getFilename() + " - Details");
                detailsButton.addClassName("option-button");
                detailsButton.addClickListener(click ->
                        UI.getCurrent().navigate("/streaming-platform/details/" + mainDirectory.getId())
                );

                topLayout.add(detailsButton);
            }

            Optional<Metadata> prevVideo = videoManager.getPrevVideo(video.get(0));
            Optional<Metadata> nextVideo = videoManager.getNextVideo(video.get(0));

            if (prevVideo.isPresent()) {
                Button prevButton = new Button("Previous episode");
                prevButton.addClassName("option-button");
                prevButton.addClickListener(click ->
                        UI.getCurrent().getPage().setLocation("/streaming-platform/video-player/" + prevVideo.get().getId())
                );
                topLayout.add(prevButton);
            }

            if (nextVideo.isPresent()) {
                Button nextButton = new Button("Next episode");
                nextButton.addClassName("option-button");
                nextButton.addClickListener(click ->
                        UI.getCurrent().getPage().setLocation("/streaming-platform/video-player/" + nextVideo.get().getId())
                );
                topLayout.add(nextButton);
            }

            add(new Hr(), new H4("Video: " + video.get(0).getFilename()));

            setSizeFull();
            setSpacing(false);
            setPadding(true);

            WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(
                    AuthService.getLoggedUserId().toString(), videoId
            );

            double enDelay = watchDetails.getSubtitleDelayEN();
            double plDelay = watchDetails.getSubtitleDelayPL();

            String videoSrc = "/storage/videos/video/" + videoId;
            Div videoContainer = new Div();
            videoContainer.setId("videoContainer");

            getElement().executeJs(
                    "const style = document.createElement('style');" +
                            "style.textContent = `" +
                            "  #videoContainer {" +
                            "    display: flex;" +
                            "    justify-content: center;" +
                            "    align-items: center;" +
                            "    flex-direction: column;" +
                            "    width: 100%; " +
                            "  }" +
                            "  #videoPlayer {" +
                            "    width: 90vw; " +
                            "    height: 80vh;" +
                            "    max-width: 100%; " +
                            "    max-height: 100%; " +
                            "  }" +
                            "  .custom-button {" +
                            "    margin: 10px;" +
                            "    padding: 10px 20px;" +
                            "    border: none;" +
                            "    background-color: #007bff;" +
                            "    color: white;" +
                            "    font-size: 16px;" +
                            "    border-radius: 5px;" +
                            "    cursor: pointer;" +
                            "    transition: background-color 0.3s ease;" +
                            "  }" +
                            "  .custom-button:hover {" +
                            "    background-color: #0056b3;" +
                            "  }" +
                            "`;" +
                            "document.head.appendChild(style);"
            );
            // === VIDEO SETUP ===
            Div videoWrapper = new Div();
            videoWrapper.getStyle().set("text-align", "center");

            Element videoElement = getVideoElement(videoId, videoSrc);

            videoWrapper.getElement().appendChild(videoElement);

            // === CONTROLS ===
            Div controls = new Div();
            controls.getStyle().set("margin-top", "10px");
            controls.getStyle().set("display", "flex");
            controls.getStyle().set("flex-direction", "column");
            controls.getStyle().set("align-items", "center");

            NumberField delayENElement = new NumberField("Subtitle Delay (EN) [s]");
            delayENElement.setId("subtitleDelayInputEN");
            delayENElement.setStep(0.5);
            delayENElement.setWidth("300px");
            delayENElement.setValue(enDelay);

            NumberField delayPLElement = new NumberField("Subtitle Delay (PL) [s]");
            delayPLElement.setId("subtitleDelayInputPL");
            delayPLElement.setStep(0.5);
            delayPLElement.setWidth("300px");
            delayPLElement.setValue(plDelay);

            controls.add(delayENElement, delayPLElement, new BervanButton("Shift (workaround for startup)", (e) -> {
                delayPLElement.setValue(plDelay);
                delayENElement.setValue(enDelay);
            }));

            add(videoWrapper, controls);

            // === JS LOGIC: Shift subtitle cues ===
            String shiftScript = """
                        window.shiftSubtitles = (lang, delay) => {
                          const video = document.getElementById('videoPlayer');
                          if (!video) return;
                          const tracks = video.textTracks;
                          for (let i = 0; i < tracks.length; i++) {
                            const t = tracks[i];
                            if (t.language === lang || t.label.toLowerCase() === lang) {
                              for (let j = 0; j < t.cues.length; j++) {
                                const cue = t.cues[j];
                                cue.startTime = Math.max(0, cue.startTime + delay);
                                cue.endTime = Math.max(0, cue.endTime + delay);
                              }
                            }
                          }
                        };
                    """;
            getElement().executeJs(shiftScript);

            // === React to value change ===
            delayENElement.addValueChangeListener(e -> {
                double val = e.getValue() != null ? e.getValue() : 0.0;
                getElement().executeJs("window.shiftSubtitles('en', $0)", val);
                saveActualDelay(videoId, val, plDelay);
            });

            delayPLElement.addValueChangeListener(e -> {
                double val = e.getValue() != null ? e.getValue() : 0.0;
                getElement().executeJs("window.shiftSubtitles('pl', $0)", val);
                saveActualDelay(videoId, enDelay, val);
            });

            double currentVideoTime = watchDetails.getCurrentVideoTime();

            getElement().executeJs(
                    "let videoPlayer = document.getElementById('videoPlayer');" +
                            " if (videoPlayer) {" +
                            "    videoPlayer.currentTime = $2;" +
                            " }" +

                            " window._videoPlayerKeyHandler = function(event) {" +
                            "    if (event.key === 'b') {" +
                            toggleSubtitles() +
                            "    } else if (event.key === ' ' || event.key === 'Spacebar') {" +
                            toggleStartStop() +
                            "    } else if (event.key === 'ArrowRight') {" +
                            plusTimeVideo() +
                            "    } else if (event.key === 'ArrowLeft') {" +
                            minusTimeVideo() +
                            "    } else if (event.key === 'f') {" +
                            toggleFullscreen() +
                            "    } else {return;}" +
                            " }; " +
                            " document.addEventListener('keydown', window._videoPlayerKeyHandler);  " +
                            "   " +
                            "if (videoPlayer) {" +
                            "    let lastSentTime = 0;" +
                            " window._videoPlayerInterval = setInterval(() => {" +
                            "        if (!isNaN(videoPlayer.currentTime) && Math.abs(videoPlayer.currentTime - lastSentTime) >= 5) {" +
                            "            lastSentTime = videoPlayer.currentTime;" +
                            "            $0.$server.saveWatchProgress($1, videoPlayer.currentTime);" +
                            "        }" +
                            "    }, 10000);" +
                            " } "
                    ,
                    getElement(),     // $0
                    videoId,          // $1
                    currentVideoTime // $2
            );
        } catch (Exception e) {
            log.error("Could not load video!", e);
            showErrorNotification("Could not load video!");
        }
    }

    private Element getVideoElement(String videoId, String videoSrc) {
        Element videoElement = new Element("video");
        videoElement.setAttribute("id", "videoPlayer");
        videoElement.setAttribute("controls", "");
        videoElement.setAttribute("playsinline", "");
        videoElement.setAttribute("preload", "auto");
        videoElement.setAttribute("width", "640");
        videoElement.setAttribute("height", "360");

        // Source
        Element source = new Element("source");
        source.setAttribute("src", videoSrc);
        source.setAttribute("type", "video/mp4");
        videoElement.appendChild(source);

        // Subtitle EN
        Element trackEN = new Element("track");
        trackEN.setAttribute("id", "trackEN");
        trackEN.setAttribute("kind", "subtitles");
        trackEN.setAttribute("src", "/storage/videos/subtitles/" + videoId + "/en");
        trackEN.setAttribute("srclang", "en");
        trackEN.setAttribute("label", "English");
        trackEN.setAttribute("default", "");

        // Subtitle PL
        Element trackPL = new Element("track");
        trackPL.setAttribute("id", "trackPL");
        trackPL.setAttribute("kind", "subtitles");
        trackPL.setAttribute("src", "/storage/videos/subtitles/" + videoId + "/pl");
        trackPL.setAttribute("srclang", "pl");
        trackPL.setAttribute("label", "Polish");

        videoElement.appendChild(trackEN);
        videoElement.appendChild(trackPL);
        return videoElement;
    }


    private String toggleFullscreen() {
        return " "
                + "if (!videoPlayer) return; "
                + "if(document.fullscreenElement === videoPlayer) {"
                + "  document.exitFullscreen(); "
                + "} else {"
                + "  if (videoPlayer.requestFullscreen) { "
                + "      videoPlayer.requestFullscreen(); "
                + "  } else if (videoPlayer.webkitRequestFullscreen) { /* Safari */ "
                + "      videoPlayer.webkitRequestFullscreen(); "
                + "  } else if ( videoPlayer.msRequestFullscreen) { /* IE11 */ "
                + "      videoPlayer.msRequestFullscreen(); "
                + "  }"
                + "}";
    }

    private String plusTimeVideo() {
        return " "
                + "if (!videoPlayer) return; "
                + "videoPlayer.currentTime += 5;";
    }

    private String minusTimeVideo() {
        return " "
                + "if (!videoPlayer) return; "
                + "videoPlayer.currentTime -= 5;";
    }

    private String toggleStartStop() {
        return " "
                + "if (videoPlayer.paused) {"
                + "  videoPlayer.play();"
                + "} else {"
                + "  videoPlayer.pause();"
                + "}";
    }

    private String toggleSubtitles() {
        return " "
                + "if (videoPlayer.textTracks.length < 2) return;"
                + "if (videoPlayer.textTracks[0].mode === 'hidden') {"
                + "  videoPlayer.textTracks[0].mode = 'showing'; "
                + "  videoPlayer.textTracks[1].mode = 'hidden'; "
                + "} else {"
                + "  videoPlayer.textTracks[0].mode = 'hidden';"
                + "  videoPlayer.textTracks[1].mode = 'showing';"
                + "}";
    }

    @ClientCallable
    public void saveWatchProgress(String videoId, double lastWatchedTime) {
        WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(
                AuthService.getLoggedUserId().toString(), videoId
        );
        videoManager.saveWatchProgress(watchDetails, lastWatchedTime);
    }

    @ClientCallable
    public void saveActualDelay(String videoId, double enDelay, double plDelay) {
        WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(
                AuthService.getLoggedUserId().toString(), videoId
        );
        videoManager.saveSubtitleDelays(watchDetails, enDelay, plDelay);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // Remove keyboard listener and interval on detach
        getElement().removeAllChildren();
        getElement().executeJs(
                "if (window._videoPlayerKeyHandler) {" +
                        "  document.removeEventListener('keydown', window._videoPlayerKeyHandler);" +
                        "  window._videoPlayerKeyHandler = null;" +
                        "}" +
                        "if (window._videoPlayerInterval) {" +
                        "  clearInterval(window._videoPlayerInterval);" +
                        "  window._videoPlayerInterval = null;" +
                        "}"
        );
    }
}