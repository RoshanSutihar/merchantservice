package com.roshansutihar.merchantportal.Response;

import com.roshansutihar.merchantportal.Request.Transaction;
import lombok.Data;

import java.util.List;

@Data
public class TransactionResponse {
    private String merchantId;
    private List<Transaction> transactions;
}
