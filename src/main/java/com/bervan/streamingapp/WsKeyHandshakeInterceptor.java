package com.bervan.streamingapp;

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
                return true;
            }
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

