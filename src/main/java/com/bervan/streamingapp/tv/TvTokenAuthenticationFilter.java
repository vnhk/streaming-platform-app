package com.bervan.streamingapp.tv;

import com.bervan.common.user.User;
import com.bervan.common.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Security filter that turns X-Auth-Token header into an authenticated user.
 *
 * This allows TV browser (second app) to call the main API using only the token
 * issued by the main server. No login form on the TV app is required.
 */
@Component
public class TvTokenAuthenticationFilter extends OncePerRequestFilter {

    private final TvAccessTokenService tvAccessTokenService;
    private final UserRepository userRepository;

    public TvTokenAuthenticationFilter(TvAccessTokenService tvAccessTokenService,
                                       UserRepository userRepository) {
        this.tvAccessTokenService = tvAccessTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Do not override existing authentication (normal web login, OTP, etc.)
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing == null || !existing.isAuthenticated()) {
            String token = request.getHeader("X-Auth-Token");
            Optional<UUID> userIdOpt = tvAccessTokenService.resolveUserId(token);
            if (userIdOpt.isPresent()) {
                UUID userId = userIdOpt.get();
                Optional<User> userOpt = userRepository.findById(userId);
                userOpt.ifPresent(user -> {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }

        filterChain.doFilter(request, response);
    }
}

