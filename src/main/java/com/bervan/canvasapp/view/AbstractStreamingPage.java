package com.bervan.canvasapp.view;

import com.bervan.common.AbstractPageView;

public abstract class AbstractStreamingPage extends AbstractPageView {

    public AbstractStreamingPage(String route) {
        add(new StreamingPlatformPageLayout(route));
    }
}
