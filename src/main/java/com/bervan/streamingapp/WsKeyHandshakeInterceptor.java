package com.bervan.streamingapp;

import com.bervan.logging.JsonLogger;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WsKeyHandshakeInterceptor implements HandshakeInterceptor {

    private final WsKeyService wsKeyService;
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");

    public WsKeyHandshakeInterceptor(WsKeyService wsKeyService) {
        this.wsKeyService = wsKeyService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        URI uri = request.getURI();
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String key = params.getFirst("key");
        String roomId = params.getFirst("roomId");

        if (key != null && roomId != null) {
            Optional<UUID> userIdOpt = wsKeyService.validateAndConsume(key, roomId);
            if (userIdOpt.isPresent()) {
                attributes.put("userId", userIdOpt.get());
                log.info("WsKeyHandshakeInterceptor: key validated, userId=" + userIdOpt.get() + " roomId=" + roomId);
                return true;
            } else {
                log.warn("WsKeyHandshakeInterceptor: validateAndConsume returned empty for key=" + key + " roomId=" + roomId);
            }
        } else {
            log.warn("WsKeyHandshakeInterceptor: missing params — key=" + key + " roomId=" + roomId);
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}

