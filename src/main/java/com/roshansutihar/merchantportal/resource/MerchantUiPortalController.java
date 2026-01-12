package com.roshansutihar.merchantportal.resource;


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

        System.out.println("Post-login - Is ADMIN? " + isAdmin);

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
                    return authority.equals("ROLE_ADMIN") ||
                            authority.toUpperCase().contains("ADMIN");
                });

        if (isAdmin) {
            System.out.println("Admin user trying to access dashboard, redirecting to /register-merchant");
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

            TransactionResponse todayTransactions = apiService.getTodayTransactions(merchantId);
            model.addAttribute("transactions", todayTransactions);

            model.addAttribute("view", "today");
            model.addAttribute("fromDate", LocalDate.now());
            model.addAttribute("toDate", LocalDate.now());
            model.addAttribute("selectedStatus", "");

        } catch (Exception e) {
            log.error("Error loading dashboard for siteId {}", siteId, e);
            model.addAttribute("error", "Unable to load dashboard: " + e.getMessage());
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

            // Create Keycloak user with storeName in lastName field
            keycloakAdminService.createMerchantUser(
                    siteId,           // username
                    tempPassword,
                    "Merchant",       // firstName
                    storeName.trim()  // lastName = store name
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
            log.error("Failed to register merchant", e);
            model.addAttribute("error", "Failed to register merchant: " + e.getMessage());
            return "register-merchant";
        }
    }

    @PostMapping("/transactions/today")
    public String getTodayTransactions(@RequestParam String merchantId, Model model, Authentication authentication) {
        try {
            TransactionResponse response = apiService.getTodayTransactions(merchantId);
            model.addAttribute("transactions", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("view", "today");
            model.addAttribute("fromDate", LocalDate.now());
            model.addAttribute("toDate", LocalDate.now());
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