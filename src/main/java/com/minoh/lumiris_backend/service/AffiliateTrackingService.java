package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.AffiliateTrackRequest;
import com.minoh.lumiris_backend.entity.AffiliateClick;
import com.minoh.lumiris_backend.repository.AffiliateClickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Enregistre les clics d'affiliation externe (append-only). Fire-and-forget côté
// front : on ne bloque jamais la redirection sur ce tracking.
@Service
@RequiredArgsConstructor
public class AffiliateTrackingService {

    private final AffiliateClickRepository affiliateClickRepository;

    @Transactional
    public void track(AffiliateTrackRequest req) {
        affiliateClickRepository.save(new AffiliateClick(
                req.productId(),
                req.source(),
                req.targetUrl(),
                req.dppPublicCode(),
                req.referrer()
        ));
    }
}
