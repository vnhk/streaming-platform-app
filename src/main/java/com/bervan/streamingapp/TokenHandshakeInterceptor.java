package com.bervan.streamingapp;

import com.bervan.common.user.User;
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

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userIdFromSession = null;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            userIdFromSession = ((User) auth.getPrincipal()).getId();
        } else if (token == null || tokenService.resolveUserId(token).isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } else {
            userIdFromSession = tokenService.resolveUserId(token).get();
        }

        attributes.put("userId", userIdFromSession);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}