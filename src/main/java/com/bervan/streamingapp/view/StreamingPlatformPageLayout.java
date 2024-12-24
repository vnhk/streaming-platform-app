package com.bervan.streamingapp.view;


import com.bervan.common.MenuNavigationComponent;

public final class StreamingPlatformPageLayout extends MenuNavigationComponent {

    public StreamingPlatformPageLayout(String routeName) {
        super(routeName);

        addButton(menuButtonsRow, AbstractVideoListView.ROUTE_NAME, "Home");
        addButton(menuButtonsRow, AbstractVideoPlayerView.ROUTE_NAME, "Player");

        add(menuButtonsRow);
    }
}
