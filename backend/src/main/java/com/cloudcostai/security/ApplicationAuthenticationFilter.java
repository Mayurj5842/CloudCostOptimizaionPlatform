package com.cloudcostai.security;

import com.cloudcostai.auth.model.AppSession;
import com.cloudcostai.auth.repository.AppSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class ApplicationAuthenticationFilter extends OncePerRequestFilter {

    private final AppSessionRepository sessionRepository;

    public ApplicationAuthenticationFilter(AppSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/auth/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (SecurityContextHolder.getContext().getAuthentication() == null
            && header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            sessionRepository.findById(token)
                    .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                    .map(AppSession::getUser)
                    .ifPresent(user -> {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                user.getId(), token, List.of(new SimpleGrantedAuthority("ROLE_APP_USER")));
                        org.springframework.security.core.context.SecurityContextHolder.getContext()
                                .setAuthentication(authentication);
                    });
        }
        filterChain.doFilter(request, response);
    }
}