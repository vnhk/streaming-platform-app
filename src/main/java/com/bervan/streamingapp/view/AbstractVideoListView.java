package com.bervan.streamingapp.view;

import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.io.File;
import java.util.List;

@Route(AbstractVideoListView.ROUTE_NAME)
public abstract class AbstractVideoListView extends AbstractStreamingPage {
    public static final String ROUTE_NAME = "/streaming-platform";
    private final BervanLogger logger;
    private final VideoManager videoManager;

    public AbstractVideoListView(BervanLogger logger, VideoManager videoManager) {
        super(ROUTE_NAME);
        this.logger = logger;
        this.videoManager = videoManager;

        HorizontalLayout groupedVideosLayout = groupedVideos(logger, videoManager);
        HorizontalLayout allVideosLayout = allVideos(logger, videoManager);

        add(new H3("Movies/TV Series:"), groupedVideosLayout, new Hr(), new H3("All Videos"), allVideosLayout);
    }

    private HorizontalLayout groupedVideos(BervanLogger logger, VideoManager videoManager) {
        HorizontalLayout scrollingLayout = new HorizontalLayout();
        scrollingLayout.getStyle()
                .set("overflow-x", "auto")
                .set("white-space", "nowrap")
                .set("padding", "10px");

        List<Metadata> videosMainDirectories = videoManager.loadVideosMainDirectories();
        for (Metadata mainDirectory : videosMainDirectories) {
            try {
                VerticalLayout tile = getTile();
                // Add poster image
                String imageSrc = "/storage/videos/poster/" + mainDirectory.getId();
                Image image = getImage(mainDirectory.getFilename(), imageSrc);

                // Add title/description
                H3 title = getTitle(mainDirectory.getFilename());

                tile.add(image, title);

                // Add click listener for navigation
                tile.addClickListener(click ->
                        UI.getCurrent().navigate(ROUTE_NAME + "/details/" + mainDirectory.getId())
                );
                scrollingLayout.add(tile);
            } catch (Exception e) {
                logger.error("Unable to load video!", e);
                showErrorNotification("Unable to load video!");
            }

        }
        return scrollingLayout;
    }

    private HorizontalLayout allVideos(BervanLogger logger, VideoManager videoManager) {
        HorizontalLayout scrollingLayout = new HorizontalLayout();
        scrollingLayout.getStyle()
                .set("overflow-x", "auto")
                .set("white-space", "nowrap")
                .set("padding", "10px");

        List<Metadata> videos = videoManager.loadVideos();
        for (Metadata video : videos) {
            try {
                Metadata mainDirectory = videoManager.getMainVideoFolder(video);

                String imageSrc = "";
                String altText = "Not found";
                String titleText = video.getFilename();

                if (mainDirectory == null) {
                    logger.warn("Video without main directory: " + video.getPath() + File.separator + video.getFilename());
                } else {
                    altText = mainDirectory.getFilename();
                    imageSrc = "/storage/videos/poster/" + mainDirectory.getId();
                    titleText = mainDirectory.getFilename();
                }

                VerticalLayout tile = getTile();
                Image image = getImage(altText, imageSrc);

                // Add title/description
                H3 title = getTitle(titleText);

                tile.add(image, title);

                // Add click listener for navigation
                tile.addClickListener(click ->
                        UI.getCurrent().navigate(ROUTE_NAME + "/video-player/" + video.getId())
                );
                scrollingLayout.add(tile);
            } catch (Exception e) {
                logger.error("Unable to load video!", e);
                showErrorNotification("Unable to load video!");
            }

        }
        return scrollingLayout;
    }

    private static H3 getTitle(String value) {
        H3 title = new H3(value);
        title.getStyle()
                .set("text-align", "center")
                .set("margin-top", "10px");
        return title;
    }

    private static Image getImage(String text, String imageSrc) {
        Image image = new Image(imageSrc, text);
        image.setWidth("100%");
        image.setHeight("70%");
        image.getStyle().set("object-fit", "cover");
        return image;
    }

    private static VerticalLayout getTile() {
        VerticalLayout tile = new VerticalLayout();
        tile.getStyle()
                .set("margin", "10px")
                .set("cursor", "pointer")
                .set("display", "inline-block")
                .set("width", "400px")
                .set("height", "600px")
                .set("background", "#f3f3f3")
                .set("border-radius", "8px")
                .set("overflow", "hidden")
                .set("box-shadow", "0px 4px 10px rgba(0, 0, 0, 0.1)");
        return tile;
    }
}