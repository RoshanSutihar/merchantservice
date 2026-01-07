package com.roshansutihar.merchantportal.service;


import com.roshansutihar.merchantportal.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Service
public class SiteIdGeneratorService {

    private static final int SITE_ID_LENGTH = 5;
    private static final int MAX_SITE_ID = 99999;
    private static final int MIN_SITE_ID = 1; // We'll pad to 00001

    private final MerchantRepository merchantRepository;
    private final Random random = new Random();

    public SiteIdGeneratorService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @Transactional
    public String generateUniqueSiteId() {

        int attempts = 0;
        final int maxAttempts = 1000;

        while (attempts < maxAttempts) {
            int number = random.nextInt(MAX_SITE_ID - MIN_SITE_ID + 1) + MIN_SITE_ID;
            String siteId = String.format("%05d", number); // Pads with zeros: 00001, 01234, etc.

            if (!merchantRepository.existsBySiteId(siteId)) {
                return siteId;
            }
            attempts++;
        }

        throw new RuntimeException("Unable to generate unique 5-digit siteId after " + maxAttempts + " attempts. Database may be full.");
    }
}
