package com.bervan.streamingapp.view;

import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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

            if(mainDirectory != null) {
                Button detailsButton = new Button(mainDirectory.getFilename() + " - Details");
                detailsButton.addClassName("option-button");
                detailsButton.addClickListener(click -> {
                    UI.getCurrent().navigate("/streaming-platform/details/" + mainDirectory.getId());
                });
                add(detailsButton);
            }

            String videoSrc = "/storage/videos/video/" + videoId;

            setSizeFull();
            setSpacing(false);
            setPadding(true);

            // Create a Div to contain the video player
            Div videoContainer = new Div();

            // Add custom controls if needed
            NativeButton playButton = new NativeButton("Play", e -> {
                getElement().executeJs("document.getElementById('videoPlayer').play()");
            });

            playButton.addClassName("custom-button");

            NativeButton pauseButton = new NativeButton("Pause", e -> {
                getElement().executeJs("document.getElementById('videoPlayer').pause()");
            });

            pauseButton.addClassName("custom-button");

            // Add CSS styles directly into the page
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

            // HTML Video Player
            videoContainer.getElement().setProperty(
                    "innerHTML",
                    "<div id='videoContainer'>" +
                            "  <video id='videoPlayer' controls>" +
                            "    <source src='" + videoSrc + "' type='video/mp4'>" +
                            "    <track id='trackEN' kind='subtitles' src='/storage/videos/subtitles/" + videoId + "/en' srclang='en' label='English' default>" +
                            "    <track id='trackPL' kind='subtitles' src='/storage/videos/subtitles/" + videoId + "/pl' srclang='pl' label='Polish'>" +
                            "  </video>" +
                            "</div>"
            );

            NativeButton toggleSubtitlesButton = new NativeButton("Toggle Subtitles", e -> {
                getElement().executeJs(
                        toggleSubtitles()
                );
            });
            toggleSubtitlesButton.addClassName("custom-button");

            add(videoContainer);
            add(new HorizontalLayout(playButton, pauseButton, toggleSubtitlesButton));

            // Keyboard shortcut "B" for toggling subtitles
            getElement().executeJs(
                    "document.addEventListener('keydown', function(event) {" +
                            "  if (event.key === 'b') {" +
                            toggleSubtitles() +
                            "  }" +
                            "  if (event.key === 'Spacebar') {" +
                            toggleStartStop() +
                            "  }" +
                            "});"
            );


            // JavaScript for keyboard event handling
            getElement().executeJs(
                    "document.addEventListener('keydown', function(event) {" +
                            "  const video = document.getElementById('videoPlayer');" +
                            "  if (!video) return;" +
                            "  switch (event.key) {" +
                            "    case 'ArrowRight':" +
                            "      video.currentTime += 5;" +
                            "      break;" +
                            "    case 'ArrowLeft':" +
                            "      video.currentTime -= 5;" +
                            "      break;" +
                            "  }" +
                            "});"
            );
        } catch (Exception e) {
            logger.error("Could not load video!", e);
            showErrorNotification("Could not load video!");
        }
    }

    private String toggleStartStop() {
        return "  var videoPlayer = document.getElementById('videoPlayer'); " +
                "    if (videoPlayer.paused) { " +
                "        videoPlayer.play(); " +
                "    } else { " +
                "        videoPlayer.pause(); " +
                "    }";
    }

    private static String toggleSubtitles() {
        return "    var video = document.getElementById('videoPlayer'); " +
                "   if(video.textTracks[0].mode == 'hidden') { " +
                "          video.textTracks[0].mode = 'showing'; " +
                "          video.textTracks[1].mode = 'hidden'; " +
                "   } else { " +
                "          video.textTracks[0].mode = 'hidden'; " +
                "          video.textTracks[1].mode = 'showing'; " +
                "   } ";
    }
}