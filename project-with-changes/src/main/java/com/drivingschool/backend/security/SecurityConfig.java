package com.drivingschool.backend.security;

import com.drivingschool.backend.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

import java.util.Arrays;

/**
 * Security Configuration for the Driving School Backend
 * 
 * Implements:
 * - JWT-based stateless authentication
 * - Role-Based Access Control (RBAC)
 * - API Versioning (/api/v1)
 * - Rate limiting on authentication endpoints
 * - Security headers (CSP, HSTS, X-Frame-Options, etc.)
 * - Profile-aware endpoint protection (Dev vs Production)
 * 
 * Key Security Features:
 * 1. All endpoints require authentication by default (except public auth endpoints)
 * 2. Swagger/API docs public in dev/test, disabled entirely in production
 * 3. Schools list restricted to authenticated users only (not public)
 * 4. Rate limiting on login/register endpoints (10 req/min)
 * 5. CORS restricted to configured origins only
 * 6. HSTS headers enabled in production
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final ApiVersioningFilter apiVersioningFilter;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitingFilter rateLimitingFilter,
                          ApiVersioningFilter apiVersioningFilter,
                          PasswordEncoder passwordEncoder,
                          Environment environment,
                          CustomAuthenticationEntryPoint authenticationEntryPoint,
                          CustomAccessDeniedHandler accessDeniedHandler) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.apiVersioningFilter = apiVersioningFilter;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        http
                // CSRF: Disabled for stateless JWT architecture
                .csrf(csrf -> csrf.disable())
                
                // CORS: Configured via CorsConfig
                .cors(cors -> {})
                
                // Session Management: Stateless JWT tokens only
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionFixation(sessionFixation -> sessionFixation.migrateSession()))

                // Exception Handling: without this, Spring Security's default
                // AuthenticationEntryPoint (Http403ForbiddenEntryPoint, since no
                // formLogin/httpBasic is configured) returns 403 for EVERY auth
                // failure - missing, expired, or invalid JWT alike - instead of 401.
                // Clients whose token-refresh logic watches for 401 never get the
                // signal to refresh. This restores the correct 401 vs 403 split:
                // 401 = not authenticated at all, 403 = authenticated but not permitted.
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                // Security Headers: Comprehensive protection against common attacks
                .headers(headers -> headers
                        // CSP: Restrict script execution to origin only
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self'; " +
                                        "connect-src 'self'; " +
                                        "form-action 'self'; " +
                                        "frame-ancestors 'none'"))

                        // HSTS: Force HTTPS in production
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(isProduction ? 31536000L : 3600L)  // 1 year prod, 1 hour dev
                                .includeSubDomains(true)
                                .preload(isProduction))

                        // X-Frame-Options: Prevent clickjacking
                        .frameOptions(frameOptions -> frameOptions.deny())

                        // X-Content-Type-Options: Prevent MIME-type sniffing
                        .contentTypeOptions(contentTypeOptions -> {
                        })

                        // X-XSS-Protection: Legacy XSS protection
                        .xssProtection(xss -> xss.headerValue(
                                XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))

                        // Referrer-Policy: Control referrer information
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))

                        // Custom headers
                        .addHeaderWriter(new StaticHeadersWriter("X-API-Version", "1.0"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Frame-Options", "DENY"))
                        .addHeaderWriter(new StaticHeadersWriter("X-XSS-Protection", "1; mode=block")).permissionsPolicyHeader(permissions -> permissions
                                .policy("geolocation=(), microphone=(), camera=(), payment=(), usb=()")))
                
                // Authorization Rules: API versioning with /api/v1 prefix
                .authorizeHttpRequests(auth -> auth
                        // Public authentication endpoints - Rate limited
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/refresh-token",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/verification/send",
                                "/api/v1/auth/verification/confirm").permitAll()
                        
                        // WebSocket handshake: browsers can't send an Authorization header on
                        // it, so the access token is checked on the STOMP CONNECT frame instead
                        // (StompAuthChannelInterceptor) - nothing is reachable before that.
                        .requestMatchers("/ws", "/ws/**").permitAll()

                        // All other auth endpoints require authentication
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        
                        // Schools: Now restricted to authenticated users only (was public)
                        .requestMatchers(HttpMethod.GET, "/api/v1/schools").authenticated()
                        .requestMatchers("/api/v1/schools/**").hasRole("ADMIN")

                        // School deletion requests: bootstrap-admin review queue (fine-grained
                        // bootstrap-only check happens in the service, same as school create/delete)
                        .requestMatchers("/api/v1/school-deletion-requests/**").hasRole("ADMIN")
                        
                        // API Documentation: public in dev/test for convenience, but
                        // never in production - also disabled outright via
                        // springdoc.api-docs.enabled/swagger-ui.enabled in
                        // application-prod.yml; this is defense in depth in case
                        // that config is ever removed.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**")
                        .access((authentication, context) -> new AuthorizationDecision(!isProduction))

                        // OPTIONS: Allow CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        
                        // Actuator: Health checks public, rest requires ADMIN
                        // (Spring Boot's actual probe-group paths are "liveness"/"readiness",
                        // not "live"/"ready" - verified live; the short forms 404 and would
                        // otherwise fall through to the ADMIN-only rule below, breaking
                        // container-orchestrator health checks.)
                        .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        
                        // Default: All other requests require authentication (SECURE DEFAULT)
                        .anyRequest().authenticated())
                
                // Authentication provider
                .authenticationProvider(authenticationProvider())
                
                // Filter chain: API versioning first, then rate limiting, then JWT auth
                .addFilterBefore(apiVersioningFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
