package com.schoolable.backend.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
                            System.out.println("❌ UNAUTHORIZED REQUEST to " + req.getRequestURI());
                            System.out.println("   Method: " + req.getMethod());
                            System.out.println("   Exception: " + ex.getMessage());
                            System.out.println("   Headers: Authorization=" + (req.getHeader("Authorization") != null ? "Present" : "Missing"));
                            
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Unauthorized: " + ex.getMessage() + "\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            System.out.println("🚫 ACCESS DENIED to " + req.getRequestURI());
                            
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Forbidden\"}");
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
        configuration.setAllowedOrigins(Arrays.asList("*"));
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
                System.out.println("🔐 JwtAuthFilter: " + method + " " + uri);
                System.out.println("   Authorization header: " + (authHeader != null ? "Present (" + authHeader.length() + " chars)" : "MISSING"));
            }
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    Claims claims = jwtService.parse(token);
                    UUID userId = UUID.fromString(claims.getSubject());
                    String role = claims.get("role", String.class);
                    if (uri.contains("/tasks") || uri.contains("/profile")) {
                        System.out.println("   ✅ JWT parsed successfully: userId=" + userId);
                    }
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())) : List.of()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception e) {
                    System.out.println("   ❌ JWT parse FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    // Don't return here, let the entry point handle it for meaningful 401s
                }
            } else {
                if (uri.contains("/tasks") || uri.contains("/profile")) {
                    System.out.println("   ⚠️ No Bearer token in request");
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
