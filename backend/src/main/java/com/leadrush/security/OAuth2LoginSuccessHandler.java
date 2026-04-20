package com.leadrush.security;

import com.leadrush.auth.dto.AuthResponse;
import com.leadrush.auth.entity.AuthProvider;
import com.leadrush.auth.service.AuthService;
import com.leadrush.config.LeadRushProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Runs after a successful OAuth2 handshake with Google or GitHub. Translates
 * the provider's OAuth2User into a tenant user (create or link), mints JWT
 * tokens, and 302-redirects to the frontend's callback page with the tokens
 * in the query string.
 *
 * Tokens on the URL is a known tradeoff: they'll appear in the user's
 * browser history, but the callback page reads + strips them within a tick.
 * Refresh tokens rotate on use, access tokens are short-lived. The cleaner
 * alternative (a one-time exchange code) is a follow-up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
    private final LeadRushProperties properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        // One big try/catch so ANY failure after the OAuth handshake — attribute
        // extraction, DB write, redirect — routes through a user-friendly
        // /auth/login?error=... redirect instead of leaking Spring's Whitelabel
        // 500. Narrow catches let exceptions bubble to the servlet error page.
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
                log.warn("OAuth2 success handler received non-OAuth2 authentication: {}",
                        authentication.getClass().getSimpleName());
                redirectWithError(response, "oauth_unexpected");
                return;
            }

            String registrationId = oauthToken.getAuthorizedClientRegistrationId();  // "google" | "github"
            AuthProvider provider = switch (registrationId.toLowerCase()) {
                case "google" -> AuthProvider.GOOGLE;
                case "github" -> AuthProvider.GITHUB;
                default -> null;
            };
            if (provider == null) {
                log.warn("Unknown OAuth2 registration id: {}", registrationId);
                redirectWithError(response, "oauth_unknown_provider");
                return;
            }

            OAuth2User oauthUser = oauthToken.getPrincipal();
            AuthService.OAuthProfile profile = extractProfile(provider, oauthUser);
            AuthResponse auth = authService.loginViaOAuth(provider, profile);
            redirectWithTokens(response, provider, auth);
        } catch (Exception e) {
            log.error("OAuth login failed", e);
            redirectWithError(response, "oauth_login_failed");
        }
    }

    /**
     * Normalise Google vs GitHub attribute shapes into our neutral OAuthProfile.
     *
     *   Google attrs:  sub, email, email_verified (Boolean), name, picture
     *   GitHub attrs:  id (int), login, name, email, avatar_url
     *                  — email comes from /user OR LeadRushOAuth2UserService's
     *                    fallback to /user/emails, which only accepts verified
     *                    addresses. So any email we see from GitHub is verified
     *                    by construction.
     */
    private AuthService.OAuthProfile extractProfile(AuthProvider provider, OAuth2User user) {
        Map<String, Object> attrs = user.getAttributes();
        return switch (provider) {
            case GOOGLE -> new AuthService.OAuthProfile(
                    str(attrs.get("sub")),
                    str(attrs.get("email")),
                    str(attrs.get("name")),
                    str(attrs.get("picture")),
                    Boolean.TRUE.equals(attrs.get("email_verified"))
            );
            case GITHUB -> new AuthService.OAuthProfile(
                    str(attrs.get("id")),                              // int → string
                    str(attrs.get("email")),
                    // GitHub's "name" is optional; fall back to login (username).
                    attrs.get("name") != null ? str(attrs.get("name")) : str(attrs.get("login")),
                    str(attrs.get("avatar_url")),
                    // Our custom user service only merges /user/emails entries
                    // where verified=true, so any email we see here is verified.
                    true
            );
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void redirectWithTokens(HttpServletResponse response,
                                     AuthProvider provider,
                                     AuthResponse auth) throws IOException {
        String target = frontendBase()
                + "/auth/callback"
                + "?accessToken=" + enc(auth.getAccessToken())
                + "&refreshToken=" + enc(auth.getRefreshToken())
                + "&provider=" + provider.name()
                + "&email=" + enc(auth.getUser().getEmail());
        getRedirectStrategy().sendRedirect(null, response, target);
    }

    private void redirectWithError(HttpServletResponse response, String code) throws IOException {
        String target = frontendBase() + "/auth/login?error=" + enc(code);
        getRedirectStrategy().sendRedirect(null, response, target);
    }

    private String frontendBase() {
        // frontend-url may be a comma-separated list of allowed CORS origins —
        // when redirecting we only want the first one (the canonical public URL).
        String raw = properties.getFrontendUrl();
        if (raw == null || raw.isBlank()) return "http://localhost:4000";
        String first = raw.split(",")[0].trim();
        return first.endsWith("/") ? first.substring(0, first.length() - 1) : first;
    }

    private static String enc(String v) {
        return v == null ? "" : URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
