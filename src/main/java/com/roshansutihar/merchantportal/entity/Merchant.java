package com.roshansutihar.merchantportal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "merchants")
@Data
@NoArgsConstructor
public class Merchant {

    @Id
    private String merchantId;
    private String siteId;
    private String storeName;
    private String callbackUrl;
    private String commissionType;
    private BigDecimal commissionValue;
    private BigDecimal minCommission;
    private BigDecimal maxCommission;
    private String bankAccountNumber;
    private String bankRoutingNumber;

}
