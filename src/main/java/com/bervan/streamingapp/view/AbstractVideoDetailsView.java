package com.bervan.streamingapp.view;

import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.VideoManager;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractVideoDetailsView extends AbstractStreamingPage implements HasUrlParameter<String> {
    public static final String ROUTE_NAME = "/streaming-platform/details";
    private final BervanLogger logger;
    private final VideoManager videoManager;

    private final Div treeView;

    public AbstractVideoDetailsView(BervanLogger logger, VideoManager videoManager) {
        super(ROUTE_NAME);
        this.logger = logger;
        this.videoManager = videoManager;

        // Initialize the Tree View container
        treeView = new Div();
        treeView.addClassName("tree-view");

        // Add Tree View to the UI
        Div content = new Div(treeView);
        content.setSizeFull();
        content.addClassName("content-container");
        add(content);
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

            // Generate HTML for the entire tree
            String treeHtml = generateTreeHtml(rootFolder);

            // Add the generated tree HTML to the container
            treeView.getElement().setProperty("innerHTML", treeHtml);

            // Attach JavaScript for dynamic tree handling
            attachScript();

        } catch (Exception e) {
            logger.error("Could not load details!", e);
            showErrorNotification("Could not load details!");
        }
    }

    private String generateTreeHtml(Metadata folder) {
        StringBuilder htmlBuilder = new StringBuilder();

        htmlBuilder.append("<ul>");
        appendFolderContent(htmlBuilder, folder);
        htmlBuilder.append("</ul>");

        return htmlBuilder.toString();
    }

    private void appendFolderContent(StringBuilder htmlBuilder, Metadata folder) {
        htmlBuilder.append("<li class='folder-item'>");
        htmlBuilder.append("<span class='folder-toggle option-button'>")
                .append(folder.getFilename()).append("</span>");

        // Load directory content
        Map<String, List<Metadata>> content = videoManager.loadVideoDirectory(folder);
        List<Metadata> subfoldersAndFiles = content.values().stream()
                .flatMap(List::stream)
                .toList();

        if (!subfoldersAndFiles.isEmpty()) {
            htmlBuilder.append("<ul class='nested'>");
            for (Metadata item : subfoldersAndFiles) {
                if (item.isDirectory()) {
                    appendFolderContent(htmlBuilder, item);
                } else if (videoManager.getSupportedExtensions().contains(item.getExtension())) {
                    htmlBuilder.append("<li class='file-item'>");
                    htmlBuilder.append("<span class='file-name'>").append(item.getFilename()).append("</span>");
                    htmlBuilder.append("<button class='file-button' onclick='navigateToVideo(\"").append(item.getId()).append("\")'>Open</button>");
                    htmlBuilder.append("</li>");
                }
            }
            htmlBuilder.append("</ul>");
        }

        htmlBuilder.append("</li>");
    }

    private void attachScript() {
        getElement().executeJs(
                """                    
                        document.querySelectorAll('.folder-toggle').forEach(folder => {
                            folder.addEventListener('click', function () {
                                const nested = this.nextElementSibling;
                                 if (nested)
                                    nested.classList.toggle('active');
                            });
                        });
                                                
                        // Navigate to video
                        window.navigateToVideo = function(videoId) {
                            window.location.href = '/streaming-platform/video-player/' + videoId;
                        };
                        """
        );
    }
}