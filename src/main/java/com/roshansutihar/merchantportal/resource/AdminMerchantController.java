package com.roshansutihar.merchantportal.resource;

import com.roshansutihar.merchantportal.entity.Merchant;
import com.roshansutihar.merchantportal.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminMerchantController {

    private final MerchantRepository merchantRepository;
    private static final Logger log = LoggerFactory.getLogger(AdminMerchantController.class);

    public AdminMerchantController(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
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
}
