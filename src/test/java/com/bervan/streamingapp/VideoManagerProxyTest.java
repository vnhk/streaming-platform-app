package com.bervan.streamingapp;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoManagerProxyTest {
    @Test
    void readsConfiguredStorageFolderThroughTheSpringProxy() {
        VideoManager target = new VideoManager(null, null, null);
        ReflectionTestUtils.setField(target, "appFolder", "/Streaming Platform");
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        VideoManager proxy = (VideoManager) factory.getProxy();

        assertEquals("/Streaming Platform", proxy.getAppFolder());
    }
}
