package com.cloudcostai.security;

import com.cloudcostai.aws.service.AwsConnectionSessionService;
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
import java.util.List;

@Component
public class AwsConnectionAuthenticationFilter extends OncePerRequestFilter {

    private final AwsConnectionSessionService sessionService;

    public AwsConnectionAuthenticationFilter(AwsConnectionSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/aws/connect".equals(path)
                || "/api/aws/connection".equals(path)
                || "/api/aws/disconnect".equals(path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String connectionToken = request.getHeader("X-AWS-Connection-Token");
        String path = request.getRequestURI();

        if (connectionToken == null || connectionToken.isBlank()) {
            if (isProtectedApi(path)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "AWS connection is required.");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        try {
            var session = sessionService.getSession(connectionToken);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    session.accountId(),
                    connectionToken,
                    List.of(new SimpleGrantedAuthority("ROLE_AWS_USER"))
            );
            authentication.setDetails(session);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            if (isProtectedApi(path)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "AWS connection token is invalid or expired.");
                return;
            }
            filterChain.doFilter(request, response);
        }
    }

    private boolean isProtectedApi(String path) {
        return path.startsWith("/api/")
                && !("/api/aws/connect".equals(path)
                || "/api/aws/connection".equals(path)
                || "/api/aws/disconnect".equals(path));
    }
}
