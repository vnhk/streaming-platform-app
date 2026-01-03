package com.bervan.streamingapp.view;

import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.ProductionDetails;
import com.bervan.streamingapp.config.structure.*;
import com.bervan.streamingapp.config.structure.mp4.MP4EpisodeStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4MovieRootProductionStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4SeasonStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4TvSeriesRootProductionStructure;
import com.bervan.streamingapp.view.player.AbstractProductionPlayerView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractProductionDetailsView extends AbstractStreamingPage implements HasUrlParameter<String> {
    public static final String ROUTE_NAME = "/streaming-platform/details";
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final VideoManager videoManager;
    private final Map<String, ProductionData> streamingProductionData;

    public AbstractProductionDetailsView(VideoManager videoManager, Map<String, ProductionData> streamingProductionData) {
        super(ROUTE_NAME, AbstractProductionPlayerView.ROUTE_NAME);
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;
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
            Optional<ProductionData> optionalProduction = streamingProductionData.values().stream()
                    .filter(e -> e.getProductionId().equals(videoFolderId)).findFirst();
            if (optionalProduction.isEmpty()) {
                log.error("Could not find production based on provided id!");
                showErrorNotification("Could not find details!");
                return;
            }

            // Create hero section
            VerticalLayout heroSection = createHeroSection(optionalProduction.get());

            // Create content section
            Div contentSection = buildDetails(optionalProduction.get());

            add(heroSection, contentSection);
        } catch (Exception e) {
            log.error("Could not load details!", e);
            showErrorNotification("Could not load details!");
        }
    }

    private VerticalLayout createHeroSection(ProductionData productionData) {
        VerticalLayout heroSection = new VerticalLayout();
        heroSection.setWidthFull();
        heroSection.setAlignItems(Alignment.CENTER);
        heroSection.getStyle()
                .set("background", "linear-gradient(var(--streaming-overlay-background), var(--streaming-overlay-background)), url('/storage/videos/poster/" + productionData.getProductionId() + "')")
                .set("background-size", "cover")
                .set("background-position", "center")
                .set("background-attachment", "fixed")
                .set("color", "white")
                .set("padding", "60px 20px")
                .set("text-align", "center")
                .set("position", "relative");

        HorizontalLayout heroContent = new HorizontalLayout();
        heroContent.setAlignItems(Alignment.START);
        heroContent.setWidthFull();
        heroContent.setMaxWidth("1200px");
        heroContent.getStyle().set("gap", "40px");

        // Poster image
        Image posterImage = null;

        if (productionData.getBase64PosterSrc() != null) {
            posterImage = new Image(productionData.getBase64PosterSrc(), productionData.getProductionName());
        } else {
            String imageSrc = "/storage/videos/poster/" + productionData.getProductionId();
            posterImage = new Image(imageSrc, productionData.getProductionName());
        }

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
        infoSection.setAlignItems(Alignment.START);

        // Title
        H1 title = new H1(productionData.getProductionName());
        title.getStyle()
                .set("margin", "0 0 20px 0")
                .set("font-size", "3.5rem")
                .set("font-weight", "700")
                .set("text-shadow", "2px 2px 4px var(--streaming-title-shadow)")
                .set("line-height", "1.2");

        Paragraph description = new Paragraph(getDescription(productionData.getProductionDetails()));
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
        String firstVideoId = findFirstVideoId(productionData);

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

    private String findFirstVideoId(ProductionData productionData) {
        BaseRootProductionStructure productionStructure = productionData.getProductionStructure();
        if (productionStructure instanceof MP4MovieRootProductionStructure) {
            List<Metadata> videos = ((MP4MovieRootProductionStructure) productionStructure).getVideosFolders();
            if (videos != null && !videos.isEmpty()) {
                return videos.get(0).getId().toString();
            }
        } else if (productionStructure instanceof MP4TvSeriesRootProductionStructure) {
            List<MP4SeasonStructure> seasons = ((MP4TvSeriesRootProductionStructure) productionStructure).getSeasons();
            if (seasons != null && !seasons.isEmpty()) {
                List<MP4EpisodeStructure> episodes = seasons.get(0).getEpisodes();
                if (episodes != null && !episodes.isEmpty()) {
                    Metadata video = episodes.get(0).getVideo();
                    if (video != null) {
                        return video.getId().toString();
                    }
                }
            }

        }

        return null;
    }

    private String getDescription(ProductionDetails productionDetails) {
        if (productionDetails.getDescription() == null || productionDetails.getDescription().isBlank()) {
            return "Experience " + productionDetails.getName() + " in stunning quality. " +
                    "Join millions of viewers worldwide in this captivating journey filled with adventure, " +
                    "drama, and unforgettable moments that will keep you on the edge of your seat.";
        } else {
            return productionDetails.getDescription();
        }
    }

    private Div buildDetails(ProductionData productionData) {
        Div result = getScrollableLayoutParent();

        if (productionData.getProductionDetails().getType() == ProductionDetails.VideoType.TV_SERIES) {
            TvSeriesBaseRootProductionStructure productionStructure = (TvSeriesBaseRootProductionStructure) productionData.getProductionStructure();
            for (SeasonStructure season : productionStructure.getSeasons()) {
                result.add(createModernScrollableSection(season.getMetadataName(),
                        seasonVideosLayout(productionData, season)));
            }
        }

        if (productionData.getProductionDetails().getType() == ProductionDetails.VideoType.MOVIE) {
            MovieBaseRootProductionStructure productionStructure = (MovieBaseRootProductionStructure) productionData.getProductionStructure();
            result.add(createModernScrollableSection("Episodes",
                    createVideosLayout(productionData, productionStructure.getVideosFolders())));
        }


        return result;
    }

    private HorizontalLayout createVideosLayout(ProductionData productionData, List<Metadata> videos) {
        HorizontalLayout scrollingLayout = getHorizontalScrollingLayout();

        int videoCounter = 1;
        for (Metadata video : videos) {
            try {
                buildTile(productionData, null, video, videoCounter, scrollingLayout);
            } catch (Exception e) {
                log.error("Unable to load video!", e);
                showErrorNotification("Unable to load video!");
            }
            videoCounter++;
        }

        return scrollingLayout;
    }

    private HorizontalLayout seasonVideosLayout(ProductionData productionData, SeasonStructure seasonStructure) {
        int episodes = seasonStructure.getEpisodes().size();

        List<EpisodeStructure> sortedEpisodesFolders = new ArrayList<>();
        for (int i = 1; i <= episodes; i++) {
            String pattern = "(?:Ep(?:isode)?\\s?)" + i + "(?![0-9a-zA-Z])";
            Pattern regex = Pattern.compile(pattern);
            for (EpisodeStructure episodeStructure : seasonStructure.getEpisodes()) {
                Matcher matcher = regex.matcher(episodeStructure.getMetadataName());
                if (matcher.find()) {
                    sortedEpisodesFolders.add(episodeStructure);
                }
            }
        }

        return createEpisodesLayout(sortedEpisodesFolders, productionData);
    }


    protected HorizontalLayout createEpisodesLayout(List<EpisodeStructure> episodeStructures, ProductionData productionData) {
        HorizontalLayout scrollingLayout = getHorizontalScrollingLayout();

        int videoCounter = 1;
        for (EpisodeStructure episodeStructure : episodeStructures) {
            try {
                buildTile(productionData, episodeStructure.getPoster(), episodeStructure.getEpisodeFolder(), videoCounter, scrollingLayout);
            } catch (Exception e) {
                log.error("Unable to load video!", e);
                showErrorNotification("Unable to load video!");
            }
            videoCounter++;
        }

        return scrollingLayout;
    }

    private void buildTile(ProductionData productionData, Metadata primaryPoster, Metadata videoFolder, int videoCounter, HorizontalLayout scrollingLayout) {
        VerticalLayout tile = createTile(productionData.getProductionStructure().getPoster(), primaryPoster, videoCounter);
        tile.addClickListener(click ->
                UI.getCurrent().navigate("/streaming-platform/video-player/" + productionData.getProductionName() + "/" + videoFolder.getId())
        );
        scrollingLayout.add(tile);
    }

    private VerticalLayout createTile(Metadata defaultPoster, Metadata primaryPoster, int videoCounter) {
        String imageSrc;
        if (primaryPoster == null) {
            imageSrc = "/storage/videos/poster/direct/" + defaultPoster.getId();
        } else {
            imageSrc = "/storage/videos/poster/direct/" + primaryPoster.getId();
        }

        Image image = getModernImage("no-image", imageSrc);

        VerticalLayout tile = getModernTile();
        // Play overlay
        Div overlay = getProductionOverlay();

        H4 title = getModernTitle("Episode " + videoCounter);
        tile.getStyle().set("position", "relative");
        tile.add(image, overlay, title);

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
        return tile;
    }

    private Div getProductionOverlay() {
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
        return overlay;
    }
}