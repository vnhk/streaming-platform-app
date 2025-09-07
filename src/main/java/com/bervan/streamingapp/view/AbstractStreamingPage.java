package com.bervan.streamingapp.view;

import com.bervan.common.view.AbstractPageView;
import com.bervan.filestorage.model.Metadata;
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

import java.util.List;

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
                .set("color", "#333")
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
        tile.getStyle()
                .set("margin", "0 15px 0 0")
                .set("cursor", "pointer")
                .set("display", "inline-block")
                .set("min-width", "280px")
                .set("width", "280px")
                .set("height", "300px")
                .set("border-radius", "12px")
                .set("overflow", "hidden")
                .set("background", "white")
                .set("box-shadow", "0 8px 32px rgba(0,0,0,0.12)")
                .set("transition", "all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1)")
                .set("transform", "translateY(0)")
                .set("border", "1px solid rgba(0,0,0,0.1)")
                .set("flex-shrink", "0");

        // Add hover effect
        tile.getElement().executeJs(
                "this.addEventListener('mouseenter', () => {" +
                        "this.style.transform = 'translateY(-8px)';" +
                        "this.style.boxShadow = '0 20px 40px rgba(0,0,0,0.2)';" +
                        "});" +
                        "this.addEventListener('mouseleave', () => {" +
                        "this.style.transform = 'translateY(0)';" +
                        "this.style.boxShadow = '0 8px 32px rgba(0,0,0,0.12)';" +
                        "});"
        );

        return tile;
    }


    protected H4 getModernTitle(String value) {
        H4 title = new H4(value);
        title.getStyle()
                .set("text-align", "center")
                .set("margin", "15px 0 0 0")
                .set("color", "#333")
                .set("font-size", "1rem")
                .set("font-weight", "500")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");
        return title;
    }

    protected Image getModernImage(String text, String imageSrc, List<Metadata> defaultPoster) {
        String fallbackImageSrc;
        if (defaultPoster != null && !defaultPoster.isEmpty()) {
            fallbackImageSrc = "/storage/videos/poster/direct/" + defaultPoster.get(0).getId();
        } else {
            fallbackImageSrc = "images/no_video_image.png";
        }
        Image image = new Image(imageSrc, text);
        image.setWidth("100%");
        image.setHeight("240px");
        image.getStyle()
                .set("object-fit", "cover")
                .set("border-radius", "12px");

        image.getElement().executeJs(
                "this.onerror = function() { this.onerror = null; this.src = $0; }", fallbackImageSrc
        );

        return image;
    }

    protected Div getScrollableLayoutParent() {
        Div result = new Div();
        result.addClassName("scrollable-layout-parent");
        result.getStyle()
                .set("background", "rgba(255,255,255,0.95)")
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

}
