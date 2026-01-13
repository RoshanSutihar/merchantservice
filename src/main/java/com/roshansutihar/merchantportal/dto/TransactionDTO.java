package com.roshansutihar.merchantportal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TransactionDTO {
    private String transactionRef;
    private Double amount;
    private Double commissionAmount;
    private Double netAmount;
    private String status;
    private LocalDateTime createdAt;
}
