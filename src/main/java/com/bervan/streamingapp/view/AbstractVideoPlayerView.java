package com.bervan.streamingapp.view;

import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
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

    private void init(String videoFolderId) {
        try {
            List<Metadata> video = videoManager.loadById(videoFolderId);

            if (video.size() != 1) {
                logger.error("Could not find video based on provided id!");
                showErrorNotification("Could not find video!");
                return;
            }

            Metadata mainDirectory = videoManager.getMainVideoFolder(video.get(0));

            if(mainDirectory != null) {
                Button detailsButton = new Button("Details");
                detailsButton.addClassName("option-button");
                detailsButton.addClickListener(click -> {
                    UI.getCurrent().navigate("/streaming-platform/details/" + mainDirectory.getId());
                });
                add(detailsButton);
            }

            String videoSrc = "/storage/videos/video/" + videoFolderId;

            setSizeFull();
            setSpacing(false);
            setPadding(true);

            // Title
            Div title = new Div();
            title.setText("Advanced Video Player");
            title.getStyle()
                    .set("font-size", "24px")
                    .set("font-weight", "bold");
            add(title);

            // Create a Div to contain the video player
            Div videoContainer = new Div();
            videoContainer.getStyle()
                    .set("display", "flex")
                    .set("justify-content", "center")
                    .set("margin-top", "20px");

            // Add HTML <video> element as a child
            videoContainer.getElement().setProperty("innerHTML",
                    "<video id='videoPlayer' width='800' height='450' controls>" +
                            "  <source src='" + videoSrc + "' type='video/mp4'>" +
                            "</video>");
            add(videoContainer);

            // Add custom controls if needed
            NativeButton playButton = new NativeButton("Play", e -> {
                getElement().executeJs("document.getElementById('videoPlayer').play()");
            });

            NativeButton pauseButton = new NativeButton("Pause", e -> {
                getElement().executeJs("document.getElementById('videoPlayer').pause()");
            });

            add(playButton, pauseButton);

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
}