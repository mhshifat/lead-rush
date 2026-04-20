package com.leadrush.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps Spring's default OAuth2UserService with a GitHub-specific workaround.
 *
 * <h3>Why this exists</h3>
 *
 * Spring's default service calls the provider's userinfo endpoint once and
 * surfaces whatever attributes come back. For GitHub that means {@code /user},
 * which returns {@code email} ONLY if the user has flipped "public email" on
 * their profile. Most developers leave it private — so the email attribute
 * arrives as {@code null}, our AuthService throws {@link com.leadrush.common.exception.BusinessException}
 * ("account has no public email"), and the user hits a 500.
 *
 * When the {@code user:email} scope is granted, GitHub exposes private addresses
 * via a separate endpoint: {@code GET https://api.github.com/user/emails}. This
 * service detects the "GitHub with null email" case and makes the second call
 * to pull the primary verified address, then merges it into the attribute map
 * before handing the {@link OAuth2User} back to Spring Security.
 *
 * Google's userinfo endpoint always returns a verified email when the
 * {@code email} scope is granted — no fallback needed for that provider.
 */
@Service
@Slf4j
public class LeadRushOAuth2UserService extends DefaultOAuth2UserService {

    private static final String GITHUB_EMAILS_ENDPOINT = "https://api.github.com/user/emails";
    private static final String EMAIL_ATTRIBUTE = "email";
    /**
     * GitHub's OAuth2User is built with "id" as the name attribute by default.
     * We preserve that when we reconstruct the DefaultOAuth2User below so
     * downstream code (our success handler's {@code str(attrs.get("id"))})
     * keeps finding it.
     */
    private static final String GITHUB_NAME_ATTRIBUTE = "id";

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"github".equalsIgnoreCase(registrationId)) {
            return user;
        }

        Object existingEmail = user.getAttributes().get(EMAIL_ATTRIBUTE);
        if (existingEmail instanceof String s && !s.isBlank()) {
            // Email is public on the profile — nothing to do.
            return user;
        }

        // Fetch the primary verified email from the secondary endpoint.
        String primaryEmail = fetchPrimaryGithubEmail(userRequest.getAccessToken().getTokenValue());
        if (primaryEmail == null) {
            // User hasn't granted user:email scope OR has no verified email at all.
            // Surface the original user unchanged; downstream will fail with our
            // user-friendly "no public email" error which routes through the
            // OAuth2LoginSuccessHandler's try/catch.
            log.warn("GitHub OAuth: no primary verified email found for user id={}",
                    user.getAttributes().get("id"));
            return user;
        }

        // Merge the fetched email into a new attribute map and rebuild the user.
        Map<String, Object> merged = new HashMap<>(user.getAttributes());
        merged.put(EMAIL_ATTRIBUTE, primaryEmail);
        return new DefaultOAuth2User(user.getAuthorities(), merged, GITHUB_NAME_ATTRIBUTE);
    }

    private String fetchPrimaryGithubEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    GITHUB_EMAILS_ENDPOINT,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> emails = response.getBody();
            if (emails == null) return null;

            // Prefer primary + verified. Fall back to any verified. Never return
            // an unverified email — would let an attacker claim a linked account
            // by adding (but not verifying) a victim's address on their GitHub.
            String primary = pickEmail(emails, m ->
                    Boolean.TRUE.equals(m.get("primary")) && Boolean.TRUE.equals(m.get("verified")));
            if (primary != null) return primary;
            return pickEmail(emails, m -> Boolean.TRUE.equals(m.get("verified")));
        } catch (Exception e) {
            log.warn("Failed to fetch GitHub /user/emails: {}", e.getMessage());
            return null;
        }
    }

    private static String pickEmail(List<Map<String, Object>> emails,
                                     java.util.function.Predicate<Map<String, Object>> filter) {
        return emails.stream()
                .filter(filter)
                .map(m -> (String) m.get("email"))
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);
    }
}
