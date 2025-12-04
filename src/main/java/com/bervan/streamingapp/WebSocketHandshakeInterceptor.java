package com.bervan.streamingapp;

import com.bervan.logging.JsonLogger;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        URI uri = request.getURI();
        String query = uri.getQuery();

        if (query != null) {
            // Parse query parameters manually
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];

                    if ("deviceType".equals(key)) {
                        attributes.put("deviceType", value);
                    } else if ("roomId".equals(key)) {
                        attributes.put("roomId", value);
                    }
                }
            }
        }

        log.info("WebSocket handshake - deviceType: " + attributes.get("deviceType") +
                ", roomId: " + attributes.get("roomId"));

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do after handshake
    }

}
