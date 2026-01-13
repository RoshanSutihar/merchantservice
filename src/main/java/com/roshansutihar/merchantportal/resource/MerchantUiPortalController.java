package com.roshansutihar.merchantportal.resource;


import com.roshansutihar.merchantportal.dto.DashboardSummary;
import com.roshansutihar.merchantportal.dto.TransactionDTO;
import com.roshansutihar.merchantportal.entity.Merchant;
import com.roshansutihar.merchantportal.repository.MerchantRepository;
import com.roshansutihar.merchantportal.request.Transaction;
import com.roshansutihar.merchantportal.response.MerchantResponse;
import com.roshansutihar.merchantportal.response.SummaryResponse;
import com.roshansutihar.merchantportal.response.TransactionResponse;
import com.roshansutihar.merchantportal.service.ApiService;
import com.roshansutihar.merchantportal.service.KeycloakAdminService;
import com.roshansutihar.merchantportal.service.SiteIdGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;



@Controller
public class MerchantUiPortalController {

    private final ApiService apiService;
    private final MerchantRepository merchantRepository;
    private final SiteIdGeneratorService siteIdGeneratorService;
    private final KeycloakAdminService keycloakAdminService;

    private static final Logger log = LoggerFactory.getLogger(MerchantUiPortalController.class);

    // Chicago timezone constant
    private static final ZoneId CHICAGO_ZONE = ZoneId.of("America/Chicago");
    // UTC timezone constant
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    public MerchantUiPortalController(
            ApiService apiService,
            MerchantRepository merchantRepository,
            SiteIdGeneratorService siteIdGeneratorService,
            KeycloakAdminService keycloakAdminService) {
        this.apiService = apiService;
        this.merchantRepository = merchantRepository;
        this.siteIdGeneratorService = siteIdGeneratorService;
        this.keycloakAdminService = keycloakAdminService;
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/post-login")
    public String postLogin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(granted -> {
                    String authority = granted.getAuthority();
                    return authority.equals("ROLE_ADMIN") ||
                            authority.toUpperCase().contains("ADMIN");
                });

        if (isAdmin) {
            return "redirect:/register-merchant";
        }

        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(granted -> {
                    String authority = granted.getAuthority();
                    return authority.equals("ROLE_ADMIN") || authority.toUpperCase().contains("ADMIN");
                });

        if (isAdmin) {
            return "redirect:/register-merchant";
        }

        String siteId = authentication.getName();

        try {
            Optional<Merchant> merchantOpt = merchantRepository.findBySiteId(siteId);
            if (merchantOpt.isEmpty()) {
                model.addAttribute("error", "Merchant profile not found. Please contact support.");
                return "dashboard";
            }

            Merchant merchant = merchantOpt.get();
            String merchantId = merchant.getMerchantId();

            model.addAttribute("merchant", merchant);
            model.addAttribute("selectedMerchant", merchantId);

            // FIX: Get transactions for Chicago "today" (midnight to midnight Chicago time)
            LocalDate todayChicago = LocalDate.now(CHICAGO_ZONE);

            // Convert Chicago date to UTC for API call
            ZonedDateTime startChicago = todayChicago.atStartOfDay(CHICAGO_ZONE);
            ZonedDateTime endChicago = todayChicago.plusDays(1).atStartOfDay(CHICAGO_ZONE).minusSeconds(1);

            LocalDate startDateUTC = startChicago.withZoneSameInstant(UTC_ZONE).toLocalDate();
            LocalDate endDateUTC = endChicago.withZoneSameInstant(UTC_ZONE).toLocalDate();

            TransactionResponse todayTransactions = apiService.getTransactionsByDateRange(
                    merchantId, startDateUTC, endDateUTC, null
            );

            double totalAmount = 0.0;
            double totalCommission = 0.0;
            double totalNet = 0.0;

            if (todayTransactions != null && todayTransactions.getTransactions() != null) {
                for (Transaction tx : todayTransactions.getTransactions()) {
                    totalAmount += tx.getAmount() != null ? tx.getAmount() : 0.0;
                    totalCommission += tx.getCommissionAmount() != null ? tx.getCommissionAmount() : 0.0;
                    totalNet += tx.getNetAmount() != null ? tx.getNetAmount() : 0.0;
                }
            }

            model.addAttribute("totalAmount", totalAmount);
            model.addAttribute("totalCommission", totalCommission);
            model.addAttribute("totalNet", totalNet);
            model.addAttribute("transactions", todayTransactions);

            // Summary statistics
            LocalDate monthStart = todayChicago.withDayOfMonth(1);
            SummaryResponse todaySummary = apiService.getSummary(merchantId, todayChicago, todayChicago);
            SummaryResponse monthSummary = apiService.getSummary(merchantId, monthStart, todayChicago);

            // Build dashboard summary
            DashboardSummary summary = new DashboardSummary();

            // Today's Transactions Count
            long todayCount = todaySummary != null && todaySummary.getTotalTransactions() != null
                    ? todaySummary.getTotalTransactions()
                    : 0L;
            summary.setTodaysTransactionCount(todayCount);

            // Today's Sales
            BigDecimal todaySales = todaySummary != null && todaySummary.getTotalAmount() != null
                    ? BigDecimal.valueOf(todaySummary.getTotalAmount())
                    : BigDecimal.ZERO;
            summary.setTodaysSales(todaySales);

            // Acknowledged count
            long acknowledgedCount = 0L;
            if (todayTransactions != null && todayTransactions.getTransactions() != null) {
                acknowledgedCount = todayTransactions.getTransactions().stream()
                        .filter(tx -> tx.getStatus() != null &&
                                tx.getStatus().equalsIgnoreCase("ACKNOWLEDGED"))
                        .count();
            }
            summary.setAcknowledgedCount(acknowledgedCount);

            // Monthly Total
            BigDecimal monthlyTotal = monthSummary != null && monthSummary.getTotalAmount() != null
                    ? BigDecimal.valueOf(monthSummary.getTotalAmount())
                    : BigDecimal.ZERO;
            summary.setMonthlyTotal(monthlyTotal);

            model.addAttribute("summary", summary);

            // Form defaults
            model.addAttribute("view", "today");
            model.addAttribute("fromDate", todayChicago);
            model.addAttribute("toDate", todayChicago);
            model.addAttribute("selectedStatus", "");

        } catch (Exception e) {
            model.addAttribute("error", "Unable to load dashboard: " + e.getMessage());
            model.addAttribute("totalAmount", 0.0);
            model.addAttribute("totalCommission", 0.0);
            model.addAttribute("totalNet", 0.0);
        }

        return "dashboard";
    }

    @GetMapping("/register-merchant")
    public String showRegisterMerchantForm(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(granted -> {
                        String authority = granted.getAuthority();
                        return authority.equals("ROLE_ADMIN") ||
                                authority.toUpperCase().contains("ADMIN");
                    });

            if (!isAdmin) {
                return "redirect:/dashboard";
            }
        }

        return "register-merchant";
    }

    @GetMapping("/admin")
    public String adminRedirect() {
        return "redirect:/admin/merchants";
    }

    @PostMapping("/register-merchant")
    public String registerMerchant(
            @RequestParam String storeName,
            @RequestParam String callbackUrl,
            @RequestParam String commissionType,
            @RequestParam String commissionValue,
            @RequestParam(required = false) String minCommission,
            @RequestParam(required = false) String maxCommission,
            @RequestParam String bankAccountNumber,
            @RequestParam String bankRoutingNumber,
            Model model) {

        if (storeName == null || storeName.trim().isEmpty()) {
            model.addAttribute("error", "Store name is required");
            return "register-merchant";
        }
        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            model.addAttribute("error", "Callback URL is required");
            return "register-merchant";
        }
        if (commissionType == null || commissionType.trim().isEmpty()) {
            model.addAttribute("error", "Commission type is required");
            return "register-merchant";
        }
        if (commissionValue == null || commissionValue.trim().isEmpty()) {
            model.addAttribute("error", "Commission value is required");
            return "register-merchant";
        }
        if (bankAccountNumber == null || bankAccountNumber.trim().isEmpty()) {
            model.addAttribute("error", "Bank account number is required");
            return "register-merchant";
        }
        if (bankRoutingNumber == null || bankRoutingNumber.trim().isEmpty()) {
            model.addAttribute("error", "Bank routing number is required");
            return "register-merchant";
        }

        String cleanedAccountNumber = bankAccountNumber.trim().replaceAll("[^0-9]", "");
        if (cleanedAccountNumber.length() < 5 || cleanedAccountNumber.length() > 17) {
            model.addAttribute("error", "Bank account number must be between 5 and 17 digits");
            return "register-merchant";
        }

        String cleanedRoutingNumber = bankRoutingNumber.trim().replaceAll("[^0-9]", "");
        if (cleanedRoutingNumber.length() != 9) {
            model.addAttribute("error", "Routing number must be exactly 9 digits");
            return "register-merchant";
        }

        BigDecimal commissionValueBd;
        try {
            commissionValueBd = new BigDecimal(commissionValue);
            if (commissionValueBd.compareTo(BigDecimal.ZERO) <= 0) {
                model.addAttribute("error", "Commission value must be greater than 0");
                return "register-merchant";
            }
        } catch (NumberFormatException e) {
            model.addAttribute("error", "Invalid commission value");
            return "register-merchant";
        }

        BigDecimal minCommissionBd = BigDecimal.ZERO;
        if (minCommission != null && !minCommission.trim().isEmpty()) {
            try {
                minCommissionBd = new BigDecimal(minCommission);
                if (minCommissionBd.compareTo(BigDecimal.ZERO) < 0) {
                    model.addAttribute("error", "Min commission must be non-negative");
                    return "register-merchant";
                }
            } catch (NumberFormatException e) {
                model.addAttribute("error", "Invalid min commission value");
                return "register-merchant";
            }
        }

        BigDecimal maxCommissionBd = BigDecimal.ZERO;
        if (maxCommission != null && !maxCommission.trim().isEmpty()) {
            try {
                maxCommissionBd = new BigDecimal(maxCommission);
                if (maxCommissionBd.compareTo(BigDecimal.ZERO) < 0) {
                    model.addAttribute("error", "Max commission must be non-negative");
                    return "register-merchant";
                }
            } catch (NumberFormatException e) {
                model.addAttribute("error", "Invalid max commission value");
                return "register-merchant";
            }
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("storeName", storeName.trim());
            requestBody.put("callbackUrl", callbackUrl.trim());
            requestBody.put("commissionType", commissionType.trim());
            requestBody.put("commissionValue", commissionValueBd);
            requestBody.put("minCommission", minCommissionBd);
            requestBody.put("maxCommission", maxCommissionBd);
            requestBody.put("bankAccountNumber", cleanedAccountNumber);
            requestBody.put("bankRoutingNumber", cleanedRoutingNumber);

            MerchantResponse response = apiService.registerMerchant(requestBody);

            String siteId = siteIdGeneratorService.generateUniqueSiteId();

            String fullSecretKey = response.getSecretKey();
            String maskedSecretKey = fullSecretKey != null && fullSecretKey.length() > 8
                    ? "••••••••" + fullSecretKey.substring(fullSecretKey.length() - 8)
                    : "••••••••";

            Merchant merchant = new Merchant();
            merchant.setMerchantId(response.getMerchantId());
            merchant.setSiteId(siteId);
            merchant.setStoreName(storeName.trim());
            merchant.setCallbackUrl(callbackUrl.trim());
            merchant.setCommissionType(commissionType.trim());
            merchant.setCommissionValue(commissionValueBd);
            merchant.setMinCommission(minCommissionBd);
            merchant.setMaxCommission(maxCommissionBd);
            merchant.setBankAccountNumber(cleanedAccountNumber);
            merchant.setBankRoutingNumber(cleanedRoutingNumber);
            merchant.setSecretKey(maskedSecretKey);

            merchantRepository.save(merchant);

            String tempPassword = "admin123";

            keycloakAdminService.createMerchantUser(
                    siteId,
                    tempPassword,
                    "Merchant",
                    storeName.trim()
            );

            model.addAttribute("fullSecretKey", fullSecretKey);
            model.addAttribute("merchantResponse", response);
            model.addAttribute("siteId", siteId);
            model.addAttribute("tempPassword", tempPassword);
            model.addAttribute("success", "Merchant registered successfully!<br><br>" +
                    "<strong>Login Username (Site ID):</strong> " + siteId + "<br>" +
                    "<strong>Temporary Password:</strong> " + tempPassword + "<br><br>" +
                    "The merchant must change this password on first login.");

            return "register-merchant";

        } catch (Exception e) {
            model.addAttribute("error", "Failed to register merchant: " + e.getMessage());
            return "register-merchant";
        }
    }

    @PostMapping("/transactions/today")
    public String getTodayTransactions(@RequestParam String merchantId, Model model, Authentication authentication) {
        try {
            LocalDate todayChicago = LocalDate.now(CHICAGO_ZONE);

            // Convert Chicago date to UTC for API call
            ZonedDateTime startChicago = todayChicago.atStartOfDay(CHICAGO_ZONE);
            ZonedDateTime endChicago = todayChicago.plusDays(1).atStartOfDay(CHICAGO_ZONE).minusSeconds(1);

            LocalDate startDateUTC = startChicago.withZoneSameInstant(UTC_ZONE).toLocalDate();
            LocalDate endDateUTC = endChicago.withZoneSameInstant(UTC_ZONE).toLocalDate();

            TransactionResponse response = apiService.getTransactionsByDateRange(
                    merchantId, startDateUTC, endDateUTC, null
            );

            // Get summary data
            SummaryResponse todaySummary = apiService.getSummary(merchantId, todayChicago, todayChicago);
            LocalDate monthStart = todayChicago.withDayOfMonth(1);
            SummaryResponse monthSummary = apiService.getSummary(merchantId, monthStart, todayChicago);

            // Calculate totals
            double totalAmount = 0.0;
            double totalCommission = 0.0;
            double totalNet = 0.0;

            if (response != null && response.getTransactions() != null) {
                for (Transaction tx : response.getTransactions()) {
                    totalAmount += tx.getAmount() != null ? tx.getAmount() : 0.0;
                    totalCommission += tx.getCommissionAmount() != null ? tx.getCommissionAmount() : 0.0;
                    totalNet += tx.getNetAmount() != null ? tx.getNetAmount() : 0.0;
                }
            }

            // Build dashboard summary
            DashboardSummary summary = new DashboardSummary();

            long todayCount = todaySummary != null && todaySummary.getTotalTransactions() != null
                    ? todaySummary.getTotalTransactions()
                    : 0L;
            summary.setTodaysTransactionCount(todayCount);

            BigDecimal todaySales = todaySummary != null && todaySummary.getTotalAmount() != null
                    ? BigDecimal.valueOf(todaySummary.getTotalAmount())
                    : BigDecimal.ZERO;
            summary.setTodaysSales(todaySales);

            long acknowledgedCount = 0L;
            if (response != null && response.getTransactions() != null) {
                acknowledgedCount = response.getTransactions().stream()
                        .filter(tx -> tx.getStatus() != null &&
                                tx.getStatus().equalsIgnoreCase("ACKNOWLEDGED"))
                        .count();
            }
            summary.setAcknowledgedCount(acknowledgedCount);

            BigDecimal monthlyTotal = monthSummary != null && monthSummary.getTotalAmount() != null
                    ? BigDecimal.valueOf(monthSummary.getTotalAmount())
                    : BigDecimal.ZERO;
            summary.setMonthlyTotal(monthlyTotal);

            // Get merchant info
            String siteId = authentication.getName();
            Optional<Merchant> merchantOpt = merchantRepository.findBySiteId(siteId);
            if (merchantOpt.isPresent()) {
                Merchant merchant = merchantOpt.get();
                model.addAttribute("merchant", merchant);
            }

            // Add all attributes to model
            model.addAttribute("transactions", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("summary", summary);
            model.addAttribute("totalAmount", totalAmount);
            model.addAttribute("totalCommission", totalCommission);
            model.addAttribute("totalNet", totalNet);
            model.addAttribute("view", "today");
            model.addAttribute("fromDate", todayChicago);
            model.addAttribute("toDate", todayChicago);
            model.addAttribute("selectedStatus", "");

        } catch (Exception e) {
            model.addAttribute("error", "Error fetching today's transactions: " + e.getMessage());
        }
        return "dashboard";
    }

    @PostMapping("/transactions/range")
    public String getTransactionsByRange(
            @RequestParam String merchantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            Model model) {

        try {
            TransactionResponse response = apiService.getTransactionsByDateRange(merchantId, fromDate, toDate, status);

            model.addAttribute("transactions", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("fromDate", fromDate);
            model.addAttribute("toDate", toDate);
            model.addAttribute("selectedStatus", status != null ? status : "");
            model.addAttribute("view", "range");
        } catch (Exception e) {
            model.addAttribute("error", "Error fetching transactions: " + e.getMessage());
        }
        return "dashboard";
    }

    @PostMapping("/transactions/summary")
    public String getSummary(
            @RequestParam String merchantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {

        try {
            SummaryResponse response = apiService.getSummary(merchantId, fromDate, toDate);

            model.addAttribute("summary", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("fromDate", fromDate);
            model.addAttribute("toDate", toDate);
            model.addAttribute("view", "summary");
        } catch (Exception e) {
            model.addAttribute("error", "Error fetching summary: " + e.getMessage());
        }
        return "dashboard";
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}