package com.bervan.streamingapp.view;


import com.bervan.common.MenuNavigationComponent;

public final class StreamingPlatformPageLayout extends MenuNavigationComponent {

    public StreamingPlatformPageLayout(String routeName, String... notVisibleButtonRoutes) {
        super(routeName, notVisibleButtonRoutes);

        addButtonIfVisible(menuButtonsRow, AbstractVideoListView.ROUTE_NAME, "Home");
        addButtonIfVisible(menuButtonsRow, AbstractVideoDetailsView.ROUTE_NAME, "Details");
        addButtonIfVisible(menuButtonsRow, AbstractVideoPlayerView.ROUTE_NAME, "Player");

        add(menuButtonsRow);
    }
}
