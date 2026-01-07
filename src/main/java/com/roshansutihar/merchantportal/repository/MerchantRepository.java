package com.roshansutihar.merchantportal.repository;

import com.roshansutihar.merchantportal.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, String> {

    Optional<Merchant> findByMerchantId(String merchantId);

    boolean existsBySiteId(String siteId);

    Optional<Merchant> findBySiteId(String siteId);
}
