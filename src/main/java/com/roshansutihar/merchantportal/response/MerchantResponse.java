package com.roshansutihar.merchantportal.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantResponse {
    private boolean success;
    private String merchantId;
    private String storeName;
    private String secretKey;
    private String callbackUrl;
    private String status;
    private String commissionType;
    private BigDecimal commissionValue;
    private String message;
}
