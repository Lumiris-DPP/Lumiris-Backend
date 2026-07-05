package com.minoh.lumiris_backend.service;

import org.springframework.stereotype.Service;

@Service
public class CmaService {

    // ponytail: mock — wire real CMA API when credentials available
    public boolean isRegisteredArtisan(String siret) {
        return true;
    }
}
