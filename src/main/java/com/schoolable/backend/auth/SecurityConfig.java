package com.schoolable.backend.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
public class SecurityConfig {

    private final JwtService jwtService;
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private String corsAllowedOrigins;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> {
                            log.warn("Unauthorized request to {} {} (auth header present: {})",
                                req.getMethod(), req.getRequestURI(), req.getHeader("Authorization") != null);
                            
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Unauthorized\"}}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            log.warn("Access denied to {}", req.getRequestURI());
                            
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Forbidden\"}}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/ws/**",
                                "/ws-native/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/up", "/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/verify-link", "/auth/debug").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/verify-link").permitAll()
                        .requestMatchers(HttpMethod.GET, "/storage/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/signup", "/auth/verify-email", "/auth/resend-verification").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/reset-password", "/auth/verify-reset-code", "/auth/complete-reset").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/hr/seed").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    static class JwtAuthFilter extends OncePerRequestFilter {
        private final JwtService jwtService;
        private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

        JwtAuthFilter(JwtService jwtService) {
            this.jwtService = jwtService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
            String authHeader = request.getHeader("Authorization");
            String method = request.getMethod();
            String uri = request.getRequestURI();
            
            // Only log important endpoints to reduce noise
            if (uri.contains("/tasks") || uri.contains("/profile")) {
                log.debug("JwtAuthFilter: {} {} (auth header present: {})", method, uri, authHeader != null);
            }
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    Claims claims = jwtService.parse(token);
                    UUID userId = UUID.fromString(claims.getSubject());
                    String role = claims.get("role", String.class);
                    if (uri.contains("/tasks") || uri.contains("/profile")) {
                        log.debug("JWT parsed successfully for userId={}", userId);
                    }
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())) : List.of()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception e) {
                    log.warn("JWT parse failed: {} {}", e.getClass().getSimpleName(), e.getMessage());
                    // Don't return here, let the entry point handle it for meaningful 401s
                }
            } else {
                if (uri.contains("/tasks") || uri.contains("/profile")) {
                    log.debug("No Bearer token in request");
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
