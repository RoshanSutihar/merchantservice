package com.roshansutihar.merchantportal.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DashboardSummary {
    private long todaysTransactionCount = 0;
    private BigDecimal todaysSales = BigDecimal.ZERO;
    private long acknowledgedCount = 0;
    private BigDecimal monthlyTotal = BigDecimal.ZERO;

}
