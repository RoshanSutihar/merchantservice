package com.roshansutihar.merchantportal.response;

import lombok.Data;

@Data
public class SummaryResponse {
    private String merchantId;
    private Double totalAmount;
    private Double totalCommission;
    private Double totalNetAmount;
    private Long totalTransactions;
    private String periodFrom;
    private String periodTo;
}
