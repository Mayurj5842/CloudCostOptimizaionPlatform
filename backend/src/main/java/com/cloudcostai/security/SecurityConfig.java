package com.cloudcostai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Value("${app.cors.allowed-origins:http://localhost:3000}")
        private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/me", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/notifications/**").permitAll()
                        .requestMatchers("/api/aws/connect").permitAll()
                        .requestMatchers("/api/aws/connection").permitAll()
                        .requestMatchers("/api/aws/disconnect").permitAll()
                        .requestMatchers("/api/aws/cost-analytics").permitAll()
                        .requestMatchers("/api/aws/resources").permitAll()
                        .requestMatchers("/api/aws/dashboard/**").permitAll()
                        .requestMatchers("/api/aws/live-cost").permitAll()
                        .requestMatchers("/api/aws/recent-activity").permitAll()
                        .requestMatchers("/api/aws/insights").permitAll()
                        .requestMatchers("/api/aws/reports/**").permitAll()
                        .requestMatchers("/api/aws/recommendations/**").permitAll()
                        .requestMatchers("/api/budgets/**").permitAll()
                        .requestMatchers("/api/alerts/**").permitAll()
                        .requestMatchers("/api/recommendations/**").permitAll()
                        .requestMatchers("/api/billing/**").permitAll()
                        .requestMatchers("/api/aws/bills").permitAll()
                        .requestMatchers("/api/system/diagnostics").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isBlank())
                                .collect(Collectors.toList()));
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/api/**", configuration);
                return source;
        }
}