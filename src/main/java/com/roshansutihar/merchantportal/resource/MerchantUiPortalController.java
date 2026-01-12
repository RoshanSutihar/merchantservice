package com.roshansutihar.merchantportal.resource;


import com.roshansutihar.merchantportal.dto.DashboardSummary;
import com.roshansutihar.merchantportal.entity.Merchant;
import com.roshansutihar.merchantportal.repository.MerchantRepository;
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
        log.debug("Home page requested");
        return "home";
    }

    @GetMapping("/post-login")
    public String postLogin(Authentication authentication) {
        log.info("Post-login redirect triggered");

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthenticated user in post-login → redirect to /");
            return "redirect:/";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(granted -> {
                    String authority = granted.getAuthority();
                    return authority.equals("ROLE_ADMIN") ||
                            authority.toUpperCase().contains("ADMIN");
                });

        log.info("Post-login - Is ADMIN? {}", isAdmin);

        if (isAdmin) {
            log.info("Admin user detected → redirecting to register-merchant");
            return "redirect:/register-merchant";
        }

        log.info("Regular merchant user → redirecting to dashboard");
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        log.info("Dashboard endpoint called");

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthenticated access attempt to /dashboard → redirect to /");
            return "redirect:/";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(granted -> {
                    String authority = granted.getAuthority();
                    return authority.equals("ROLE_ADMIN") || authority.toUpperCase().contains("ADMIN");
                });

        log.info("User role check - Is ADMIN? {}", isAdmin);

        if (isAdmin) {
            log.info("Admin user trying to access merchant dashboard → redirecting to /register-merchant");
            System.out.println("Admin user trying to access dashboard, redirecting to /register-merchant");
            return "redirect:/register-merchant";
        }

        String siteId = authentication.getName();
        log.info("Loading dashboard for merchant siteId: {}", siteId);

        try {
            Optional<Merchant> merchantOpt = merchantRepository.findBySiteId(siteId);
            if (merchantOpt.isEmpty()) {
                log.warn("No merchant found in database for siteId: {}", siteId);
                model.addAttribute("error", "Merchant profile not found. Please contact support.");
                return "dashboard";
            }

            Merchant merchant = merchantOpt.get();
            String merchantId = merchant.getMerchantId();
            log.info("Merchant loaded successfully - ID: {}, Store: {}", merchantId, merchant.getStoreName());

            model.addAttribute("merchant", merchant);
            model.addAttribute("selectedMerchant", merchantId);

            // Today's transactions list (for the table)
            TransactionResponse todayTransactions = apiService.getTodayTransactions(merchantId);
            model.addAttribute("transactions", todayTransactions);

            // Summary data
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);

            // Today's summary
            SummaryResponse todaySummary = apiService.getSummary(merchantId, today, today);

            // This month's summary
            SummaryResponse monthSummary = apiService.getSummary(merchantId, monthStart, today);

            // Build dashboard summary
            DashboardSummary summary = new DashboardSummary();

            // Today's Transactions Count
            summary.setTodaysTransactionCount(
                    todaySummary.getTotalTransactions() != null
                            ? todaySummary.getTotalTransactions()
                            : 0L
            );

            // Today's Sales (gross amount)
            summary.setTodaysSales(
                    todaySummary.getTotalAmount() != null
                            ? BigDecimal.valueOf(todaySummary.getTotalAmount())
                            : BigDecimal.ZERO
            );

            // Acknowledged count → fallback to counting from today's transactions list
            long acknowledgedCount = 0L;
            if (todayTransactions != null && todayTransactions.getTransactions() != null) {
                acknowledgedCount = todayTransactions.getTransactions().stream()
                        .filter(tx -> "ACKNOWLEDGED".equalsIgnoreCase(tx.getStatus()))
                        .count();
            }
            summary.setAcknowledgedCount(acknowledgedCount);

            // Monthly Total (gross amount this month)
            summary.setMonthlyTotal(
                    monthSummary.getTotalAmount() != null
                            ? BigDecimal.valueOf(monthSummary.getTotalAmount())
                            : BigDecimal.ZERO
            );

            model.addAttribute("summary", summary);

            // Form defaults
            model.addAttribute("view", "today");
            model.addAttribute("fromDate", today);
            model.addAttribute("toDate", today);
            model.addAttribute("selectedStatus", "");

            log.info("Dashboard data prepared - rendering template");
        } catch (Exception e) {
            log.error("Critical error loading dashboard for siteId {}: {}", siteId, e.getMessage(), e);
            model.addAttribute("error", "Unable to load dashboard: " + e.getMessage());
        }

        return "dashboard";
    }

    @GetMapping("/register-merchant")
    public String showRegisterMerchantForm(Model model, Authentication authentication) {
        log.info("Register merchant form requested");

        if (authentication != null && authentication.isAuthenticated()) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(granted -> {
                        String authority = granted.getAuthority();
                        return authority.equals("ROLE_ADMIN") ||
                                authority.toUpperCase().contains("ADMIN");
                    });

            if (!isAdmin) {
                log.warn("Non-admin user tried to access register-merchant → redirect to dashboard");
                return "redirect:/dashboard";
            }
        }

        log.info("Showing register-merchant form to admin");
        return "register-merchant";
    }

    @GetMapping("/admin")
    public String adminRedirect() {
        log.info("Admin root path accessed → redirecting to /admin/merchants");
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

        log.info("Merchant registration request received - Store: {}", storeName);

        if (storeName == null || storeName.trim().isEmpty()) {
            log.warn("Registration failed: Store name missing");
            model.addAttribute("error", "Store name is required");
            return "register-merchant";
        }
        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            log.warn("Registration failed: Callback URL missing");
            model.addAttribute("error", "Callback URL is required");
            return "register-merchant";
        }
        if (commissionType == null || commissionType.trim().isEmpty()) {
            log.warn("Registration failed: Commission type missing");
            model.addAttribute("error", "Commission type is required");
            return "register-merchant";
        }
        if (commissionValue == null || commissionValue.trim().isEmpty()) {
            log.warn("Registration failed: Commission value missing");
            model.addAttribute("error", "Commission value is required");
            return "register-merchant";
        }
        if (bankAccountNumber == null || bankAccountNumber.trim().isEmpty()) {
            log.warn("Registration failed: Bank account number missing");
            model.addAttribute("error", "Bank account number is required");
            return "register-merchant";
        }
        if (bankRoutingNumber == null || bankRoutingNumber.trim().isEmpty()) {
            log.warn("Registration failed: Bank routing number missing");
            model.addAttribute("error", "Bank routing number is required");
            return "register-merchant";
        }

        String cleanedAccountNumber = bankAccountNumber.trim().replaceAll("[^0-9]", "");
        if (cleanedAccountNumber.length() < 5 || cleanedAccountNumber.length() > 17) {
            log.warn("Registration failed: Invalid bank account length: {}", cleanedAccountNumber.length());
            model.addAttribute("error", "Bank account number must be between 5 and 17 digits");
            return "register-merchant";
        }

        String cleanedRoutingNumber = bankRoutingNumber.trim().replaceAll("[^0-9]", "");
        if (cleanedRoutingNumber.length() != 9) {
            log.warn("Registration failed: Invalid routing number length: {}", cleanedRoutingNumber.length());
            model.addAttribute("error", "Routing number must be exactly 9 digits");
            return "register-merchant";
        }

        BigDecimal commissionValueBd;
        try {
            commissionValueBd = new BigDecimal(commissionValue);
            if (commissionValueBd.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Registration failed: Invalid commission value: {}", commissionValue);
                model.addAttribute("error", "Commission value must be greater than 0");
                return "register-merchant";
            }
        } catch (NumberFormatException e) {
            log.warn("Registration failed: Invalid commission value format: {}", commissionValue);
            model.addAttribute("error", "Invalid commission value");
            return "register-merchant";
        }

        BigDecimal minCommissionBd = BigDecimal.ZERO;
        if (minCommission != null && !minCommission.trim().isEmpty()) {
            try {
                minCommissionBd = new BigDecimal(minCommission);
                if (minCommissionBd.compareTo(BigDecimal.ZERO) < 0) {
                    log.warn("Registration failed: Negative min commission: {}", minCommission);
                    model.addAttribute("error", "Min commission must be non-negative");
                    return "register-merchant";
                }
            } catch (NumberFormatException e) {
                log.warn("Registration failed: Invalid min commission format: {}", minCommission);
                model.addAttribute("error", "Invalid min commission value");
                return "register-merchant";
            }
        }

        BigDecimal maxCommissionBd = BigDecimal.ZERO;
        if (maxCommission != null && !maxCommission.trim().isEmpty()) {
            try {
                maxCommissionBd = new BigDecimal(maxCommission);
                if (maxCommissionBd.compareTo(BigDecimal.ZERO) < 0) {
                    log.warn("Registration failed: Negative max commission: {}", maxCommission);
                    model.addAttribute("error", "Max commission must be non-negative");
                    return "register-merchant";
                }
            } catch (NumberFormatException e) {
                log.warn("Registration failed: Invalid max commission format: {}", maxCommission);
                model.addAttribute("error", "Invalid max commission value");
                return "register-merchant";
            }
        }

        try {
            log.info("Preparing external API request for merchant registration - Store: {}", storeName);
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
            log.info("Merchant registered via API - Merchant ID: {}", response.getMerchantId());

            String siteId = siteIdGeneratorService.generateUniqueSiteId();
            log.info("Generated unique siteId: {}", siteId);

            String fullSecretKey = response.getSecretKey();
            String maskedSecretKey = fullSecretKey != null && fullSecretKey.length() > 8
                    ? "••••••••" + fullSecretKey.substring(fullSecretKey.length() - 8)
                    : "••••••••";
            log.debug("Secret key masked for storage");

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

            log.info("Saving merchant to local database - siteId: {}", siteId);
            merchantRepository.save(merchant);

            String tempPassword = "admin123";
            log.info("Generated temp password for new user (siteId: {})", siteId);

            log.info("Creating Keycloak user for siteId: {}, store: {}", siteId, storeName.trim());
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

            log.info("Merchant registration completed successfully - siteId: {}", siteId);
            return "register-merchant";

        } catch (Exception e) {
            log.error("Failed to register merchant - Store: {}, Error: {}", storeName, e.getMessage(), e);
            model.addAttribute("error", "Failed to register merchant: " + e.getMessage());
            return "register-merchant";
        }
    }

    @PostMapping("/transactions/today")
    public String getTodayTransactions(@RequestParam String merchantId, Model model, Authentication authentication) {
        log.info("Today's transactions requested for merchantId: {}", merchantId);

        try {
            log.debug("Calling ApiService.getTodayTransactions for {}", merchantId);
            TransactionResponse response = apiService.getTodayTransactions(merchantId);
            log.info("Today's transactions loaded - {} items",
                    response != null && response.getTransactions() != null ? response.getTransactions().size() : 0);

            model.addAttribute("transactions", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("view", "today");
            model.addAttribute("fromDate", LocalDate.now());
            model.addAttribute("toDate", LocalDate.now());
            model.addAttribute("selectedStatus", "");
        } catch (Exception e) {
            log.error("Failed to fetch today's transactions for merchantId {}: {}", merchantId, e.getMessage(), e);
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

        log.info("Date range transactions requested - merchantId: {}, from: {}, to: {}, status: {}",
                merchantId, fromDate, toDate, status);

        try {
            log.debug("Calling ApiService.getTransactionsByDateRange");
            TransactionResponse response = apiService.getTransactionsByDateRange(merchantId, fromDate, toDate, status);
            log.info("Range transactions loaded - {} items",
                    response != null && response.getTransactions() != null ? response.getTransactions().size() : 0);

            model.addAttribute("transactions", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("fromDate", fromDate);
            model.addAttribute("toDate", toDate);
            model.addAttribute("selectedStatus", status != null ? status : "");
            model.addAttribute("view", "range");
        } catch (Exception e) {
            log.error("Failed to fetch date range transactions for merchantId {}: {}", merchantId, e.getMessage(), e);
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

        log.info("Summary requested - merchantId: {}, from: {}, to: {}", merchantId, fromDate, toDate);

        try {
            log.debug("Calling ApiService.getSummary");
            SummaryResponse response = apiService.getSummary(merchantId, fromDate, toDate);
            log.info("Summary loaded successfully for merchantId: {}", merchantId);

            model.addAttribute("summary", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("fromDate", fromDate);
            model.addAttribute("toDate", toDate);
            model.addAttribute("view", "summary");
        } catch (Exception e) {
            log.error("Failed to fetch summary for merchantId {}: {}", merchantId, e.getMessage(), e);
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
        String password = sb.toString();
        log.debug("Generated temporary password (length: {})", password.length());
        return password;
    }
}