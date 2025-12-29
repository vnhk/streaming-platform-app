
package com.bervan.streamingapp.view;

import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.conifg.ProductionData;
import com.bervan.streamingapp.conifg.ProductionDetails;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractProductionListView extends AbstractRemoteControlSupportedView {
    public static final String ROUTE_NAME = "/streaming-platform";
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final VideoManager videoManager;
    private final Map<String, ProductionData> streamingProductionData;

    // Search and filter components
    private TextField searchField;
    private ComboBox<ProductionDetails.VideoType> typeFilter;
    private MultiSelectComboBox<String> categoryFilter;
    private MultiSelectComboBox<String> countryFilter;
    private ComboBox<String> yearFilter;
    private ComboBox<String> ratingFilter;

    // Content containers
    private VerticalLayout contentContainer;
    private List<ProductionData> filteredData;

    public AbstractProductionListView(VideoManager videoManager, Map<String, ProductionData> streamingProductionData) {
        super(ROUTE_NAME, AbstractProductionPlayerView.ROUTE_NAME, AbstractProductionDetailsView.ROUTE_NAME);
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;
        this.filteredData = new ArrayList<>(streamingProductionData.values());

        initializeView();
    }

    private void initializeView() {
        Div scrollableLayoutParent = getScrollableLayoutParent();

        // Add search and filter section
        scrollableLayoutParent.add(createSearchAndFilterSection());

        // Add content container
        contentContainer = new VerticalLayout();
        contentContainer.setPadding(false);
        contentContainer.setSpacing(true);
        contentContainer.setWidthFull();

        scrollableLayoutParent.add(contentContainer);

        // Initial load of content
        refreshContent();

        add(scrollableLayoutParent, new Hr());
    }

    private VerticalLayout createSearchAndFilterSection() {
        VerticalLayout filterSection = new VerticalLayout();
        filterSection.setPadding(false);
        filterSection.setSpacing(true);
        filterSection.setWidthFull();
        filterSection.getStyle()
                .set("background", "var(--streaming-tile-background)")
                .set("border-radius", "12px")
                .set("padding", "20px")
                .set("margin-bottom", "20px")
                .set("box-shadow", "0 4px 12px var(--streaming-tile-shadow)");

        // Search bar
        HorizontalLayout searchLayout = createSearchBar();

        // Filter controls
        HorizontalLayout filterControls = createFilterControls();

        // Action buttons
        HorizontalLayout actionButtons = createActionButtons();

        filterSection.add(
                new H3("Discover Movies & TV Series"),
                searchLayout,
                filterControls,
                actionButtons
        );

        return filterSection;
    }

    private HorizontalLayout createSearchBar() {
        HorizontalLayout searchLayout = new HorizontalLayout();
        searchLayout.setWidthFull();
        searchLayout.setAlignItems(FlexComponent.Alignment.CENTER);

        searchField = new TextField();
        searchField.setPlaceholder("Search by title, description, tags...");
        searchField.setWidthFull();
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> applyFilters());
        searchField.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "25px");

        searchLayout.add(searchField);
        searchLayout.setFlexGrow(1, searchField);

        return searchLayout;
    }

    private HorizontalLayout createFilterControls() {
        HorizontalLayout filterLayout = new HorizontalLayout();
        filterLayout.setWidthFull();
        filterLayout.getStyle().set("flex-wrap", "wrap");
        filterLayout.getStyle().set("gap", "10px");

        // Type filter
        typeFilter = new ComboBox<>("Type");
        typeFilter.setItems(ProductionDetails.VideoType.values());
        typeFilter.setItemLabelGenerator(type ->
                type.name().replace("_", " ").toLowerCase().replace("tv", "TV"));
        typeFilter.addValueChangeListener(e -> applyFilters());
        typeFilter.getStyle().set("min-width", "150px");

        // Category filter
        categoryFilter = new MultiSelectComboBox<>("Categories");
        Set<String> allCategories = streamingProductionData.values().stream()
                .map(ProductionData::getProductionDetails)
                .filter(Objects::nonNull)
                .map(ProductionDetails::getCategories)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        categoryFilter.setItems(allCategories);
        categoryFilter.addSelectionListener(e -> applyFilters());
        categoryFilter.getStyle().set("min-width", "200px");

        // Country filter
        countryFilter = new MultiSelectComboBox<>("Countries");
        Set<String> allCountries = streamingProductionData.values().stream()
                .map(ProductionData::getProductionDetails)
                .filter(Objects::nonNull)
                .map(ProductionDetails::getCountry)
                .filter(Objects::nonNull)
                .filter(country -> !country.isBlank())
                .collect(Collectors.toSet());
        countryFilter.setItems(allCountries);
        countryFilter.addSelectionListener(e -> applyFilters());
        countryFilter.getStyle().set("min-width", "180px");

        // Year filter
        yearFilter = new ComboBox<>("Release Year");
        Set<String> years = streamingProductionData.values().stream()
                .map(ProductionData::getProductionDetails)
                .filter(Objects::nonNull)
                .filter(details -> details.getReleaseYearStart() != null)
                .map(details -> String.valueOf(details.getReleaseYearStart()))
                .collect(Collectors.toSet());
        List<String> sortedYears = years.stream()
                .sorted((a, b) -> Integer.compare(Integer.parseInt(b), Integer.parseInt(a)))
                .collect(Collectors.toList());
        yearFilter.setItems(sortedYears);
        yearFilter.addValueChangeListener(e -> applyFilters());
        yearFilter.getStyle().set("min-width", "130px");

        // Rating filter
        ratingFilter = new ComboBox<>("Min Rating");
        ratingFilter.setItems("9.0+", "8.0+", "7.0+", "6.0+", "5.0+");
        ratingFilter.addValueChangeListener(e -> applyFilters());
        ratingFilter.getStyle().set("min-width", "130px");

        filterLayout.add(typeFilter, categoryFilter, countryFilter, yearFilter, ratingFilter);

        return filterLayout;
    }

    private HorizontalLayout createActionButtons() {
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        Button clearFilters = new Button("Clear Filters", new Icon(VaadinIcon.REFRESH));
        clearFilters.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        clearFilters.addClickListener(e -> clearAllFilters());

        Button sortByRating = new Button("Sort by Rating", new Icon(VaadinIcon.STAR));
        sortByRating.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sortByRating.addClickListener(e -> sortByRating());

        Button sortByYear = new Button("Sort by Year", new Icon(VaadinIcon.CALENDAR));
        sortByYear.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sortByYear.addClickListener(e -> sortByYear());

        buttonLayout.add(clearFilters, sortByRating, sortByYear);
        return buttonLayout;
    }

    private void applyFilters() {
        String searchTerm = searchField.getValue() != null ? searchField.getValue().toLowerCase().trim() : "";
        ProductionDetails.VideoType selectedType = typeFilter.getValue();
        Set<String> selectedCategories = categoryFilter.getSelectedItems();
        Set<String> selectedCountries = countryFilter.getSelectedItems();
        String selectedYear = yearFilter.getValue();
        String selectedRating = ratingFilter.getValue();

        filteredData = streamingProductionData.values().stream()
                .filter(data -> matchesSearchTerm(data, searchTerm))
                .filter(data -> matchesType(data, selectedType))
                .filter(data -> matchesCategories(data, selectedCategories))
                .filter(data -> matchesCountries(data, selectedCountries))
                .filter(data -> matchesYear(data, selectedYear))
                .filter(data -> matchesRating(data, selectedRating))
                .collect(Collectors.toList());

        refreshContent();
    }

    private boolean matchesSearchTerm(ProductionData data, String searchTerm) {
        if (searchTerm.isEmpty()) return true;

        ProductionDetails details = data.getProductionDetails();
        if (details == null) return false;

        return (details.getName() != null && details.getName().toLowerCase().contains(searchTerm)) ||
                (details.getDescription() != null && details.getDescription().toLowerCase().contains(searchTerm)) ||
                (details.getTags() != null && details.getTags().stream()
                        .anyMatch(tag -> tag.toLowerCase().contains(searchTerm)));
    }

    private boolean matchesType(ProductionData data, ProductionDetails.VideoType selectedType) {
        if (selectedType == null) return true;
        ProductionDetails details = data.getProductionDetails();
        return details != null && selectedType.equals(details.getType());
    }

    private boolean matchesCategories(ProductionData data, Set<String> selectedCategories) {
        if (selectedCategories.isEmpty()) return true;
        ProductionDetails details = data.getProductionDetails();
        if (details == null || details.getCategories() == null) return false;
        return details.getCategories().stream().anyMatch(selectedCategories::contains);
    }

    private boolean matchesCountries(ProductionData data, Set<String> selectedCountries) {
        if (selectedCountries.isEmpty()) return true;
        ProductionDetails details = data.getProductionDetails();
        return details != null && details.getCountry() != null &&
                selectedCountries.contains(details.getCountry());
    }

    private boolean matchesYear(ProductionData data, String selectedYear) {
        if (selectedYear == null) return true;
        ProductionDetails details = data.getProductionDetails();
        if (details == null || details.getReleaseYearStart() == null) return false;
        return String.valueOf(details.getReleaseYearStart()).equals(selectedYear);
    }

    private boolean matchesRating(ProductionData data, String selectedRating) {
        if (selectedRating == null) return true;
        ProductionDetails details = data.getProductionDetails();
        if (details == null || details.getRating() == null) return false;

        double minRating = Double.parseDouble(selectedRating.replace("+", ""));
        return details.getRating() >= minRating;
    }

    private void clearAllFilters() {
        searchField.clear();
        typeFilter.clear();
        categoryFilter.clear();
        countryFilter.clear();
        yearFilter.clear();
        ratingFilter.clear();
        filteredData = new ArrayList<>(streamingProductionData.values());
        refreshContent();
    }

    private void sortByRating() {
        filteredData.sort((a, b) -> {
            Double ratingA = a.getProductionDetails() != null ? a.getProductionDetails().getRating() : 0.0;
            Double ratingB = b.getProductionDetails() != null ? b.getProductionDetails().getRating() : 0.0;
            return Double.compare(ratingB != null ? ratingB : 0.0, ratingA != null ? ratingA : 0.0);
        });
        refreshContent();
    }

    private void sortByYear() {
        filteredData.sort((a, b) -> {
            Integer yearA = a.getProductionDetails() != null ? a.getProductionDetails().getReleaseYearStart() : 0;
            Integer yearB = b.getProductionDetails() != null ? b.getProductionDetails().getReleaseYearStart() : 0;
            return Integer.compare(yearB != null ? yearB : 0, yearA != null ? yearA : 0);
        });
        refreshContent();
    }

    private void refreshContent() {
        contentContainer.removeAll();

        if (filteredData.isEmpty()) {
            contentContainer.add(createNoResultsMessage());
            return;
        }

        // Group by type and create sections
        Map<ProductionDetails.VideoType, List<ProductionData>> groupedByType = filteredData.stream()
                .filter(data -> data.getProductionDetails() != null && data.getProductionDetails().getType() != null)
                .collect(Collectors.groupingBy(data -> data.getProductionDetails().getType()));

        // Add Movies section
        List<ProductionData> movies = groupedByType.get(ProductionDetails.VideoType.MOVIE);
        if (movies != null && !movies.isEmpty()) {
            contentContainer.add(createModernScrollableSection(
                    "🎬 Movies (" + movies.size() + ")",
                    createVideoLayout(movies, ROUTE_NAME + "/details/")
            ));
        }

        // Add TV Series section
        List<ProductionData> tvSeries = groupedByType.get(ProductionDetails.VideoType.TV_SERIES);
        if (tvSeries != null && !tvSeries.isEmpty()) {
            contentContainer.add(createModernScrollableSection(
                    "📺 TV Series (" + tvSeries.size() + ")",
                    createVideoLayout(tvSeries, ROUTE_NAME + "/details/")
            ));
        }

        // Add Other section
        List<ProductionData> others = groupedByType.get(ProductionDetails.VideoType.OTHER);
        if (others != null && !others.isEmpty()) {
            contentContainer.add(createModernScrollableSection(
                    "🎭 Other (" + others.size() + ")",
                    createVideoLayout(others, ROUTE_NAME + "/details/")
            ));
        }

        // Add recently added section (last 10 items by creation/modification date)
        List<ProductionData> recentlyAdded = filteredData.stream()
                .sorted((a, b) -> Long.compare(
                        b.getMainFolder().getCreateDate() != null ? b.getMainFolder().getCreateDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0,
                        a.getMainFolder().getCreateDate() != null ? a.getMainFolder().getCreateDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0
                ))
                .limit(10)
                .collect(Collectors.toList());

        if (!recentlyAdded.isEmpty()) {
            contentContainer.add(createModernScrollableSection(
                    "🆕 Recently Added",
                    createVideoLayout(recentlyAdded, ROUTE_NAME + "/details/")
            ));
        }

        // Add high-rated section
        List<ProductionData> highRated = filteredData.stream()
                .filter(data -> data.getProductionDetails() != null &&
                        data.getProductionDetails().getRating() != null &&
                        data.getProductionDetails().getRating() >= 8.0)
                .sorted((a, b) -> Double.compare(
                        b.getProductionDetails().getRating(),
                        a.getProductionDetails().getRating()))
                .limit(15)
                .collect(Collectors.toList());

        if (!highRated.isEmpty()) {
            contentContainer.add(createModernScrollableSection(
                    "⭐ Highly Rated (8.0+)",
                    createVideoLayout(highRated, ROUTE_NAME + "/details/")
            ));
        }
    }

    private Div createNoResultsMessage() {
        Div noResults = new Div();
        noResults.setWidthFull();
        noResults.getStyle()
                .set("text-align", "center")
                .set("padding", "60px 20px")
                .set("color", "var(--lumo-secondary-text-color)");

        Icon searchIcon = new Icon(VaadinIcon.SEARCH);
        searchIcon.setSize("64px");
        searchIcon.getStyle().set("opacity", "0.5");

        H3 noResultsTitle = new H3("No Content Found");
        noResultsTitle.getStyle().set("margin", "20px 0 10px 0");

        Paragraph noResultsText = new Paragraph("Try adjusting your search criteria or filters to find what you're looking for.");
        noResultsText.getStyle().set("margin", "0");

        noResults.add(searchIcon, noResultsTitle, noResultsText);
        return noResults;
    }

    private HorizontalLayout createVideoLayout(List<ProductionData> productionDataList, String route) {
        HorizontalLayout scrollingLayout = getHorizontalScrollingLayout();

        for (ProductionData productionData : productionDataList) {
            try {
                ProductionDetails productionDetails = productionData.getProductionDetails();
                Metadata productionMainFolder = productionData.getMainFolder();

                VerticalLayout tile = getModernTile();
                String imageSrc = "/storage/videos/poster/" + productionMainFolder.getId();
                Image image = getModernImage(productionDetails.getName(), imageSrc, null);

                // Enhanced title with additional info
                VerticalLayout titleSection = new VerticalLayout();
                titleSection.setPadding(false);
                titleSection.setSpacing(false);
                titleSection.setWidthFull();

                H4 title = getModernTitle(productionDetails.getName());

                // Add rating and year info
                HorizontalLayout infoLayout = new HorizontalLayout();
                infoLayout.setPadding(false);
                infoLayout.setSpacing(true);
                infoLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
                infoLayout.getStyle().set("margin-top", "5px");

                if (productionDetails.getRating() != null) {
                    Span ratingSpan = new Span("⭐ " + String.format("%.1f", productionDetails.getRating()));
                    ratingSpan.getStyle()
                            .set("font-size", "0.75rem")
                            .set("color", "var(--streaming-accent-color)")
                            .set("font-weight", "600");
                    infoLayout.add(ratingSpan);
                }

                if (productionDetails.getReleaseYearStart() != null) {
                    Span yearSpan = new Span(String.valueOf(productionDetails.getReleaseYearStart()));
                    yearSpan.getStyle()
                            .set("font-size", "0.75rem")
                            .set("color", "var(--lumo-secondary-text-color)");
                    infoLayout.add(yearSpan);
                }

                titleSection.add(title, infoLayout);

                tile.add(image, titleSection);
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