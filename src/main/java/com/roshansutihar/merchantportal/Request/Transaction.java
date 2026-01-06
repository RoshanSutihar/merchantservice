package com.roshansutihar.merchantportal.Request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Transaction {
    private String sessionId;
    private String transactionRef;
    private Double amount;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Double commissionAmount;
    private Double netAmount;
    private LocalDateTime settlementDate;
}
