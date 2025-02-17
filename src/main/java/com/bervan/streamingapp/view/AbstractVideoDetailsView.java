package com.bervan.streamingapp.view;

import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractVideoDetailsView extends AbstractStreamingPage implements HasUrlParameter<String> {
    public static final String ROUTE_NAME = "/streaming-platform/details";
    private final BervanLogger logger;
    private final VideoManager videoManager;

    public AbstractVideoDetailsView(BervanLogger logger, VideoManager videoManager) {
        super(ROUTE_NAME, AbstractVideoPlayerView.ROUTE_NAME);
        this.logger = logger;
        this.videoManager = videoManager;
    }

    @Override
    public void setParameter(BeforeEvent event, String s) {
        String videoId = event.getRouteParameters().get("___url_parameter").orElse(UUID.randomUUID().toString());
        init(videoId);
    }

    protected void init(String videoFolderId) {
        try {
            // Load the root folder
            List<Metadata> directory = videoManager.loadById(videoFolderId);

            if (directory.size() != 1) {
                logger.error("Could not find video based on provided id!");
                showErrorNotification("Could not find details!");
                return;
            }

            Metadata rootFolder = directory.get(0);

            String imageSrc = "/storage/videos/poster/" + rootFolder.getId();

            Image image = new Image(imageSrc, rootFolder.getFilename());
            image.setWidth("200px");
            image.setHeight("70%");
            image.getStyle().set("object-fit", "cover");
            String fallbackImageSrc = "images/no_video_image.png";
            image.getElement().executeJs(
                    "this.onerror = function() { this.onerror = null; this.src = $0; }", fallbackImageSrc
            );

            H3 title = new H3(rootFolder.getFilename());
            Div details = buildDetails(rootFolder);
            add(image, title, details);
        } catch (Exception e) {
            logger.error("Could not load details!", e);
            showErrorNotification("Could not load details!");
        }
    }

    private Div buildDetails(Metadata root) {
        Div result = new Div();

        Map<String, List<Metadata>> rootContent = videoManager.loadVideoDirectoryContent(root);
        List<Metadata> defaultPoster = rootContent.get("POSTER");
        List<Metadata> directories = rootContent.get("DIRECTORY");
        if (directories != null) {
            for (Metadata directory : directories) {
                if (directory.getFilename().startsWith("Season")) {
                    result.add(createScrollableSection(directory.getFilename(), seasonVideosLayout(directory, defaultPoster)));
                    result.add(new Hr());
                }
            }
        }

        List<Metadata> videos = rootContent.get("VIDEO");
        if (videos != null && videos.size() > 0) {
            result.add(createScrollableSection("Video:", allVideos(rootContent, defaultPoster)));
        }

        return result;
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

    private HorizontalLayout seasonVideosLayout(Metadata seasonDirectory, List<Metadata> defaultPoster) {
        Map<String, List<Metadata>> stringListMap = videoManager.loadVideoDirectoryContent(seasonDirectory);
        List<Metadata> allVideosInSeason = stringListMap.entrySet().stream().filter(e -> e.getKey().equals("DIRECTORY"))
                .map(Map.Entry::getValue).flatMap(Collection::stream).filter(e -> e.getFilename().startsWith("Ep"))
                .toList();

        int episodes = allVideosInSeason.size();
        List<Map<String, List<Metadata>>> sortedEpisodesFolders = new ArrayList<>();

        for (int i = 1; i <= episodes; i++) {
            String pattern = "(?:Ep(?:isode)?\\s?)" + i + "(?![0-9a-zA-Z])";
            Pattern regex = Pattern.compile(pattern);
            for (Metadata metadata : allVideosInSeason) {
                Matcher matcher = regex.matcher(metadata.getFilename());
                if (matcher.find()) {
                    Map<String, List<Metadata>> videoFolder = videoManager.loadVideoDirectoryContent(metadata);
                    List<Metadata> video = videoFolder.get("VIDEO");
                    if (video != null && !video.isEmpty()) {
                        sortedEpisodesFolders.add(videoFolder);
                    } else {
                        logger.error("Episode folder is empty! " + metadata.getPath() + File.separator + metadata.getFilename());
                    }
                }
            }
        }

        return createVideoLayout(sortedEpisodesFolders, defaultPoster, true);
    }

    private HorizontalLayout allVideos(Map<String, List<Metadata>> videos, List<Metadata> defaultPoster) {
        return createVideoLayout(Collections.singletonList(videos), defaultPoster, false);
    }

    private HorizontalLayout createVideoLayout(List<Map<String, List<Metadata>>> videosFolders, List<Metadata> defaultPoster, boolean useEpisodeGeneratedName) {
        HorizontalLayout scrollingLayout = new HorizontalLayout();
        scrollingLayout.getStyle()
                .set("overflow-x", "hidden")
                .set("white-space", "nowrap")
                .set("padding", "10px");

        int videoCounter = 1;
        for (Map<String, List<Metadata>> videoFolder : videosFolders) {
            List<Metadata> videos = videoFolder.get("VIDEO");
            for (Metadata video : videos) {
                try {
                    VerticalLayout tile = getTile();
                    String imageSrc;
                    if (videoFolder.get("POSTER") == null || videoFolder.get("POSTER").size() == 0) {
                        imageSrc = "/storage/videos/poster/" + video.getId();
                    } else {
                        imageSrc = "/storage/videos/poster/direct/" + videoFolder.get("POSTER").get(0).getId();
                    }
                    Image image = getImage(video.getFilename(), imageSrc, defaultPoster);

                    H4 title;
                    if (useEpisodeGeneratedName) {
                        title = getTitle("Episode " + videoCounter);
                    } else {
                        title = getTitle(video.getFilename());
                    }

                    tile.add(image, title);
                    tile.addClickListener(click ->
                            UI.getCurrent().navigate("/streaming-platform/video-player/" + video.getId())
                    );
                    scrollingLayout.add(tile);
                } catch (Exception e) {
                    logger.error("Unable to load video!", e);
                    showErrorNotification("Unable to load video!");
                }
                videoCounter++;
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

    private Image getImage(String text, String imageSrc, List<Metadata> defaultPoster) {
        String fallbackImageSrc;
        if (defaultPoster != null && defaultPoster.size() > 0) {
            fallbackImageSrc = "/storage/videos/poster/direct/" + defaultPoster.get(0).getId();
        } else {
            fallbackImageSrc = "images/no_video_image.png";
        }
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