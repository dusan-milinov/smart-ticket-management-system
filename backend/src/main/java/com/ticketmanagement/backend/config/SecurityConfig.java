package com.ticketmanagement.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManager;
import com.ticketmanagement.backend.security.JwtCookieService;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

        private static void writeError(HttpServletResponse res, int status, String message) throws IOException {
                res.setStatus(status);
                res.setContentType("application/json");
                res.getWriter().write("{\"message\":\"" + message + "\"}");
        }

        @Bean
        public BearerTokenResolver bearerTokenResolver(JwtCookieService jwtCookieService) {
                DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
                return request -> {
                        // Public endpoints must not fail because of a stale cookie.
                        String path = request.getRequestURI().substring(request.getContextPath().length());
                        if (path.startsWith("/auth/")
                                        || path.startsWith("/v3/api-docs")
                                        || path.startsWith("/swagger-ui")) {
                                return null;
                        }
                        String token = jwtCookieService.resolve(request);
                        // Header fallback keeps Swagger and curl usable.
                        return token != null ? token : headerResolver.resolve(request);
                };
        }

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        JwtAuthenticationConverter jwtAuthenticationConverter,
                        BearerTokenResolver bearerTokenResolver) {
                return http
                                .cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(
                                                                SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                                "/auth/**",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html").permitAll()
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/agent/**").hasRole("SUPPORT_AGENT")
                                                .requestMatchers("/tickets/**")
                                                .hasAnyRole("ADMIN", "SUPPORT_AGENT", "USER")
                                                .requestMatchers("/comments/**").hasAnyRole("ADMIN", "SUPPORT_AGENT", "USER")
                                                .requestMatchers("/ticket-events/**").hasAnyRole("ADMIN", "SUPPORT_AGENT", "USER") // ("ADMIN", "SUPPORT_AGENT")?
                                                .anyRequest().authenticated())
                                .formLogin(form -> form.disable())
                                .httpBasic(basic -> basic.disable())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .bearerTokenResolver(bearerTokenResolver)
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                                                .authenticationEntryPoint(
                                                                (req, res, ex) -> writeError(res, 401, "Unauthorized"))
                                                .accessDeniedHandler(
                                                                (req, res, ex) -> writeError(res, 403, "Forbidden")))
                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint(
                                                                (req, res, ex) -> writeError(res, 401, "Unauthorized"))
                                                .accessDeniedHandler(
                                                                (req, res, ex) -> writeError(res, 403, "Forbidden")))
                                .build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration configuration) throws Exception {
                return configuration.getAuthenticationManager();
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
                authoritiesConverter.setAuthorityPrefix("");
                authoritiesConverter.setAuthoritiesClaimName("roles");

                JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
                authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
                return authenticationConverter;
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource(
                        @Value("${app.cors.allowed-origins}") String frontendUrl) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(List.of(frontendUrl));
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
