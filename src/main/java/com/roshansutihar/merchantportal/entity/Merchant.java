package com.roshansutihar.merchantportal.entity;

import jakarta.persistence.Column;
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

    @Column(name = "site_id", unique = true, nullable = false, length = 5)
    private String siteId;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "callback_url", nullable = false)
    private String callbackUrl;

    @Column(name = "commission_type", nullable = false)
    private String commissionType;

    @Column(name = "commission_value", nullable = false)
    private BigDecimal commissionValue;

    @Column(name = "min_commission")
    private BigDecimal minCommission;

    @Column(name = "max_commission")
    private BigDecimal maxCommission;

    @Column(name = "bank_account_number", nullable = false)
    private String bankAccountNumber;

    @Column(name = "bank_routing_number", nullable = false)
    private String bankRoutingNumber;

    @Column(name = "secret_key")
    private String secretKey;

}
