package com.bervan.streamingapp.view;

import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;

public abstract class AbstractVideoListView extends AbstractRemoteControlSupportedView {
    public static final String ROUTE_NAME = "/streaming-platform";
    private final JsonLogger log = JsonLogger.getLogger(getClass());
    private final VideoManager videoManager;

    public AbstractVideoListView(VideoManager videoManager) {
        super(ROUTE_NAME, AbstractVideoPlayerView.ROUTE_NAME, AbstractVideoDetailsView.ROUTE_NAME);
        this.videoManager = videoManager;

        Div scrollableLayoutParent = getScrollableLayoutParent();
        scrollableLayoutParent.add(createModernScrollableSection("Movies/TV Series:", groupedVideos()));
        add(scrollableLayoutParent, new Hr());
    }

    private HorizontalLayout groupedVideos() {
        return createVideoLayout(videoManager.loadVideosMainDirectories(), ROUTE_NAME + "/details/");
    }

    private HorizontalLayout createVideoLayout(List<Metadata> videos, String route) {
        HorizontalLayout scrollingLayout = getHorizontalScrollingLayout();

        for (Metadata video : videos) {
            try {
                VerticalLayout tile = getModernTile();
                String imageSrc = "/storage/videos/poster/" + video.getId();
                Image image = getModernImage(video.getFilename(), imageSrc, null);
                H4 title = getModernTitle(video.getFilename());

                tile.add(image, title);
                tile.addClickListener(click ->
                        UI.getCurrent().navigate(route + video.getId())
                );
                scrollingLayout.add(tile);
            } catch (Exception e) {
                log.error("Unable to load video!", e);
                showErrorNotification("Unable to load video!");
            }
        }

        return scrollingLayout;
    }
}