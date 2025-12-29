package com.bervan.streamingapp.view;

import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.conifg.ProductionData;
import com.bervan.streamingapp.conifg.ProductionDetails;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Map;

public abstract class AbstractProductionListView extends AbstractRemoteControlSupportedView {
    public static final String ROUTE_NAME = "/streaming-platform";
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final VideoManager videoManager;
    private final Map<String, ProductionData> streamingProductionData;

    public AbstractProductionListView(VideoManager videoManager, Map<String, ProductionData> streamingProductionData) {
        super(ROUTE_NAME, AbstractProductionPlayerView.ROUTE_NAME, AbstractProductionDetailsView.ROUTE_NAME);
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;

        Div scrollableLayoutParent = getScrollableLayoutParent();
        scrollableLayoutParent.add(createModernScrollableSection("Movies/TV Series:", groupedVideos()));
        add(scrollableLayoutParent, new Hr());
    }

    private HorizontalLayout groupedVideos() {
        return createVideoLayout(ROUTE_NAME + "/details/");
    }

    private HorizontalLayout createVideoLayout(String route) {
        HorizontalLayout scrollingLayout = getHorizontalScrollingLayout();

        for (Map.Entry<String, ProductionData> streamingProductionDataEntry : streamingProductionData.entrySet()) {
            try {
                ProductionData productionData = streamingProductionDataEntry.getValue();
                ProductionDetails productionDetails = productionData.getProductionDetails();
                Metadata productionMainFolder = productionData.getMainFolder();

                VerticalLayout tile = getModernTile();
                String imageSrc = "/storage/videos/poster/" + productionMainFolder.getId();
                Image image = getModernImage(productionDetails.getName(), imageSrc, null);
                H4 title = getModernTitle(productionDetails.getName());

                tile.add(image, title);
                tile.addClickListener(click ->
                        UI.getCurrent().navigate(route + productionMainFolder.getId())
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