package com.waleed.naseej.security;

import com.waleed.naseej.dto.out.AuthResponse;
import com.waleed.naseej.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * After Google sign-in: find or create the user, then send the browser back to the website with our own
 * JWT tokens in the URL fragment (#...). The fragment never reaches any server or log; the page reads it,
 * stores the tokens and removes it from the address bar.
 * When roleSelectionRequired is true the website shows the membership page, which calls PUT /auth/select-role.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    // Page under the context path that receives the tokens
    @Value("${app.frontend.oauth-callback-path:/oauth-callback.html}")
    private String callbackPath;

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

        String fragment = "accessToken=" + encode(tokens.getAccessToken())
                + "&refreshToken=" + encode(tokens.getRefreshToken())
                + "&expiresIn=" + tokens.getExpiresIn()
                + "&roleSelectionRequired=" + Boolean.TRUE.equals(tokens.getRoleSelectionRequired());
        response.sendRedirect(request.getContextPath() + callbackPath + "#" + fragment);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
