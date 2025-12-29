package com.bervan.streamingapp.view;


import com.bervan.common.MenuNavigationComponent;
import com.bervan.common.service.AuthService;
import com.vaadin.flow.component.icon.VaadinIcon;

public final class StreamingPlatformPageLayout extends MenuNavigationComponent {

    public StreamingPlatformPageLayout(String routeName, String... notVisibleButtonRoutes) {
        super(routeName, notVisibleButtonRoutes);

        addButtonIfVisible(menuButtonsRow, AbstractProductionListView.ROUTE_NAME, "Home", VaadinIcon.HOME.create());
        addButtonIfVisible(menuButtonsRow, AbstractProductionDetailsView.ROUTE_NAME, "Details", VaadinIcon.FILE_TEXT.create());
        addButtonIfVisible(menuButtonsRow, AbstractProductionPlayerView.ROUTE_NAME, "Player", VaadinIcon.PLAY_CIRCLE.create());
        if (AuthService.getUserRole().equals("ROLE_USER")) {
            addButtonIfVisible(menuButtonsRow, AbstractRemoteControlView.ROUTE_NAME, "Remote Control", VaadinIcon.CONTROLLER.create());
        }
        add(menuButtonsRow);
    }
}
