package com.bervan.streamingapp.view;

import com.bervan.common.service.AuthService;
import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.WatchDetails;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;

import java.util.List;
import java.util.UUID;

public abstract class AbstractVideoPlayerView extends AbstractStreamingPage implements HasUrlParameter<String> {
    public static final String ROUTE_NAME = "/streaming-platform/video-player";
    private final BervanLogger logger;
    private final VideoManager videoManager;

    public AbstractVideoPlayerView(BervanLogger logger, VideoManager videoManager) {
        super(ROUTE_NAME, AbstractVideoDetailsView.ROUTE_NAME);
        this.logger = logger;
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
                logger.error("Could not find video based on provided id!");
                showErrorNotification("Could not find video!");
                return;
            }

            Metadata mainDirectory = videoManager.getMainMovieFolder(video.get(0));

            if (mainDirectory != null) {
                Button detailsButton = new Button(mainDirectory.getFilename() + " - Details");
                detailsButton.addClassName("option-button");
                detailsButton.addClickListener(click ->
                        UI.getCurrent().navigate("/streaming-platform/details/" + mainDirectory.getId())
                );
                add(detailsButton);
            }

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

            videoContainer.getElement().setProperty(
                    "innerHTML",
                    "<div id='videoContainer'>" +
                            "  <video id='videoPlayer' controls playsinline>" +
                            "    <source src='" + videoSrc + "' type='video/mp4'>" +
                            "    <track id='trackEN' kind='subtitles' src='/storage/videos/subtitles/" + videoId + "/en' srclang='en' label='English' default>" +
                            "    <track id='trackPL' kind='subtitles' src='/storage/videos/subtitles/" + videoId + "/pl' srclang='pl' label='Polish'>" +
                            "  </video>" +
                            "  <div style='margin-top: 10px; display: flex; flex-direction: column; align-items: center;'>" +
                            "    <label for='subtitleDelayInputEN'>Subtitle Delay (EN) [s]:</label>" +
                            "    <input id='subtitleDelayInputEN' type='number' step='0.5' style='width: 100px; text-align: center;'/>" +
                            "    <label for='subtitleDelayInputPL'>Subtitle Delay (PL) [s]:</label>" +
                            "    <input id='subtitleDelayInputPL' type='number' step='0.5' style='width: 100px; text-align: center;'/>" +
                            "  </div>" +
                            "</div>"
            );

            add(videoContainer);

            double currentVideoTime = watchDetails.getCurrentVideoTime();

            getElement().executeJs(
                    "let videoPlayer = document.getElementById('videoPlayer');" +
                            "if (videoPlayer) {" +
                            "    videoPlayer.currentTime = $2;" +
                            "}" +
                            "document.getElementById('subtitleDelayInputEN').value = $3;" +
                            "document.getElementById('subtitleDelayInputPL').value = $4;" +

                            "document.addEventListener('keydown', function(event) {" +
                            "     " +
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
                            " event.preventDefault(); " +
                            "});" +

                            "if (videoPlayer) {" +
                            "    let lastSentTime = 0;" +
                            "    setInterval(() => {" +
                            "        if (!isNaN(videoPlayer.currentTime) && Math.abs(videoPlayer.currentTime - lastSentTime) >= 5) {" +
                            "            lastSentTime = videoPlayer.currentTime;" +
                            "            $0.$server.saveWatchProgress($1, videoPlayer.currentTime);" +
                            "        }" +
                            "    }, 10000);" +
                            "}" +

                            "let enDelay = $3;" +
                            "let plDelay = $4;" +

                            "function adjustSubtitleTiming(track, delay) {" +
                            "    if (!track || !track.cues) return;" +
                            "    for (let i = 0; i < track.cues.length; i++) {" +
                            "        const cue = track.cues[i];" +
                            "        cue.startTime += delay;" +
                            "        cue.endTime += delay;" +
                            "    }" +
                            "}" +

                            "document.getElementById('subtitleDelayInputEN').addEventListener('input', function(event) {" +
                            "    const textTracks = videoPlayer.textTracks;" +
                            "    const newEn = parseFloat(event.target.value) || 0;" +
                            "    let diffEn = newEn - enDelay;" +
                            "    enDelay = newEn;" +
                            "    for (let i = 0; i < textTracks.length; i++) {" +
                            "        if (textTracks[i].language === 'en' || textTracks[i].label.toLowerCase() === 'english') {" +
                            "            adjustSubtitleTiming(textTracks[i], diffEn);" +
                            "        }" +
                            "    }" +
                            "    $0.$server.saveActualDelay($1, enDelay, plDelay);" +
                            "});" +

                            "document.getElementById('subtitleDelayInputPL').addEventListener('input', function(event) {" +
                            "    const textTracks = videoPlayer.textTracks;" +
                            "    const newPl = parseFloat(event.target.value) || 0;" +
                            "    let diffPl = newPl - plDelay;" +
                            "    plDelay = newPl;" +
                            "    for (let i = 0; i < textTracks.length; i++) {" +
                            "        if (textTracks[i].language === 'pl' || textTracks[i].label.toLowerCase() === 'polish') {" +
                            "            adjustSubtitleTiming(textTracks[i], diffPl);" +
                            "        }" +
                            "    }" +
                            "    $0.$server.saveActualDelay($1, enDelay, plDelay);" +
                            "});"
                    ,
                    getElement(),     // $0
                    videoId,          // $1
                    currentVideoTime, // $2
                    enDelay,          // $3
                    plDelay           // $4
            );
        } catch (Exception e) {
            logger.error("Could not load video!", e);
            showErrorNotification("Could not load video!");
        }
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
        logger.info("videoId + lastWatchedTime: " + videoId + "=" + lastWatchedTime);
        WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(
                AuthService.getLoggedUserId().toString(), videoId
        );
        videoManager.saveWatchProgress(watchDetails, lastWatchedTime);
    }

    @ClientCallable
    public void saveActualDelay(String videoId, double enDelay, double plDelay) {
        logger.info("Saving subtitle delays: EN=" + enDelay + ", PL=" + plDelay + " for videoId=" + videoId);
        WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(
                AuthService.getLoggedUserId().toString(), videoId
        );
        videoManager.saveSubtitleDelays(watchDetails, enDelay, plDelay);
    }
}