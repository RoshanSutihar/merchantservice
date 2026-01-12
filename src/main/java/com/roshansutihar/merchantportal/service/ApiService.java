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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ApiService {

    @Value("${PAYMENTS_CORE_BASE_URL}")
    private String baseUrl;

    private static final Logger log = LoggerFactory.getLogger(ApiService.class);
    private final RestTemplate restTemplate;

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
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("from", from.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .queryParam("to", to.atTime(23, 59, 59).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (status != null && !status.isEmpty()) {
            builder.queryParam("status", status);
        }

        String finalUrl = builder.toUriString();
        log.info("Calling external API: GET {}", finalUrl);

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

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("from", from.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .queryParam("to", to.atTime(23, 59, 59).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

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
}
