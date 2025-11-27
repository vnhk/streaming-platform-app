package com.bervan.streamingapp.view;

import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class AbstractVideoDetailsView extends AbstractStreamingPage implements HasUrlParameter<String> {
    public static final String ROUTE_NAME = "/streaming-platform/details";
    private final VideoManager videoManager;

    public AbstractVideoDetailsView( VideoManager videoManager) {
        super(ROUTE_NAME, AbstractVideoPlayerView.ROUTE_NAME);
        this.videoManager = videoManager;
        setupViewStyles();
    }

    private void setupViewStyles() {
        getStyle()
                .set("background", "linear-gradient(135deg, var(--streaming-tile-background), var(--streaming-tile-background))")
                .set("min-height", "100vh")
                .set("padding", "0")
                .set("margin", "0");
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
                log.error("Could not find video based on provided id!");
                showErrorNotification("Could not find details!");
                return;
            }

            Metadata rootFolder = directory.get(0);

            // Create hero section
            VerticalLayout heroSection = createHeroSection(rootFolder);

            // Create content section
            Div contentSection = buildDetails(rootFolder);

            add(heroSection, contentSection);
        } catch (Exception e) {
            log.error("Could not load details!", e);
            showErrorNotification("Could not load details!");
        }
    }

    private VerticalLayout createHeroSection(Metadata rootFolder) {
        VerticalLayout heroSection = new VerticalLayout();
        heroSection.setWidthFull();
        heroSection.setAlignItems(FlexComponent.Alignment.CENTER);
        heroSection.getStyle()
                .set("background", "linear-gradient(var(--streaming-overlay-background), var(--streaming-overlay-background)), url('/storage/videos/poster/" + rootFolder.getId() + "')")
                .set("background-size", "cover")
                .set("background-position", "center")
                .set("background-attachment", "fixed")
                .set("color", "white")
                .set("padding", "60px 20px")
                .set("text-align", "center")
                .set("position", "relative");

        HorizontalLayout heroContent = new HorizontalLayout();
        heroContent.setAlignItems(FlexComponent.Alignment.START);
        heroContent.setWidthFull();
        heroContent.setMaxWidth("1200px");
        heroContent.getStyle().set("gap", "40px");

        // Poster image
        String imageSrc = "/storage/videos/poster/" + rootFolder.getId();
        Image posterImage = new Image(imageSrc, rootFolder.getFilename());
        posterImage.setWidth("300px");
        posterImage.setHeight("450px");
        posterImage.getStyle()
                .set("object-fit", "cover")
                .set("border-radius", "12px")
                .set("box-shadow", "0 20px 40px var(--streaming-tile-shadow)")
                .set("transition", "transform 0.3s ease");

        String fallbackImageSrc = "images/no_video_image.png";
        posterImage.getElement().executeJs(
                "this.onerror = function() { this.onerror = null; this.src = $0; }", fallbackImageSrc
        );

        // Content info
        VerticalLayout infoSection = new VerticalLayout();
        infoSection.setSpacing(false);
        infoSection.setPadding(false);
        infoSection.setAlignItems(FlexComponent.Alignment.START);

        // Title
        H1 title = new H1(rootFolder.getFilename());
        title.getStyle()
                .set("margin", "0 0 20px 0")
                .set("font-size", "3.5rem")
                .set("font-weight", "700")
                .set("text-shadow", "2px 2px 4px var(--streaming-title-shadow)")
                .set("line-height", "1.2");

        Paragraph description = new Paragraph(getDescription(rootFolder.getFilename()));
        description.getStyle()
                .set("font-size", "1.2rem")
                .set("line-height", "1.6")
                .set("margin", "0 0 30px 0")
                .set("max-width", "600px")
                .set("opacity", "0.9");

        // Action buttons
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setSpacing(true);

        Button playButton = new Button("Play Now", new Icon(VaadinIcon.PLAY));
        playButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        playButton.getStyle()
                .set("background", "linear-gradient(45deg, var(--streaming-play-icon-color), var(--streaming-play-icon-color))")
                .set("border", "none")
                .set("padding", "15px 30px")
                .set("font-size", "1.1rem")
                .set("border-radius", "25px")
                .set("box-shadow", "0 4px 15px var(--streaming-tile-shadow-hover)")
                .set("transition", "all 0.3s ease");

        Button editDetails = new Button("Edit", new Icon(VaadinIcon.EDIT));
        editDetails.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_LARGE);
        editDetails.getStyle()
                .set("background", "rgba(255,255,255,0.1)")
                .set("border", "2px solid var(--streaming-tile-border)")
                .set("color", "white")
                .set("padding", "15px 30px")
                .set("font-size", "1.1rem")
                .set("border-radius", "25px")
                .set("backdrop-filter", "blur(10px)")
                .set("transition", "all 0.3s ease");

        // Find first video to play
        Map<String, List<Metadata>> rootContent = videoManager.loadVideoDirectoryContent(rootFolder);
        String firstVideoId = findFirstVideoOrLatestVideoWatchedByUsed(rootContent);

        if (firstVideoId != null) {
            playButton.addClickListener(click ->
                    UI.getCurrent().navigate("/streaming-platform/video-player/" + firstVideoId)
            );
        } else {
            playButton.setEnabled(false);
        }

        buttonLayout.add(playButton, editDetails);

        infoSection.add(title, description, buttonLayout);
        heroContent.add(posterImage, infoSection);
        heroContent.setFlexGrow(1, infoSection);

        heroSection.add(heroContent);
        return heroSection;
    }

    private String findFirstVideoOrLatestVideoWatchedByUsed(Map<String, List<Metadata>> rootContent) {
        // First check for direct videos
        List<Metadata> videos = rootContent.get("VIDEO");
        if (videos != null && !videos.isEmpty()) {
            return videos.get(0).getId().toString();
        }

        // Then check in directories (seasons/episodes)
        List<Metadata> directories = rootContent.get("DIRECTORY");
        if (directories != null) {
            for (Metadata directory : directories) {
                Map<String, List<Metadata>> dirContent = videoManager.loadVideoDirectoryContent(directory);
                List<Metadata> dirVideos = dirContent.get("VIDEO");
                if (dirVideos != null && !dirVideos.isEmpty()) {
                    return dirVideos.get(0).getId().toString();
                }

                // Check nested directories (episodes)
                List<Metadata> nestedDirs = dirContent.get("DIRECTORY");
                if (nestedDirs != null) {
                    for (Metadata nestedDir : nestedDirs) {
                        Map<String, List<Metadata>> nestedContent = videoManager.loadVideoDirectoryContent(nestedDir);
                        List<Metadata> nestedVideos = nestedContent.get("VIDEO");
                        if (nestedVideos != null && !nestedVideos.isEmpty()) {
                            return nestedVideos.get(0).getId().toString();
                        }
                    }
                }
            }
        }
        return null;
    }

    private String getDescription(String filename) {
        return "Experience " + filename + " in stunning quality. " +
                "Join millions of viewers worldwide in this captivating journey filled with adventure, " +
                "drama, and unforgettable moments that will keep you on the edge of your seat.";
    }

    private Div buildDetails(Metadata root) {
        Div result = getScrollableLayoutParent();

        Map<String, List<Metadata>> rootContent = videoManager.loadVideoDirectoryContent(root);
        List<Metadata> defaultPoster = rootContent.get("POSTER");
        List<Metadata> directories = rootContent.get("DIRECTORY");

        if (directories != null) {
            for (Metadata directory : directories) {
                if (directory.getFilename().startsWith("Season")) {
                    result.add(createModernScrollableSection(directory.getFilename(), seasonVideosLayout(directory, defaultPoster)));
                }
            }
        }

        List<Metadata> videos = rootContent.get("VIDEO");
        if (videos != null && !videos.isEmpty()) {
            result.add(createModernScrollableSection("Episodes", allVideos(rootContent, defaultPoster)));
        }

        return result;
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
                        log.error("Episode folder is empty! " + metadata.getPath() + File.separator + metadata.getFilename());
                    }
                }
            }
        }

        return createVideoLayout(sortedEpisodesFolders, defaultPoster, true);
    }


    protected HorizontalLayout createVideoLayout(List<Map<String, List<Metadata>>> videosFolders, List<Metadata> defaultPoster, boolean useEpisodeGeneratedName) {
        HorizontalLayout scrollingLayout = getHorizontalScrollingLayout();

        int videoCounter = 1;
        for (Map<String, List<Metadata>> videoFolder : videosFolders) {
            List<Metadata> videos = videoFolder.get("VIDEO");
            if (videos != null) {
                for (Metadata video : videos) {
                    try {
                        VerticalLayout tile = getModernTile();
                        String imageSrc;
                        if (videoFolder.get("POSTER") == null || videoFolder.get("POSTER").isEmpty()) {
                            imageSrc = "/storage/videos/poster/" + video.getId();
                        } else {
                            imageSrc = "/storage/videos/poster/direct/" + videoFolder.get("POSTER").get(0).getId();
                        }
                        Image image = getModernImage(video.getFilename(), imageSrc, defaultPoster);

                        H4 title;
                        if (useEpisodeGeneratedName) {
                            title = getModernTitle("Episode " + videoCounter);
                        } else {
                            title = getModernTitle(video.getFilename());
                        }

                        // Play overlay
                        Div overlay = new Div();
                        overlay.getStyle()
                                .set("position", "absolute")
                                .set("top", "50%")
                                .set("left", "50%")
                                .set("transform", "translate(-50%, -50%)")
                                .set("background", "rgba(0,0,0,0.7)")
                                .set("border-radius", "50%")
                                .set("width", "60px")
                                .set("height", "60px")
                                .set("display", "flex")
                                .set("align-items", "center")
                                .set("justify-content", "center")
                                .set("opacity", "0")
                                .set("transition", "opacity 0.3s ease")
                                .set("z-index", "10");

                        Icon playIcon = new Icon(VaadinIcon.PLAY);
                        playIcon.setColor("white");
                        playIcon.setSize("24px");
                        overlay.add(playIcon);

                        tile.getStyle().set("position", "relative");
                        tile.add(image, overlay, title);
                        tile.addClickListener(click ->
                                UI.getCurrent().navigate("/streaming-platform/video-player/" + video.getId())
                        );

                        // Hover effect for overlay
                        tile.getElement().executeJs(
                                "this.addEventListener('mouseenter', () => {" +
                                        "const overlay = this.querySelector('div');" +
                                        "if (overlay) overlay.style.opacity = '1';" +
                                        "});" +
                                        "this.addEventListener('mouseleave', () => {" +
                                        "const overlay = this.querySelector('div');" +
                                        "if (overlay) overlay.style.opacity = '0';" +
                                        "});"
                        );

                        scrollingLayout.add(tile);
                    } catch (Exception e) {
                        log.error("Unable to load video!", e);
                        showErrorNotification("Unable to load video!");
                    }
                    videoCounter++;
                }
            }
        }

        return scrollingLayout;
    }

    private HorizontalLayout allVideos(Map<String, List<Metadata>> videos, List<Metadata> defaultPoster) {
        return createVideoLayout(Collections.singletonList(videos), defaultPoster, false);
    }
}