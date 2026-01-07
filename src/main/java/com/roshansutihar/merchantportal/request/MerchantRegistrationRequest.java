package com.roshansutihar.merchantportal.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRegistrationRequest {
    @NotBlank private String storeName;
    private String callbackUrl;
    private String commissionType;
    private BigDecimal commissionValue;
    private BigDecimal minCommission;
    private BigDecimal maxCommission;
    private String bankAccountNumber;
    private String bankRoutingNumber;

}