package com.waleed.naseej.security;

import com.waleed.naseej.dto.out.AuthResponse;
import com.waleed.naseej.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * After Google sign-in: find or create the user, then return our own JWT tokens as JSON.
 * When "roleSelectionRequired" is true the client shows the membership page and calls PUT /auth/select-role.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();

        AuthResponse tokens = authService.loginWithGoogle(
                googleUser.getAttribute("sub"),
                googleUser.getAttribute("email"),
                googleUser.getAttribute("name"));

        // The session was only needed for the Google redirect; the API itself is stateless
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(tokens));
    }
}