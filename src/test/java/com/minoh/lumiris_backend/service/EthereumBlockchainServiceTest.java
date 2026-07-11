package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class EthereumBlockchainServiceTest {

    @Mock
    private Web3j web3j;

    @Mock
    private DppFormRepository dppFormRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    // Clé privée de test déterministe (ne jamais utiliser en prod)
    private final Credentials credentials = Credentials.create(
            "1000000000000000000000000000000000000000000000000000000000000001");

    private EthereumBlockchainService service;

    private static final String HASH = "6b3ae768908dba782f9ba7919f448b657d17d895c4490ab21c9aac18ec3cd487";
    private static final String TX_HASH = "0xdeadbeef000000000000000000000000000000000000000000000000deadbeef";

    @BeforeEach
    void setUp() {
        service = new EthereumBlockchainService(web3j, credentials, dppFormRepository, jdbcTemplate);
    }

    // ── retrieveHash ──────────────────────────────────────────────────────────

    @Test
    void retrieveHash_shouldExtractHashFromTransactionInput() throws IOException {
        Transaction tx = mock(Transaction.class);
        when(tx.getInput()).thenReturn("0x" + HASH);

        EthTransaction ethTransaction = mock(EthTransaction.class);
        when(ethTransaction.getTransaction()).thenReturn(Optional.of(tx));

        Request mockRequest = mock(Request.class);
        when(mockRequest.send()).thenReturn(ethTransaction);
        when(web3j.ethGetTransactionByHash(TX_HASH)).thenReturn(mockRequest);

        String result = service.retrieveHash(TX_HASH);

        assertThat(result).isEqualTo(HASH);
    }

    @Test
    void retrieveHash_shouldHandleInputWithoutHexPrefix() throws IOException {
        Transaction tx = mock(Transaction.class);
        when(tx.getInput()).thenReturn(HASH); // pas de "0x"

        EthTransaction ethTransaction = mock(EthTransaction.class);
        when(ethTransaction.getTransaction()).thenReturn(Optional.of(tx));

        Request mockRequest = mock(Request.class);
        when(mockRequest.send()).thenReturn(ethTransaction);
        when(web3j.ethGetTransactionByHash(TX_HASH)).thenReturn(mockRequest);

        String result = service.retrieveHash(TX_HASH);

        assertThat(result).isEqualTo(HASH);
    }

    @Test
    void retrieveHash_shouldThrowWhenTransactionNotFoundOnChain() throws IOException {
        EthTransaction ethTransaction = mock(EthTransaction.class);
        when(ethTransaction.getTransaction()).thenReturn(Optional.empty());

        Request mockRequest = mock(Request.class);
        when(mockRequest.send()).thenReturn(ethTransaction);
        when(web3j.ethGetTransactionByHash(TX_HASH)).thenReturn(mockRequest);

        assertThatThrownBy(() -> service.retrieveHash(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transaction not found on blockchain");
    }

    // ── anchorAsync ───────────────────────────────────────────────────────────

    @Test
    void anchorAsync_shouldUpdateStatusToAnchored_whenTransactionConfirmed() throws Exception {
        UUID dppFormId = UUID.randomUUID();
        mockSuccessfulTransaction(TX_HASH, "0x1");

        service.anchorAsync(dppFormId, HASH);

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), eq(TX_HASH), statusCaptor.capture(), eq(dppFormId.toString()));
        assertThat(statusCaptor.getValue()).isEqualTo(BlockchainAnchorStatus.ANCHORED.name());
    }

    @Test
    void anchorAsync_shouldUpdateStatusToFailed_whenTransactionReverted() throws Exception {
        UUID dppFormId = UUID.randomUUID();
        mockSuccessfulTransaction(TX_HASH, "0x0"); // status revert

        service.anchorAsync(dppFormId, HASH);

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), eq(TX_HASH), statusCaptor.capture(), eq(dppFormId.toString()));
        assertThat(statusCaptor.getValue()).isEqualTo(BlockchainAnchorStatus.FAILED.name());
    }

    @Test
    void anchorAsync_shouldUpdateStatusToFailed_whenRpcReturnsError() throws Exception {
        UUID dppFormId = UUID.randomUUID();

        // Nonce OK
        mockEthGetTransactionCount(BigInteger.ZERO);
        mockEthGasPrice(BigInteger.valueOf(1_000_000_000L));

        // La tx retourne une erreur RPC
        EthSendTransaction sendTx = mock(EthSendTransaction.class);
        when(sendTx.hasError()).thenReturn(true);
        Response.Error error = mock(Response.Error.class);
        when(error.getMessage()).thenReturn("nonce too low");
        when(sendTx.getError()).thenReturn(error);

        Request sendRequest = mock(Request.class);
        when(sendRequest.send()).thenReturn(sendTx);
        when(web3j.ethSendRawTransaction(anyString())).thenReturn(sendRequest);

        service.anchorAsync(dppFormId, HASH);

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), isNull(), statusCaptor.capture(), eq(dppFormId.toString()));
        assertThat(statusCaptor.getValue()).isEqualTo(BlockchainAnchorStatus.FAILED.name());
    }

    @Test
    void anchorAsync_shouldUpdateStatusToFailed_whenNetworkThrows() throws Exception {
        UUID dppFormId = UUID.randomUUID();

        Request mockRequest = mock(Request.class);
        when(mockRequest.send()).thenThrow(new IOException("Connection refused"));
        when(web3j.ethGetTransactionCount(anyString(), any())).thenReturn(mockRequest);

        service.anchorAsync(dppFormId, HASH);

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), isNull(), statusCaptor.capture(), eq(dppFormId.toString()));
        assertThat(statusCaptor.getValue()).isEqualTo(BlockchainAnchorStatus.FAILED.name());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void mockSuccessfulTransaction(String txHash, String receiptStatus) throws Exception {
        mockEthGetTransactionCount(BigInteger.ZERO);
        mockEthGasPrice(BigInteger.valueOf(1_000_000_000L));

        EthSendTransaction sendTx = mock(EthSendTransaction.class);
        when(sendTx.hasError()).thenReturn(false);
        when(sendTx.getTransactionHash()).thenReturn(txHash);
        Request sendRequest = mock(Request.class);
        when(sendRequest.send()).thenReturn(sendTx);
        when(web3j.ethSendRawTransaction(anyString())).thenReturn(sendRequest);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getStatus()).thenReturn(receiptStatus);
        EthGetTransactionReceipt ethReceipt = mock(EthGetTransactionReceipt.class);
        when(ethReceipt.getTransactionReceipt()).thenReturn(Optional.of(receipt));
        Request receiptRequest = mock(Request.class);
        when(receiptRequest.send()).thenReturn(ethReceipt);
        when(web3j.ethGetTransactionReceipt(txHash)).thenReturn(receiptRequest);
    }

    private void mockEthGetTransactionCount(BigInteger nonce) throws Exception {
        EthGetTransactionCount ethCount = mock(EthGetTransactionCount.class);
        when(ethCount.getTransactionCount()).thenReturn(nonce);
        Request countRequest = mock(Request.class);
        when(countRequest.send()).thenReturn(ethCount);
        when(web3j.ethGetTransactionCount(anyString(), any())).thenReturn(countRequest);
    }

    private void mockEthGasPrice(BigInteger gasPrice) throws Exception {
        EthGasPrice ethGasPrice = mock(EthGasPrice.class);
        when(ethGasPrice.getGasPrice()).thenReturn(gasPrice);
        Request gasPriceRequest = mock(Request.class);
        when(gasPriceRequest.send()).thenReturn(ethGasPrice);
        when(web3j.ethGasPrice()).thenReturn(gasPriceRequest);
    }
}
