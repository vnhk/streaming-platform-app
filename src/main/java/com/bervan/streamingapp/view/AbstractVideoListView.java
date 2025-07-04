package com.bervan.streamingapp.view;

import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
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

        VerticalLayout groupedVideosLayout = createScrollableSection("Movies/TV Series:", groupedVideos());

        //its time-consuming
        //        VerticalLayout allVideosLayout = createScrollableSection("All Videos", allVideos());

//        add(groupedVideosLayout, new Hr(), allVideosLayout);
        add(groupedVideosLayout, new Hr());
    }

    private VerticalLayout createScrollableSection(String title, HorizontalLayout contentLayout) {
        VerticalLayout section = new VerticalLayout();
        section.add(new H3(title));
        section.setWidth("95vw");

        HorizontalLayout container = new HorizontalLayout();
        container.setWidthFull();
        container.setAlignItems(Alignment.CENTER);

        Button leftArrow = new Button("<");
        leftArrow.addClassName("option-button");
        leftArrow.addClickListener(event -> contentLayout.getElement().executeJs("this.scrollBy({left: -345, behavior: 'smooth'})"));

        Button rightArrow = new Button(">");
        rightArrow.addClassName("option-button");
        rightArrow.addClickListener(event -> contentLayout.getElement().executeJs("this.scrollBy({left: 345, behavior: 'smooth'})"));

        container.add(leftArrow, contentLayout, rightArrow);
        container.setFlexGrow(1, contentLayout);

        section.add(container);
        return section;
    }

    private HorizontalLayout groupedVideos() {
        return createVideoLayout(videoManager.loadVideosMainDirectories(), ROUTE_NAME + "/details/");
    }

//    private HorizontalLayout allVideos() {
//        return createVideoLayout(videoManager.loadVideos(), ROUTE_NAME + "/video-player/");
//    }

    private HorizontalLayout createVideoLayout(List<Metadata> videos, String route) {
        HorizontalLayout scrollingLayout = new HorizontalLayout();
        scrollingLayout.getStyle()
                .set("overflow-x", "hidden")
                .set("white-space", "nowrap")
                .set("padding", "10px");

        for (Metadata video : videos) {
            try {
                VerticalLayout tile = getTile();
                String imageSrc = "/storage/videos/poster/" + video.getId();
                Image image = getImage(video.getFilename(), imageSrc);
                H4 title = getTitle(video.getFilename());

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

    private H4 getTitle(String value) {
        H4 title = new H4(value);
        title.getStyle()
                .set("text-align", "center")
                .set("margin-top", "10px");
        return title;
    }

    private Image getImage(String text, String imageSrc) {
        String fallbackImageSrc = "images/no_video_image.png";
        Image image = new Image(imageSrc, text);
        image.setWidth("100%");
        image.setHeight("70%");
        image.getStyle().set("object-fit", "cover");

        image.getElement().executeJs(
                "this.onerror = function() { this.onerror = null; this.src = $0; }", fallbackImageSrc
        );

        return image;
    }

    private VerticalLayout getTile() {
        VerticalLayout tile = new VerticalLayout();
        tile.addClassName("movie-tile");
        tile.getStyle()
                .set("margin", "10px")
                .set("cursor", "pointer")
                .set("display", "inline-block")
                .set("min-width", "320px")
                .set("width", "320px")
                .set("height", "440px")
                .set("border-radius", "8px")
                .set("overflow", "hidden")
                .set("box-shadow", "0px 4px 10px rgba(0, 0, 0, 0.1)");
        return tile;
    }
}