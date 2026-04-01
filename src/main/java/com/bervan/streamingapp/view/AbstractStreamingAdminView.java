package com.bervan.streamingapp.view;

import com.bervan.common.component.BervanButton;
import com.bervan.common.component.BervanButtonStyle;
import com.bervan.streamingapp.StreamingAdminService;
import com.bervan.streamingapp.config.ProductionData;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;

import java.util.Map;

public abstract class AbstractStreamingAdminView extends AbstractStreamingPage {
    public static final String ROUTE_NAME = "/streaming-platform/admin";

    private final StreamingAdminService adminService;
    private final Map<String, ProductionData> streamingProductionData;

    public AbstractStreamingAdminView(StreamingAdminService adminService,
                                       Map<String, ProductionData> streamingProductionData) {
        super(ROUTE_NAME);
        this.adminService = adminService;
        this.streamingProductionData = streamingProductionData;
        buildView();
    }

    private void buildView() {
        VerticalLayout content = new VerticalLayout();
        content.setWidthFull();
        content.getStyle().set("padding", "20px");

        H2 title = new H2("Streaming Admin");
        title.getStyle().set("margin-bottom", "20px").set("color", "var(--lumo-body-text-color)");

        H3 productionsTitle = new H3("Productions");
        productionsTitle.getStyle().set("color", "var(--lumo-body-text-color)");
        VerticalLayout productionsList = buildProductionsList();

        BervanButton addButton = new BervanButton("Add Production", VaadinIcon.PLUS.create(), BervanButtonStyle.PRIMARY);
        addButton.addClickListener(e -> openAddProductionDialog());
        addButton.getStyle().set("margin-bottom", "20px");

        content.add(title, addButton, productionsTitle, productionsList);
        add(content);
    }

    private VerticalLayout buildProductionsList() {
        VerticalLayout list = new VerticalLayout();
        list.setSpacing(false);
        list.setPadding(false);

        if (streamingProductionData.isEmpty()) {
            Paragraph empty = new Paragraph("No productions found. Add one using the button above.");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
            list.add(empty);
            return list;
        }

        for (Map.Entry<String, ProductionData> entry : streamingProductionData.entrySet()) {
            ProductionData pd = entry.getValue();
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
            row.setWidthFull();
            row.getStyle()
                    .set("padding", "10px")
                    .set("border-bottom", "1px solid var(--bervan-border-color, rgba(255,255,255,0.1))");

            Paragraph name = new Paragraph(pd.getProductionName());
            name.getStyle().set("margin", "0").set("flex-grow", "1")
                    .set("color", "var(--lumo-body-text-color)");

            String type = pd.getProductionDetails() != null && pd.getProductionDetails().getType() != null
                    ? pd.getProductionDetails().getType().name() : "UNKNOWN";
            Paragraph typeLabel = new Paragraph(type);
            typeLabel.getStyle().set("margin", "0").set("min-width", "100px")
                    .set("color", "var(--lumo-secondary-text-color)");

            Button detailsBtn = new Button("Details", VaadinIcon.FILE_TEXT.create());
            detailsBtn.addClickListener(e ->
                    com.vaadin.flow.component.UI.getCurrent().navigate(
                            AbstractProductionDetailsView.ROUTE_NAME + "/" + pd.getProductionId()));

            row.add(name, typeLabel, detailsBtn);
            row.setFlexGrow(1, name);
            list.add(row);
        }

        return list;
    }

    private void openAddProductionDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add New Production");
        dialog.setWidth("600px");

        VerticalLayout form = new VerticalLayout();
        form.setSpacing(true);
        form.setPadding(false);

        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setRequired(true);

        ComboBox<String> typeCombo = new ComboBox<>("Type");
        typeCombo.setItems("movie", "tv_series");
        typeCombo.setValue("movie");
        typeCombo.setWidthFull();

        ComboBox<String> formatCombo = new ComboBox<>("Video Format");
        formatCombo.setItems("mp4", "hls");
        formatCombo.setValue("mp4");
        formatCombo.setWidthFull();

        TextArea descriptionArea = new TextArea("Description");
        descriptionArea.setWidthFull();
        descriptionArea.setMaxLength(2000);

        NumberField ratingField = new NumberField("Rating (0-10)");
        ratingField.setMin(0);
        ratingField.setMax(10);
        ratingField.setStep(0.1);
        ratingField.setWidthFull();

        HorizontalLayout yearRow = new HorizontalLayout();
        yearRow.setWidthFull();
        NumberField yearStartField = new NumberField("Year Start");
        yearStartField.setMin(1900);
        yearStartField.setMax(2100);
        yearStartField.setWidthFull();
        NumberField yearEndField = new NumberField("Year End");
        yearEndField.setMin(1900);
        yearEndField.setMax(2100);
        yearEndField.setWidthFull();
        yearRow.add(yearStartField, yearEndField);
        yearRow.setFlexGrow(1, yearStartField);
        yearRow.setFlexGrow(1, yearEndField);

        TextField countryField = new TextField("Country");
        countryField.setWidthFull();

        TextField categoriesField = new TextField("Categories (comma-separated)");
        categoriesField.setWidthFull();

        TextField tagsField = new TextField("Tags (comma-separated)");
        tagsField.setWidthFull();

        MemoryBuffer posterBuffer = new MemoryBuffer();
        Upload posterUpload = new Upload(posterBuffer);
        posterUpload.setAcceptedFileTypes("image/jpeg", "image/png", ".jpg", ".jpeg", ".png");
        posterUpload.setMaxFiles(1);
        posterUpload.setDropLabel(new com.vaadin.flow.component.html.Span("Drop poster here"));
        com.vaadin.flow.component.html.Div posterLabel = new com.vaadin.flow.component.html.Div("Poster (optional)");
        posterLabel.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");

        form.add(nameField, typeCombo, formatCombo, descriptionArea, ratingField, yearRow,
                countryField, categoriesField, tagsField, posterLabel, posterUpload);

        BervanButton saveButton = new BervanButton("Create", VaadinIcon.CHECK.create(), BervanButtonStyle.PRIMARY);
        saveButton.addClickListener(e -> {
            String name = nameField.getValue();
            if (name == null || name.isBlank()) {
                showErrorNotification("Production name is required!");
                return;
            }
            try {
                byte[] posterBytes = null;
                String posterFilename = null;
                if (posterBuffer.getInputStream() != null && posterBuffer.getFileName() != null && !posterBuffer.getFileName().isBlank()) {
                    posterBytes = posterBuffer.getInputStream().readAllBytes();
                    posterFilename = posterBuffer.getFileName();
                }

                adminService.createProduction(
                        name,
                        typeCombo.getValue(),
                        formatCombo.getValue(),
                        descriptionArea.getValue(),
                        ratingField.getValue(),
                        yearStartField.getValue() != null ? yearStartField.getValue().intValue() : null,
                        yearEndField.getValue() != null ? yearEndField.getValue().intValue() : null,
                        countryField.getValue(),
                        categoriesField.getValue(),
                        tagsField.getValue(),
                        posterBytes,
                        posterFilename
                );

                adminService.reloadConfig(streamingProductionData);
                dialog.close();
                showPrimaryNotification("Production \"" + name + "\" created successfully!");
                com.vaadin.flow.component.UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                showErrorNotification("Failed to create production: " + ex.getMessage());
            }
        });

        Button cancelButton = new Button("Cancel", ev -> dialog.close());

        HorizontalLayout buttons = new HorizontalLayout(saveButton, cancelButton);
        dialog.add(form);
        dialog.getFooter().add(buttons);
        dialog.open();
    }
}
