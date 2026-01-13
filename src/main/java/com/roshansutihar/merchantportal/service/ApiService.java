package com.roshansutihar.merchantportal.service;


import com.roshansutihar.merchantportal.response.MerchantResponse;
import com.roshansutihar.merchantportal.response.SecretRotationResponse;
import com.roshansutihar.merchantportal.response.SummaryResponse;
import com.roshansutihar.merchantportal.response.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
@Service
public class ApiService {

    @Value("${PAYMENTS_CORE_BASE_URL}")
    private String baseUrl;

    private static final Logger log = LoggerFactory.getLogger(ApiService.class);
    private final RestTemplate restTemplate;

    // Timezone constants
    private static final ZoneId CHICAGO_ZONE = ZoneId.of("America/Chicago");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public ApiService() {
        this.restTemplate = new RestTemplate();
    }

    public List<String> getMerchantIds() {
        String url = baseUrl + "/api/v1/merchants/ids";
        ResponseEntity<List<String>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {}
        );
        return response.getBody();
    }

    public TransactionResponse getTodayTransactions(String merchantId) {
        String url = baseUrl + "/api/v1/transactions/merchant/" + merchantId + "/today";
        log.info("Calling external API: GET {}", url);
        try {
            TransactionResponse response = restTemplate.getForObject(url, TransactionResponse.class);
            int count = response != null && response.getTransactions() != null
                    ? response.getTransactions().size() : 0;
            log.info("API success - Today's transactions for {}: {} items", merchantId, count);
            return response;
        } catch (Exception e) {
            log.error("API call FAILED for getTodayTransactions (merchantId={}): {}", merchantId, e.getMessage(), e);
            throw e;
        }
    }

    public TransactionResponse getTransactionsByDateRange(String merchantId, LocalDate from, LocalDate to, String status) {
        String url = baseUrl + "/api/v1/transactions/merchant/" + merchantId;

        // Convert Chicago dates to UTC for API call
        ZonedDateTime fromChicago = from.atStartOfDay(CHICAGO_ZONE);
        ZonedDateTime toChicago = to.atTime(23, 59, 59).atZone(CHICAGO_ZONE);

        ZonedDateTime fromUTC = fromChicago.withZoneSameInstant(UTC_ZONE);
        ZonedDateTime toUTC = toChicago.withZoneSameInstant(UTC_ZONE);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("from", fromUTC.format(API_DATE_FORMATTER))
                .queryParam("to", toUTC.format(API_DATE_FORMATTER));

        if (status != null && !status.isEmpty()) {
            builder.queryParam("status", status);
        }

        String finalUrl = builder.toUriString();
        log.info("Calling external API: GET {}", finalUrl);
        log.info("Date range - Chicago: {} to {}, UTC: {} to {}",
                fromChicago.toLocalDate(), toChicago.toLocalDate(),
                fromUTC.toLocalDate(), toUTC.toLocalDate());

        try {
            TransactionResponse response = restTemplate.getForObject(finalUrl, TransactionResponse.class);
            int count = response != null && response.getTransactions() != null
                    ? response.getTransactions().size() : 0;
            log.info("API success - Range transactions for {}: {} items", merchantId, count);
            return response;
        } catch (Exception e) {
            log.error("API call FAILED for getTransactionsByDateRange (merchantId={}): {}", merchantId, e.getMessage(), e);
            throw e;
        }
    }

    public SummaryResponse getSummary(String merchantId, LocalDate from, LocalDate to) {
        String url = baseUrl + "/api/v1/transactions/merchant/" + merchantId + "/summary";

        // Convert Chicago dates to UTC for API call
        ZonedDateTime fromChicago = from.atStartOfDay(CHICAGO_ZONE);
        ZonedDateTime toChicago = to.atTime(23, 59, 59).atZone(CHICAGO_ZONE);

        ZonedDateTime fromUTC = fromChicago.withZoneSameInstant(UTC_ZONE);
        ZonedDateTime toUTC = toChicago.withZoneSameInstant(UTC_ZONE);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("from", fromUTC.format(API_DATE_FORMATTER))
                .queryParam("to", toUTC.format(API_DATE_FORMATTER));

        log.info("Summary API - Chicago dates: {} to {}, UTC dates: {} to {}",
                fromChicago.toLocalDate(), toChicago.toLocalDate(),
                fromUTC.toLocalDate(), toUTC.toLocalDate());

        return restTemplate.getForObject(builder.toUriString(), SummaryResponse.class);
    }

    public MerchantResponse registerMerchant(Map<String, Object> request) {
        String url = baseUrl + "/api/v1/merchants/register";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<MerchantResponse> response = restTemplate.postForEntity(url, entity, MerchantResponse.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to register merchant: " + response.getStatusCode());
        }
        return response.getBody();
    }

    public String rotateSecretKey(String merchantId) {
        String url = baseUrl + "/api/v1/merchants/" + merchantId + "/rotate-secret";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<SecretRotationResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                SecretRotationResponse.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to rotate secret key: " + response.getStatusCode());
        }

        return response.getBody().getNewSecretKey();
    }

    // Helper method to get "today" in Chicago time
    public LocalDate getTodayChicago() {
        return LocalDate.now(CHICAGO_ZONE);
    }
}