package com.roshansutihar.merchantportal.resource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LogoutController {

    private final String keycloakBase;
    private final String realm;

    public LogoutController(
            @Value("${keycloak.base-url}") String keycloakBase,
            @Value("${keycloak.realm}") String realm) {
        this.keycloakBase = keycloakBase;
        this.realm = realm;
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request,
                         HttpServletResponse response,
                         Authentication authentication) {

        // 1. Invalidate Spring Security session first
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        // 2. Get id_token from session (we'll store it during login/dashboard access)
        String idToken = (String) request.getSession().getAttribute("id_token");

        // 3. Build post-logout redirect URI (root of the application)
        String contextPath = request.getContextPath();
        String baseUrl = request.getScheme() + "://" + request.getServerName();
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            baseUrl += ":" + request.getServerPort();
        }
        String postLogoutRedirect = baseUrl + contextPath + "/";

        // 4. Build Keycloak logout URL
        StringBuilder logoutUrl = new StringBuilder()
                .append(keycloakBase)
                .append("/realms/")
                .append(realm)
                .append("/protocol/openid-connect/logout")
                .append("?post_logout_redirect_uri=")
                .append(postLogoutRedirect);

        if (idToken != null) {
            logoutUrl.append("&id_token_hint=").append(idToken);
        }

        // 5. Redirect to Keycloak to end SSO session
        return "redirect:" + logoutUrl.toString();
    }
}
