package com.roshansutihar.merchantportal.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
@Service
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    @Value("${keycloak.base-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-client.id}")
    private String adminClientId;

    @Value("${keycloak.admin-client.secret}")
    private String adminClientSecret;

    private final RestTemplate restTemplate;

    private String adminToken;
    private Instant tokenExpiry = Instant.now();

    public KeycloakAdminService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private synchronized void ensureValidToken() {
        if (adminToken == null || Instant.now().isAfter(tokenExpiry.minusSeconds(30))) {
            adminToken = fetchAdminAccessToken();
            tokenExpiry = Instant.now().plusSeconds(300);
            log.debug("Fetched new Keycloak admin token");
        }
    }

    private String fetchAdminAccessToken() {
        String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", adminClientId);
        body.add("client_secret", adminClientSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            Map<String, Object> respBody = response.getBody();
            if (respBody != null && respBody.containsKey("access_token")) {
                return (String) respBody.get("access_token");
            }
        } catch (Exception e) {
            log.error("Failed to get Keycloak admin token", e);
            throw new RuntimeException("Failed to get Keycloak admin token", e);
        }
        throw new RuntimeException("Invalid response from Keycloak token endpoint");
    }

    public void createMerchantUser(String username, String password, String firstName, String lastName) {
        ensureValidToken();

        String url = keycloakUrl + "/admin/realms/" + realm + "/users";

        Map<String, Object> userRepresentation = new HashMap<>();
        userRepresentation.put("username", username);
        userRepresentation.put("enabled", true);
        userRepresentation.put("firstName", firstName);
        userRepresentation.put("lastName", lastName);

        List<Map<String, Object>> credentials = new ArrayList<>();
        Map<String, Object> cred = new HashMap<>();
        cred.put("type", "password");
        cred.put("value", password);
        cred.put("temporary", true);
        credentials.add(cred);
        userRepresentation.put("credentials", credentials);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userRepresentation, headers);

        ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, request, Void.class);

        if (response.getStatusCode() != HttpStatus.CREATED) {
            throw new RuntimeException("Failed to create merchant user in Keycloak: " + response.getStatusCode());
        }

        String location = Objects.requireNonNull(response.getHeaders().getLocation()).toString();
        String userId = location.substring(location.lastIndexOf("/") + 1);

        assignRoleToUser(userId, "merchant");
        log.info("Created Keycloak user: username={}, firstName={}, lastName={}, role=merchant",
                username, firstName, lastName);
    }

    private void assignRoleToUser(String userId, String roleName) {
        ensureValidToken();

        String roleUrl = keycloakUrl + "/admin/realms/" + realm + "/roles/" + roleName;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> roleRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> roleResponse = restTemplate.exchange(roleUrl, HttpMethod.GET, roleRequest, Map.class);

        Map<String, Object> roleRepresentation = roleResponse.getBody();
        if (roleRepresentation == null) {
            throw new RuntimeException("Role not found in Keycloak: " + roleName);
        }

        String assignUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";

        List<Map<String, Object>> roles = List.of(roleRepresentation);

        HttpEntity<List<Map<String, Object>>> assignRequest = new HttpEntity<>(roles, headers);
        ResponseEntity<Void> assignResponse = restTemplate.exchange(assignUrl, HttpMethod.POST, assignRequest, Void.class);

        if (!assignResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to assign role '" + roleName + "' to user");
        }
    }
}