package com.bervan.canvasapp.view;

import com.bervan.canvasapp.VideoManager;
import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.util.List;
import java.util.Map;

@Route(AbstractVideoListView.ROUTE_NAME)
public abstract class AbstractVideoListView extends AbstractStreamingPage {
    public static final String ROUTE_NAME = "/streaming-platform";
    private final BervanLogger logger;
    private final VideoManager videoManager;

    public AbstractVideoListView(BervanLogger logger, VideoManager videoManager) {
        super(ROUTE_NAME);
        this.logger = logger;
        this.videoManager = videoManager;

        HorizontalLayout scrollingLayout = new HorizontalLayout();
        scrollingLayout.getStyle()
                .set("overflow-x", "auto")
                .set("white-space", "nowrap")
                .set("padding", "10px");

        List<Metadata> videosDirectories = videoManager.loadVideosDirectories();
        for (Metadata directory : videosDirectories) {
            try {
                Map<String, Metadata> files = videoManager.loadVideoDirectory(directory);

//                Metadata poster = files.get("POSTER"); // POSTER file (e.g., png/jpg)
//                Metadata properties = files.get("PROPERTIES"); // PROPERTIES file (e.g., json)

                // Create tile
                VerticalLayout tile = new VerticalLayout();
                tile.getStyle()
                        .set("margin", "10px")
                        .set("cursor", "pointer")
                        .set("display", "inline-block")
                        .set("width", "200px")
                        .set("height", "300px")
                        .set("background", "#f3f3f3")
                        .set("border-radius", "8px")
                        .set("overflow", "hidden")
                        .set("box-shadow", "0px 4px 10px rgba(0, 0, 0, 0.1)");

                // Add poster image
                String imageSrc = "/storage/videos/poster/" + directory.getId();

                Image image = new Image(imageSrc, directory.getFilename());
                image.setWidth("100%");
                image.setHeight("70%");
                image.getStyle().set("object-fit", "cover");

                // Add title/description
                H3 title = new H3(directory.getFilename());
                title.getStyle()
                        .set("text-align", "center")
                        .set("margin-top", "10px");

                tile.add(image, title);

                // Add click listener for navigation
                tile.addClickListener(click ->
                        UI.getCurrent().navigate(ROUTE_NAME + "/video-player/" + directory.getId())
                );

                scrollingLayout.add(tile);
            } catch (Exception e) {
                logger.error("Unable to load video!", e);
                showErrorNotification("Unable to load video!");
            }

        }

        add(scrollingLayout);
    }
}