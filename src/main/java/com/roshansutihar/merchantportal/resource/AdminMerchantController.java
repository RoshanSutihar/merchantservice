package com.roshansutihar.merchantportal.resource;

import com.roshansutihar.merchantportal.entity.Merchant;
import com.roshansutihar.merchantportal.repository.MerchantRepository;
import com.roshansutihar.merchantportal.response.TransactionResponse;
import com.roshansutihar.merchantportal.service.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


@Controller
@RequestMapping("/admin")
public class AdminMerchantController {

    private final MerchantRepository merchantRepository;
    private final ApiService apiService;

    private static final Logger log = LoggerFactory.getLogger(AdminMerchantController.class);

    public AdminMerchantController(MerchantRepository merchantRepository, ApiService apiService) {
        this.merchantRepository = merchantRepository;
        this.apiService = apiService;
    }

    @GetMapping("/merchants")
    public String viewAllMerchants(Model model, Authentication authentication) {
        // Security check - only admins can access
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(granted -> {
                    String authority = granted.getAuthority();
                    return authority.equals("ROLE_ADMIN") ||
                            authority.toUpperCase().contains("ADMIN");
                });

        if (!isAdmin) {
            return "redirect:/dashboard";
        }

        try {
            List<Merchant> merchants = merchantRepository.findAll();
            model.addAttribute("merchants", merchants);
            model.addAttribute("totalMerchants", merchants.size());

        } catch (Exception e) {
            log.error("Error loading merchant list", e);
            model.addAttribute("error", "Unable to load merchant list: " + e.getMessage());
        }

        return "admin-merchants";
    }

    @GetMapping("/transactions")
    public String viewAdminTransactions(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            Model model,
            Authentication authentication) {

        // Admin security check
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().toUpperCase().contains("ADMIN"));

        if (!isAdmin) {
            return "redirect:/dashboard";
        }

        List<Merchant> merchants = merchantRepository.findAll();
        model.addAttribute("merchants", merchants);
        model.addAttribute("totalMerchants", merchants.size());

        // Default date range: last 30 days if none provided
        LocalDate end = (toDate != null) ? toDate : LocalDate.now();
        LocalDate start = (fromDate != null) ? fromDate : end.minusDays(30);

        model.addAttribute("fromDate", start);
        model.addAttribute("toDate", end);
        model.addAttribute("selectedStatus", status != null ? status : "");

        if (merchantId != null && !merchantId.isEmpty()) {
            Optional<Merchant> selectedMerchantOpt = merchants.stream()
                    .filter(m -> m.getMerchantId().equals(merchantId))
                    .findFirst();

            if (selectedMerchantOpt.isPresent()) {
                Merchant selected = selectedMerchantOpt.get();
                model.addAttribute("selectedMerchant", selected);

                try {
                    TransactionResponse transactions = apiService.getTransactionsByDateRange(
                            merchantId, start, end, status);
                    model.addAttribute("transactions", transactions);
                    model.addAttribute("selectedMerchantId", merchantId);

                } catch (Exception e) {
                    log.error("Error fetching transactions for merchant {}", merchantId, e);
                    model.addAttribute("error", "Unable to load transactions: " + e.getMessage());
                }
            } else {
                model.addAttribute("error", "Selected merchant not found.");
            }
        } else {
            model.addAttribute("transactions", null);
        }

        return "admin-transactions";
    }

    @GetMapping("/merchants/{merchantId}/edit")
    public String editMerchant(@PathVariable String merchantId, Model model) {
        Merchant merchant = merchantRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
        model.addAttribute("merchant", merchant);
        return "edit-merchant";
    }

    @PostMapping("/admin/merchants/{merchantId}/update-bank")
    public String updateBankDetails(
            @PathVariable String merchantId,
            @RequestParam String bankAccountNumber,
            @RequestParam String bankRoutingNumber,
            RedirectAttributes redirectAttributes) {

        Merchant merchant = merchantRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        merchant.setBankAccountNumber(bankAccountNumber);
        merchant.setBankRoutingNumber(bankRoutingNumber);
        merchantRepository.save(merchant);

        redirectAttributes.addFlashAttribute("success", "Bank details updated successfully");
        return "redirect:/admin/merchants/" + merchantId + "/edit";
    }

    @PostMapping("/merchants/{merchantId}/rotate-secret")
    public String rotateSecretKey(
            @PathVariable String merchantId,
            RedirectAttributes redirectAttributes) {

        try {
            // Call your API service to rotate secret
            String newFullSecretKey = apiService.rotateSecretKey(merchantId);

            // Update in database (masked)
            Merchant merchant = merchantRepository.findByMerchantId(merchantId)
                    .orElseThrow(() -> new RuntimeException("Merchant not found"));

            String maskedSecretKey = newFullSecretKey.length() > 8
                    ? "••••••••" + newFullSecretKey.substring(newFullSecretKey.length() - 8)
                    : "••••••••";

            merchant.setSecretKey(maskedSecretKey);
            merchantRepository.save(merchant);

            redirectAttributes.addFlashAttribute("newFullSecretKey", newFullSecretKey);
            redirectAttributes.addFlashAttribute("success", "Secret key rotated successfully");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed: " + e.getMessage());
        }

        return "redirect:/admin/merchants/" + merchantId + "/edit";
    }
}