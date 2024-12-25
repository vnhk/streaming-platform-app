package com.bervan.streamingapp.view;

import com.bervan.common.AbstractPageView;

public abstract class AbstractStreamingPage extends AbstractPageView {

    public AbstractStreamingPage(String route, String... notVisibleButtonRoutes) {
        add(new StreamingPlatformPageLayout(route, notVisibleButtonRoutes));
    }
}
