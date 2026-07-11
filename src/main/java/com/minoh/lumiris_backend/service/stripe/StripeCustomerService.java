package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StripeCustomerService {

    private final StripeProperties properties;
    private final UserRepository userRepository;

    @Transactional
    public String ensureCustomer(User user) {
        properties.requireSecretKey();
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            return user.getStripeCustomerId();
        }
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getName() != null ? user.getName() : user.getEmail())
                .putMetadata("user_id", user.getId().toString())
                .build();
        RequestOptions idempotent = RequestOptions.builder()
                .setIdempotencyKey("customer-create:" + user.getId())
                .build();
        String customerId = StripeCalls.billed("Création du client Stripe impossible",
                () -> Customer.create(params, idempotent).getId());
        user.setStripeCustomerId(customerId);
        userRepository.save(user);
        return customerId;
    }
}
