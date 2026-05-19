package com.bervan.streamingapp;

import com.bervan.common.user.User;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.tv.TvAccessTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenHandshakeInterceptor implements HandshakeInterceptor {
    private final TvAccessTokenService tokenService;
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");

    public TokenHandshakeInterceptor(TvAccessTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        URI uri = request.getURI();
        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String token = queryParams.getFirst("token");
        String key = queryParams.getFirst("key");
        String roomId = queryParams.getFirst("roomId");

        if (key != null) {
            log.info("TokenHandshakeInterceptor: delegating to WsKeyHandshakeInterceptor for key=" + key + " roomId=" + roomId);
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userIdFromSession = null;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            userIdFromSession = ((User) auth.getPrincipal()).getId();
            log.info("TokenHandshakeInterceptor: authenticated via session, userId=" + userIdFromSession);
        } else if (token == null) {
            log.warn("TokenHandshakeInterceptor: no session, no token, no key — rejecting");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } else if (tokenService.resolveUserId(token).isEmpty()) {
            log.warn("TokenHandshakeInterceptor: invalid token, rejecting — token=" + token);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } else {
            userIdFromSession = tokenService.resolveUserId(token).get();
            log.info("TokenHandshakeInterceptor: resolved userId via token, userId=" + userIdFromSession + " token=" + token);
        }

        attributes.put("userId", userIdFromSession);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}