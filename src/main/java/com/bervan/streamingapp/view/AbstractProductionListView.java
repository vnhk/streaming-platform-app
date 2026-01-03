package com.bervan.streamingapp.view;

import com.bervan.common.component.BervanButton;
import com.bervan.common.component.BervanButtonStyle;
import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.ProductionDetails;
import com.bervan.streamingapp.view.player.AbstractProductionPlayerView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractProductionListView extends AbstractRemoteControlSupportedView {
    public static final String ROUTE_NAME = "/streaming-platform";
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final VideoManager videoManager;
    private final Map<String, ProductionData> streamingProductionData;

    // Search and filter components
    private TextField searchField;
    private MultiSelectComboBox<ProductionDetails.VideoType> typeFilter;
    private MultiSelectComboBox<String> categoryFilter;
    private MultiSelectComboBox<String> countryFilter;
    private MultiSelectComboBox<String> tagFilter;
    private ComboBox<String> yearFilter;
    private ComboBox<String> ratingFilter;
    private ComboBox<String> audioLangFilter;

    // Content containers
    private VerticalLayout contentContainer;
    private VerticalLayout searchSection;
    private Button searchToggleButton;
    private boolean searchExpanded = false;
    private boolean filtersActive = false;

    public AbstractProductionListView(VideoManager videoManager, Map<String, ProductionData> streamingProductionData) {
        super(ROUTE_NAME, AbstractProductionPlayerView.ROUTE_NAME, AbstractProductionDetailsView.ROUTE_NAME);
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;

        initializeView();
    }

    private void initializeView() {
        log.info("Initializing production list view for {}", streamingProductionData.size());
        Div scrollableLayoutParent = getScrollableLayoutParent();

        // Add search toggle and filter section
        scrollableLayoutParent.add(createSearchToggleSection());

        // Add collapsible search section (hidden by default)
        searchSection = createSearchAndFilterSection();
        searchSection.setVisible(false);
        scrollableLayoutParent.add(searchSection);

        // Add content container
        contentContainer = new VerticalLayout();
        contentContainer.setPadding(false);
        contentContainer.setSpacing(true);
        contentContainer.setWidthFull();

        scrollableLayoutParent.add(contentContainer);

        // Initial load of content (default view)
        showDefaultContent();

        add(scrollableLayoutParent, new Hr());
    }

    private HorizontalLayout createSearchToggleSection() {
        HorizontalLayout toggleSection = new HorizontalLayout();
        toggleSection.setWidthFull();
        toggleSection.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toggleSection.setAlignItems(FlexComponent.Alignment.CENTER);
        toggleSection.getStyle()
                .set("padding", "10px 20px")
                .set("margin-bottom", "10px");

        H3 title = new H3("Discover Movies & TV Series");

        searchToggleButton = new Button("Search & Filters", new Icon(VaadinIcon.SEARCH));
        searchToggleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        searchToggleButton.addClickListener(e -> toggleSearchSection());

        toggleSection.add(title, searchToggleButton);
        return toggleSection;
    }

    private void toggleSearchSection() {
        searchExpanded = !searchExpanded;
        searchSection.setVisible(searchExpanded);

        if (searchExpanded) {
            searchToggleButton.setText("Hide Search & Filters");
            searchToggleButton.setIcon(new Icon(VaadinIcon.ANGLE_UP));
        } else {
            searchToggleButton.setText("Search & Filters");
            searchToggleButton.setIcon(new Icon(VaadinIcon.SEARCH));
        }
    }

    private VerticalLayout createSearchAndFilterSection() {
        VerticalLayout filterSection = new VerticalLayout();
        filterSection.setPadding(false);
        filterSection.setSpacing(true);
        filterSection.setWidthFull();
        filterSection.addClassName("search-filter-section");
        filterSection.getStyle()
                .set("background-color", "var(--streaming-tile-background)")
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

        filterSection.add(searchLayout, filterControls, actionButtons);
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

        // Type filter (multiple selection)
        typeFilter = new MultiSelectComboBox<>("Types");
        typeFilter.setItems(ProductionDetails.VideoType.values());
        typeFilter.setItemLabelGenerator(type ->
                type.name().replace("_", " ").toLowerCase().replace("tv", "TV"));
        typeFilter.addSelectionListener(e -> applyFilters());
        typeFilter.getStyle().set("min-width", "150px");

        // Category filter
        categoryFilter = new MultiSelectComboBox<>("Categories");
        Set<String> allCategories = streamingProductionData.values().stream()
                .map(ProductionData::getProductionDetails)
                .filter(Objects::nonNull)
                .map(ProductionDetails::getCategories)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        categoryFilter.setItems(allCategories);
        categoryFilter.addSelectionListener(e -> applyFilters());
        categoryFilter.getStyle().set("min-width", "200px");

        // Tags filter
        tagFilter = new MultiSelectComboBox<>("Tags");
        Set<String> allTags = streamingProductionData.values().stream()
                .map(ProductionData::getProductionDetails)
                .filter(Objects::nonNull)
                .map(ProductionDetails::getTags)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        tagFilter.setItems(allTags);
        tagFilter.addSelectionListener(e -> applyFilters());
        tagFilter.getStyle().set("min-width", "180px");

        // Country filter (multiple selection)
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

        // Audio Language filter
        audioLangFilter = new ComboBox<>("Audio Language");
        Set<String> allAudioLangs = streamingProductionData.values().stream()
                .map(ProductionData::getProductionDetails)
                .filter(Objects::nonNull)
                .map(ProductionDetails::getAudioLang)
                .filter(Objects::nonNull)
                .filter(audioLang -> !audioLang.isBlank())
                .collect(Collectors.toSet());
        audioLangFilter.setItems(allAudioLangs);
        audioLangFilter.addValueChangeListener(e -> applyFilters());
        audioLangFilter.getStyle().set("min-width", "150px");

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

        filterLayout.add(typeFilter, categoryFilter, tagFilter, countryFilter, audioLangFilter, yearFilter, ratingFilter);

        return filterLayout;
    }

    private HorizontalLayout createActionButtons() {
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        Button clearFilters = new BervanButton("Clear Filters", new Icon(VaadinIcon.REFRESH), BervanButtonStyle.SECONDARY);
        clearFilters.addClickListener(e -> clearAllFiltersAndShowDefault());

        Button sortByRating = new BervanButton("Sort by Rating", new Icon(VaadinIcon.STAR));
        sortByRating.addClickListener(e -> sortByRating());

        Button sortByYear = new BervanButton("Sort by Year", new Icon(VaadinIcon.CALENDAR));
        sortByYear.addClickListener(e -> sortByYear());

        buttonLayout.add(clearFilters, sortByRating, sortByYear);
        return buttonLayout;
    }

    private void applyFilters() {
        filtersActive = true;

        String searchTerm = searchField.getValue() != null ? searchField.getValue().toLowerCase().trim() : "";
        Set<ProductionDetails.VideoType> selectedTypes = typeFilter.getSelectedItems();
        Set<String> selectedCategories = categoryFilter.getSelectedItems();
        Set<String> selectedTags = tagFilter.getSelectedItems();
        Set<String> selectedCountries = countryFilter.getSelectedItems();
        String selectedAudioLang = audioLangFilter.getValue();
        String selectedYear = yearFilter.getValue();
        String selectedRating = ratingFilter.getValue();

        List<ProductionData> filteredData = streamingProductionData.values().stream()
                .filter(data -> matchesSearchTerm(data, searchTerm))
                .filter(data -> matchesTypes(data, selectedTypes))
                .filter(data -> matchesCategories(data, selectedCategories))
                .filter(data -> matchesTags(data, selectedTags))
                .filter(data -> matchesCountries(data, selectedCountries))
                .filter(data -> matchesAudioLang(data, selectedAudioLang))
                .filter(data -> matchesYear(data, selectedYear))
                .filter(data -> matchesRating(data, selectedRating))
                .collect(Collectors.toList());

        refreshFilteredContent(filteredData);
    }

    private boolean matchesAudioLang(ProductionData data, String selectedAudioLang) {
        if (selectedAudioLang == null) return true;
        ProductionDetails details = data.getProductionDetails();
        return details != null && details.getAudioLang() != null &&
                details.getAudioLang().equals(selectedAudioLang);
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

    private boolean matchesTypes(ProductionData data, Set<ProductionDetails.VideoType> selectedTypes) {
        if (selectedTypes.isEmpty()) return true;
        ProductionDetails details = data.getProductionDetails();
        return details != null && selectedTypes.contains(details.getType());
    }

    private boolean matchesCategories(ProductionData data, Set<String> selectedCategories) {
        if (selectedCategories.isEmpty()) return true;
        ProductionDetails details = data.getProductionDetails();
        if (details == null || details.getCategories() == null) return false;
        return details.getCategories().stream().anyMatch(selectedCategories::contains);
    }

    private boolean matchesTags(ProductionData data, Set<String> selectedTags) {
        if (selectedTags.isEmpty()) return true;
        ProductionDetails details = data.getProductionDetails();
        if (details == null || details.getTags() == null) return false;
        return details.getTags().stream().anyMatch(selectedTags::contains);
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

    private void clearAllFiltersAndShowDefault() {
        searchField.clear();
        typeFilter.clear();
        categoryFilter.clear();
        tagFilter.clear();
        countryFilter.clear();
        yearFilter.clear();
        ratingFilter.clear();
        filtersActive = false;
        showDefaultContent();
    }

    private void sortByRating() {
        // This will only work if filters are active
        if (filtersActive) {
            applyFilters();
        }
    }

    private void sortByYear() {
        // This will only work if filters are active
        if (filtersActive) {
            applyFilters();
        }
    }

    private void showDefaultContent() {
        contentContainer.removeAll();

        // Group productions by categories and sort by count
        Map<String, List<ProductionData>> productionsByCategory = new LinkedHashMap<>();

        // Collect all productions grouped by their categories
        for (ProductionData data : streamingProductionData.values()) {
            ProductionDetails details = data.getProductionDetails();
            if (details != null && details.getCategories() != null) {
                for (String category : details.getCategories()) {
                    productionsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(data);
                }
            } else {
                // Add to "Uncategorized" if no categories
                productionsByCategory.computeIfAbsent("Uncategorized", k -> new ArrayList<>()).add(data);
            }
        }

        // Sort categories by production count (descending)
        List<Map.Entry<String, List<ProductionData>>> sortedCategories = productionsByCategory.entrySet()
                .stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .collect(Collectors.toList());

        // Create sections for each category
        for (Map.Entry<String, List<ProductionData>> categoryEntry : sortedCategories) {
            String categoryName = categoryEntry.getKey();
            List<ProductionData> productions = categoryEntry.getValue();

            if (!productions.isEmpty()) {
                String sectionTitle = "📂 " + categoryName + " (" + productions.size() + ")";
                contentContainer.add(createModernScrollableSection(
                        sectionTitle,
                        createVideoLayout(productions, ROUTE_NAME + "/details")
                ));
            }
        }
    }

    private void refreshFilteredContent(List<ProductionData> filteredData) {
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
                    createVideoLayout(movies, ROUTE_NAME + "/details")
            ));
        }

        // Add TV Series section
        List<ProductionData> tvSeries = groupedByType.get(ProductionDetails.VideoType.TV_SERIES);
        if (tvSeries != null && !tvSeries.isEmpty()) {
            contentContainer.add(createModernScrollableSection(
                    "📺 TV Series (" + tvSeries.size() + ")",
                    createVideoLayout(tvSeries, ROUTE_NAME + "/details")
            ));
        }

        // Add Other section
        List<ProductionData> others = groupedByType.get(ProductionDetails.VideoType.OTHER);
        if (others != null && !others.isEmpty()) {
            contentContainer.add(createModernScrollableSection(
                    "🎭 Other (" + others.size() + ")",
                    createVideoLayout(others, ROUTE_NAME + "/details")
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

    protected HorizontalLayout createVideoLayout(List<ProductionData> productionDataList, String route) {
        HorizontalLayout scrollingLayout = getHorizontalScrollingLayout();

        for (ProductionData productionData : productionDataList) {
            try {
                ProductionDetails productionDetails = productionData.getProductionDetails();
                Metadata productionMainFolder = productionData.getMainFolder();

                String title = productionDetails.getName();
                String year = productionDetails.getReleaseYearStart() != null ?
                        String.valueOf(productionDetails.getReleaseYearStart()) : "N/A";
                String rating = productionDetails.getRating() != null ?
                        String.format("%.1f", productionDetails.getRating()) : "N/A";
                String imageSrc = null;
                if (productionData.getBase64PosterSrc() != null) {
                    imageSrc = productionData.getBase64PosterSrc();
                } else {
                    imageSrc = "/storage/videos/poster/" + productionMainFolder.getId();
                }

                // Create the modern production tile with poster, rating, and hover effects
                VerticalLayout tile = createProductionTile(title, year, rating, imageSrc);

                // Add click navigation
                tile.addClickListener(click ->
                        UI.getCurrent().navigate(route + "/" + productionMainFolder.getId())
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