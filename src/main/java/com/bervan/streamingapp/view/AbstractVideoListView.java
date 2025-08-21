package com.bervan.streamingapp.view;

import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.util.List;

@Route(AbstractVideoListView.ROUTE_NAME)
public abstract class AbstractVideoListView extends AbstractStreamingPage {
    public static final String ROUTE_NAME = "/streaming-platform";
    private final BervanLogger logger;
    private final VideoManager videoManager;

    public AbstractVideoListView(BervanLogger logger, VideoManager videoManager) {
        super(ROUTE_NAME, AbstractVideoPlayerView.ROUTE_NAME, AbstractVideoDetailsView.ROUTE_NAME);
        this.logger = logger;
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
                logger.error("Unable to load video!", e);
                showErrorNotification("Unable to load video!");
            }
        }

        return scrollingLayout;
    }
}