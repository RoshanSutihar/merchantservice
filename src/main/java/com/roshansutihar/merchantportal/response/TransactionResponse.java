package com.roshansutihar.merchantportal.response;

import com.roshansutihar.merchantportal.request.Transaction;
import lombok.Data;

import java.util.List;

@Data
public class TransactionResponse {
    private String merchantId;
    private List<Transaction> transactions;
}
