package com.minoh.lumiris_backend.service;

import java.io.IOException;
import java.util.UUID;

public interface BlockchainService {

    /**
     * Anchors the hash on Ethereum (async — returns immediately, updates DB when mined).
     */
    void anchorAsync(UUID dppFormId, String hash);

    /**
     * Retrieves the hash stored in the calldata of a blockchain transaction.
     */
    String retrieveHash(String txHash) throws IOException;
}
