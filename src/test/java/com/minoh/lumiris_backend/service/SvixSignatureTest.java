package com.minoh.lumiris_backend.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SvixSignatureTest {

    private static final String SECRET_B64 = Base64.getEncoder().encodeToString("super-secret-key".getBytes());
    private static final String SECRET = "whsec_" + SECRET_B64;

    private static String sign(String id, String ts, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET_B64), "HmacSHA256"));
        String signed = id + "." + ts + "." + body;
        return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void verify_acceptsAValidSignature() throws Exception {
        String body = "{\"type\":\"email.bounced\"}";
        String sig = sign("msg_1", "1700000000", body);

        assertThat(SvixSignature.verify(SECRET, "msg_1", "1700000000", sig, body)).isTrue();
    }

    @Test
    void verify_rejectsATamperedBody() throws Exception {
        String sig = sign("msg_1", "1700000000", "{\"type\":\"email.bounced\"}");

        assertThat(SvixSignature.verify(SECRET, "msg_1", "1700000000", sig, "{\"type\":\"email.delivered\"}"))
                .isFalse();
    }

    @Test
    void verify_rejectsMissingHeaders() {
        assertThat(SvixSignature.verify(SECRET, null, null, null, "{}")).isFalse();
    }
}
