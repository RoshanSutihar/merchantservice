package com.roshansutihar.merchantportal.service;


import com.roshansutihar.merchantportal.response.MerchantResponse;
import com.roshansutihar.merchantportal.response.SummaryResponse;
import com.roshansutihar.merchantportal.response.TransactionResponse;
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

    @Value("${app.api.base-url}")
    private String baseUrl;

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
        return restTemplate.getForObject(url, TransactionResponse.class);
    }

    public TransactionResponse getTransactionsByDateRange(String merchantId, LocalDate from, LocalDate to, String status) {
        String url = baseUrl + "/api/v1/transactions/merchant/" + merchantId;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("from", from.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .queryParam("to", to.atTime(23, 59, 59).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (status != null && !status.isEmpty()) {
            builder.queryParam("status", status);
        }

        return restTemplate.getForObject(builder.toUriString(), TransactionResponse.class);
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
}
