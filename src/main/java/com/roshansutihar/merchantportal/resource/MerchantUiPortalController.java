package com.roshansutihar.merchantportal.resource;


import com.roshansutihar.merchantportal.Response.MerchantResponse;
import com.roshansutihar.merchantportal.Response.SummaryResponse;
import com.roshansutihar.merchantportal.Response.TransactionResponse;
import com.roshansutihar.merchantportal.service.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

import java.util.ArrayList;
import java.util.Map;

@Controller
public class MerchantUiPortalController {

    private final ApiService apiService;

    private static final Logger log = LoggerFactory.getLogger(MerchantUiPortalController.class);

    public MerchantUiPortalController(ApiService apiService) {
        this.apiService = apiService;
    }

    @GetMapping("/")
    public String index(Model model, RedirectAttributes redirectAttributes) {
        // Handle flash attributes for success/error
        if (redirectAttributes.containsAttribute("success")) {
            model.addAttribute("success", redirectAttributes.getFlashAttributes().get("success"));
        }
        if (redirectAttributes.containsAttribute("error")) {
            model.addAttribute("error", redirectAttributes.getFlashAttributes().get("error"));
        }

        try {
            List<String> merchantIds = apiService.getMerchantIds();
            model.addAttribute("merchantIds", merchantIds);
        } catch (Exception e) {
            model.addAttribute("merchantIds", new ArrayList<String>());
            model.addAttribute("error", "Unable to fetch merchant list: " + e.getMessage());
        }
        // Initialize all attributes with safe defaults
        model.addAttribute("selectedMerchant", "");
        model.addAttribute("view", "");
        model.addAttribute("fromDate", LocalDate.now());
        model.addAttribute("toDate", LocalDate.now());
        model.addAttribute("selectedStatus", "");
        return "index";
    }

    @GetMapping("/register-merchant")
    public String showRegisterMerchantForm(Model model, RedirectAttributes redirectAttributes) {
        if (redirectAttributes.containsAttribute("error")) {
            model.addAttribute("error", redirectAttributes.getFlashAttributes().get("error"));
        }
        return "register-merchant";
    }

    @PostMapping("/register-merchant")
    public String registerMerchant(
            @RequestParam String storeName,
            @RequestParam String callbackUrl,
            @RequestParam String commissionType,
            @RequestParam String commissionValue,
            @RequestParam String minCommission,
            @RequestParam String maxCommission,
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
        try {
            if (minCommission != null && !minCommission.trim().isEmpty()) {
                minCommissionBd = new BigDecimal(minCommission);
                if (minCommissionBd.compareTo(BigDecimal.ZERO) < 0) {
                    model.addAttribute("error", "Min commission must be non-negative");
                    return "register-merchant";
                }
            }
        } catch (NumberFormatException e) {
            model.addAttribute("error", "Invalid min commission value");
            return "register-merchant";
        }

        BigDecimal maxCommissionBd = BigDecimal.ZERO;
        try {
            if (maxCommission != null && !maxCommission.trim().isEmpty()) {
                maxCommissionBd = new BigDecimal(maxCommission);
                if (maxCommissionBd.compareTo(BigDecimal.ZERO) < 0) {
                    model.addAttribute("error", "Max commission must be non-negative");
                    return "register-merchant";
                }
            }
        } catch (NumberFormatException e) {
            model.addAttribute("error", "Invalid max commission value");
            return "register-merchant";
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


            model.addAttribute("merchantResponse", response);
            model.addAttribute("success", true);

            return "register-merchant"; // Stay on the same page

        } catch (Exception e) {
            model.addAttribute("error", "Failed to register merchant: " + e.getMessage());
            return "register-merchant";
        }
    }

    @PostMapping("/select-merchant")
    public String selectMerchant(@RequestParam String merchantId, Model model) {
        try {
            List<String> merchantIds = apiService.getMerchantIds();
            model.addAttribute("merchantIds", merchantIds);


            TransactionResponse todayTransactions = apiService.getTodayTransactions(merchantId);
            model.addAttribute("transactions", todayTransactions);

            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("view", "today");
            model.addAttribute("fromDate", LocalDate.now());
            model.addAttribute("toDate", LocalDate.now());
            model.addAttribute("selectedStatus", "");

        } catch (Exception e) {
            model.addAttribute("error", "Error fetching transactions: " + e.getMessage());
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("view", "");
        }
        return "index";
    }

    @PostMapping("/transactions/today")
    public String getTodayTransactions(@RequestParam String merchantId, Model model) {
        try {
            List<String> merchantIds = apiService.getMerchantIds();
            model.addAttribute("merchantIds", merchantIds);

            TransactionResponse response = apiService.getTodayTransactions(merchantId);
            model.addAttribute("transactions", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("view", "today");
            model.addAttribute("fromDate", LocalDate.now());
            model.addAttribute("toDate", LocalDate.now());
            model.addAttribute("selectedStatus", "");

        } catch (Exception e) {
            model.addAttribute("error", "Error fetching today's transactions: " + e.getMessage());
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("view", "today");
        }
        return "index";
    }

    @PostMapping("/transactions/range")
    public String getTransactionsByRange(
            @RequestParam String merchantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            Model model) {
        try {
            List<String> merchantIds = apiService.getMerchantIds();
            model.addAttribute("merchantIds", merchantIds);

            TransactionResponse response = apiService.getTransactionsByDateRange(merchantId, fromDate, toDate, status);
            model.addAttribute("transactions", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("fromDate", fromDate);
            model.addAttribute("toDate", toDate);
            model.addAttribute("selectedStatus", status != null ? status : "");
            model.addAttribute("view", "range");

        } catch (Exception e) {
            model.addAttribute("error", "Error fetching transactions: " + e.getMessage());
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("view", "range");
        }
        return "index";
    }

    @PostMapping("/transactions/summary")
    public String getSummary(
            @RequestParam String merchantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {
        try {
            List<String> merchantIds = apiService.getMerchantIds();
            model.addAttribute("merchantIds", merchantIds);

            SummaryResponse response = apiService.getSummary(merchantId, fromDate, toDate);
            model.addAttribute("summary", response);
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("fromDate", fromDate);
            model.addAttribute("toDate", toDate);
            model.addAttribute("view", "summary");

        } catch (Exception e) {
            model.addAttribute("error", "Error fetching summary: " + e.getMessage());
            model.addAttribute("selectedMerchant", merchantId);
            model.addAttribute("view", "summary");
        }
        return "index";
    }
}