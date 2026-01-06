package com.bervan.streamingapp.view;

import com.bervan.common.view.AbstractPageView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public abstract class AbstractStreamingPage extends AbstractPageView {

    public AbstractStreamingPage(String route, String... notVisibleButtonRoutes) {
        add(new StreamingPlatformPageLayout(route, notVisibleButtonRoutes));
    }

    protected static HorizontalLayout getHorizontalScrollingLayout() {
        HorizontalLayout scrollingLayout = new HorizontalLayout();
        scrollingLayout.setSpacing(false);
        scrollingLayout.setPadding(false);
        scrollingLayout.getStyle()
                .set("overflow-x", "auto")
                .set("overflow-y", "hidden")
                .set("white-space", "nowrap")
                .set("padding", "10px 0")
                .set("width", "100%")
                .set("scroll-behavior", "smooth")
                .set("-webkit-overflow-scrolling", "touch")
                .set("scrollbar-width", "none")
                .set("-ms-overflow-style", "none");

        // Hide scrollbar completely but keep scrolling functionality
        scrollingLayout.getElement().executeJs(
                "this.style.setProperty('-webkit-scrollbar', 'none', 'important');"
        );
        return scrollingLayout;
    }

    protected Button createNavigationButton(VaadinIcon icon, String tooltip) {
        Button button = new Button(new Icon(icon));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_LARGE);
        button.getStyle()
                .set("background", "rgba(0,0,0,0.1)")
                .set("border-radius", "50%")
                .set("width", "50px")
                .set("height", "50px")
                .set("margin", "0 10px")
                .set("transition", "all 0.3s ease")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)");
        button.getElement().setAttribute("title", tooltip);
        return button;
    }

    protected VerticalLayout createModernScrollableSection(String title, HorizontalLayout contentLayout) {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(false);
        section.setPadding(false);
        section.setWidthFull();
        section.getStyle().set("margin-bottom", "40px");

        H2 sectionTitle = new H2(title);
        sectionTitle.getStyle()
                .set("margin", "0 0 20px 0")
                .set("font-size", "2rem")
                .set("font-weight", "600");

        HorizontalLayout container = new HorizontalLayout();
        container.setWidthFull();
        container.setAlignItems(FlexComponent.Alignment.CENTER);
        container.setSpacing(false);

        Button leftArrow = createNavigationButton(VaadinIcon.CHEVRON_LEFT, "Previous");
        Button rightArrow = createNavigationButton(VaadinIcon.CHEVRON_RIGHT, "Next");

        leftArrow.addClickListener(event ->
                contentLayout.getElement().executeJs("this.scrollBy({left: -345, behavior: 'smooth'})"));
        rightArrow.addClickListener(event ->
                contentLayout.getElement().executeJs("this.scrollBy({left: 345, behavior: 'smooth'})"));

        container.add(leftArrow, contentLayout, rightArrow);
        container.setFlexGrow(1, contentLayout);

        section.add(sectionTitle, container);
        return section;
    }

    protected VerticalLayout getModernTile() {
        VerticalLayout tile = new VerticalLayout();
        tile.addClassName("modern-movie-tile");
        tile.setSpacing(false);
        tile.setPadding(false);
        tile.setWidth("280px");
        tile.setHeight("420px"); // Increased height to accommodate poster aspect ratio
        tile.getStyle()
                .set("position", "relative")
                .set("overflow", "hidden")
                .set("border-radius", "12px")
                .set("cursor", "pointer")
                .set("transition", "all 0.3s ease")
                .set("box-shadow", "0 4px 15px rgba(0, 0, 0, 0.2)");

        // Add hover effect
        tile.getElement().executeJs("""
                    this.addEventListener('mouseenter', () => {
                        this.style.transform = 'scale(1.05)';
                        this.style.boxShadow = '0 8px 25px rgba(0, 0, 0, 0.3)';
                        const hoverOverlay = this.querySelector('.hover-overlay');
                        if (hoverOverlay) {
                            hoverOverlay.style.opacity = '1';
                            hoverOverlay.style.transform = 'translateY(0)';
                        }
                    });
                    this.addEventListener('mouseleave', () => {
                        this.style.transform = 'scale(1)';
                        this.style.boxShadow = '0 4px 15px rgba(0, 0, 0, 0.2)';
                        const hoverOverlay = this.querySelector('.hover-overlay');
                        if (hoverOverlay) {
                            hoverOverlay.style.opacity = '0';
                            hoverOverlay.style.transform = 'translateY(100%)';
                        }
                    });
                """);

        return tile;
    }


    protected H4 getModernTitle(String value) {
        H4 title = new H4(value);
        title.addClassName("modern-movie-title");
        return title;
    }

    protected Image getModernImage(String altText, String imageSrc) {
        String fallbackImageSrc = "images/no_video_image.png";
        Image image = new Image(imageSrc, altText);
        image.setWidth("100%");
        image.setHeight("100%"); // Fill entire tile
        image.getStyle()
                .set("object-fit", "cover")
                .set("border-radius", "12px");

        image.getElement().executeJs(
                "this.onerror = function() { this.onerror = null; this.src = $0; }", fallbackImageSrc
        );

        return image;
    }

    protected Div createRatingBadge(String rating) {
        Div ratingBadge = new Div();
        ratingBadge.setText(rating);
        ratingBadge.addClassName("rating-badge");
        ratingBadge.getStyle()
                .set("position", "absolute")
                .set("bottom", "10px")
                .set("right", "10px")
                .set("background", "rgba(0, 0, 0, 0.8)")
                .set("color", "white")
                .set("padding", "4px 8px")
                .set("border-radius", "6px")
                .set("font-size", "0.9rem")
                .set("font-weight", "600")
                .set("z-index", "2")
                .set("backdrop-filter", "blur(10px)");

        return ratingBadge;
    }

    protected Div createHoverOverlay(String title, String subTitle) {
        Div hoverOverlay = new Div();
        hoverOverlay.addClassName("hover-overlay");

        H4 titleElement = new H4(title);
        titleElement.getStyle()
                .set("margin", "0 0 5px 0")
                .set("color", "white")
                .set("font-size", "1.1rem")
                .set("font-weight", "600")
                .set("text-shadow", "2px 2px 4px rgba(0, 0, 0, 0.8)");

        Div yearElement = new Div(subTitle);
        yearElement.getStyle()
                .set("color", "#ccc")
                .set("font-size", "0.9rem")
                .set("margin", "0");

        hoverOverlay.add(titleElement, yearElement);
        hoverOverlay.getStyle()
                .set("position", "absolute")
                .set("bottom", "0")
                .set("left", "0")
                .set("right", "0")
                .set("background", "linear-gradient(transparent, rgba(0, 0, 0, 0.9))")
                .set("padding", "40px 15px 15px 15px")
                .set("color", "white")
                .set("opacity", "0")
                .set("transform", "translateY(100%)")
                .set("transition", "all 0.3s ease")
                .set("z-index", "3")
                .set("border-radius", "0 0 12px 12px");

        return hoverOverlay;
    }


    protected Div getScrollableLayoutParent() {
        Div result = new Div();
        result.addClassName("scrollable-layout-parent");
        result.getStyle()
                .set("background", "var(--streaming-tile-background)")
                .set("backdrop-filter", "blur(10px)")
                .set("border-radius", "20px 20px 0 0")
                .set("margin-top", "-20px")
                .set("padding", "40px 20px")
                .set("position", "90%")
                .set("width", "90%")
                .set("margin-left", "5%")
                .set("margin-right", "5%")
                .set("z-index", "1");
        return result;
    }

    protected VerticalLayout createProductionTile(String title, String year, String rating,
                                                  String imageSrc) {
        VerticalLayout tile = getModernTile();

        // Create poster image
        Image poster = getModernImage(title, imageSrc);

        // Create rating badge
        Div ratingBadge = createRatingBadge(rating);

        // Create hover overlay with title and year
        Div hoverOverlay = createHoverOverlay(title, year);

        // Add all components to tile
        tile.add(poster);
        tile.getElement().appendChild(ratingBadge.getElement());
        tile.getElement().appendChild(hoverOverlay.getElement());

        return tile;
    }


}
