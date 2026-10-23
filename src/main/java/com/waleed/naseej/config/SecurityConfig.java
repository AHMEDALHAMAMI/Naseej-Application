package com.waleed.naseej.config;

import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.security.JwtAuthenticationFilter;
import com.waleed.naseej.security.JwtService;
import com.waleed.naseej.security.OAuth2LoginSuccessHandler;
import com.waleed.naseej.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;

/**
 * server.servlet.context-path=/api/v1 is removed by Spring Security before matching,
 * so all paths here are written WITHOUT /api/v1.
 * The URLs clients call still start with /api/v1.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATH = PathPatternRequestMatcher.withDefaults();

    private static RequestMatcher[] paths(String... patterns) {
        return Arrays.stream(patterns).map(PATH::matcher).toArray(RequestMatcher[]::new);
    }

    private static RequestMatcher[] paths(HttpMethod method, String... patterns) {
        return Arrays.stream(patterns).map(p -> PATH.matcher(method, p)).toArray(RequestMatcher[]::new);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UserRepository userRepository,
                                                   RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                                                   OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints
                        .requestMatchers(paths(
                                "/auth/register",
                                "/auth/login",
                                "/auth/send-otp",
                                "/auth/verify-otp",
                                "/auth/reset-password",
                                "/auth/refresh")).permitAll()
                        // Google sign-in + error page
                        .requestMatchers(paths("/oauth2/**", "/login/oauth2/**", "/error")).permitAll()
                        // Sign-up page needs the neighborhoods list before the user has an account
                        .requestMatchers(paths(HttpMethod.GET,
                                "/neighborhoods/get-all",
                                "/neighborhoods/get/**",
                                "/neighborhoods/get-by-city/**",
                                "/neighborhoods/search/**")).permitAll()
                        // Moyasar calls these directly (no JWT); the webhook checks its own X-Webhook-Secret
                        .requestMatchers(paths(HttpMethod.POST, "/payments/webhook/moyasar")).permitAll()
                        .requestMatchers(paths(HttpMethod.GET, "/payments/moyasar-callback")).permitAll()
                        // Logged in but maybe still PENDING (Google user before choosing a membership)
                        .requestMatchers(paths("/auth/**")).authenticated()
                        // Admin CRUD
                        .requestMatchers(paths("/users/**", "/neighborhoods/**")).hasRole("ADMIN")
                        // Everything else: a real membership is required, PENDING is blocked
                        .anyRequest().hasAnyRole("RESIDENT", "INVESTOR", "LANDLORD", "ADMIN"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAuthenticationEntryPoint))
                .oauth2Login(oauth -> oauth
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler((request, response, exception) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Google sign-in failed")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}