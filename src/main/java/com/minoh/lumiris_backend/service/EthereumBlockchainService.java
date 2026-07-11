package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EthereumBlockchainService implements BlockchainService {

    private static final long SEPOLIA_CHAIN_ID = 11155111L;
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(50_000);

    private final Web3j web3j;
    private final Credentials credentials;
    private final DppFormRepository dppFormRepository;
    private final JdbcTemplate jdbcTemplate;

    @Async
    @Override
    public void anchorAsync(UUID dppFormId, String hash) {
        try {
            String txHash = sendTransaction(hash);
            log.info("DPP {} anchored on Sepolia, waiting for receipt (tx={})", dppFormId, txHash);

            TransactionReceipt receipt = new PollingTransactionReceiptProcessor(web3j, 3_000, 60)
                    .waitForTransactionReceipt(txHash);

            if ("0x1".equals(receipt.getStatus())) {
                updateStatus(dppFormId, txHash, BlockchainAnchorStatus.ANCHORED);
                log.info("DPP {} anchor confirmed (tx={})", dppFormId, txHash);
            } else {
                updateStatus(dppFormId, txHash, BlockchainAnchorStatus.FAILED);
                log.warn("DPP {} anchor tx reverted (tx={})", dppFormId, txHash);
            }
        } catch (Exception e) {
            log.error("DPP {} blockchain anchor failed: {}", dppFormId, e.getMessage(), e);
            updateStatus(dppFormId, null, BlockchainAnchorStatus.FAILED);
        }
    }

    @Override
    public String retrieveHash(String txHash) throws IOException {
        Transaction tx = web3j.ethGetTransactionByHash(txHash).send()
                .getTransaction()
                .orElseThrow(() -> new IllegalStateException("Transaction not found on blockchain: " + txHash));
        String input = tx.getInput();
        return input.startsWith("0x") ? input.substring(2) : input;
    }

    private String sendTransaction(String hash) throws Exception {
        String fromAddress = credentials.getAddress();
        BigInteger nonce = web3j
                .ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING)
                .send()
                .getTransactionCount();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        RawTransaction rawTx = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                GAS_LIMIT,
                fromAddress,
                BigInteger.ZERO,
                "0x" + hash
        );

        byte[] signed = TransactionEncoder.signMessage(rawTx, SEPOLIA_CHAIN_ID, credentials);
        EthSendTransaction response = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();

        if (response.hasError()) {
            throw new IllegalStateException("Ethereum RPC error: " + response.getError().getMessage());
        }
        return response.getTransactionHash();
    }

    private void updateStatus(UUID dppFormId, String txHash, BlockchainAnchorStatus status) {
        int rows = jdbcTemplate.update(
                "UPDATE dpp_forms SET blockchain_tx_hash = ?, blockchain_anchor_status = ? WHERE id = ?::uuid",
                txHash, status.name(), dppFormId.toString()
        );
        log.info("DB update for DPP {} → {} row(s), status={}", dppFormId, rows, status);
    }
}
