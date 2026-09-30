package com.hmall.customer.chat;

import com.hmall.utils.JwtTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPairGenerator;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerIdentityServiceTest {
    private JwtTool tokens;
    private CustomerIdentityService identities;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        tokens = new JwtTool(generator.generateKeyPair());
        identities = new CustomerIdentityService(tokens);
    }

    @Test
    void acceptsRawAndBearerTokens() {
        String token = tokens.createToken(42L, Duration.ofMinutes(5));

        assertThat(identities.actor(token, "ignored").getUserId()).isEqualTo(42L);
        assertThat(identities.actor("Bearer " + token, "ignored").getUserId()).isEqualTo(42L);
    }

    @Test
    void keepsAnonymousGuestKeyWhenAuthorizationIsBlank() {
        CustomerActor actor = identities.actor("  ", "guest-123");

        assertThat(actor.authenticated()).isFalse();
        assertThat(actor.getGuestKey()).isEqualTo("guest-123");
    }

    @Test
    void rejectsInvalidTokensAsUnauthorized() {
        assertThatThrownBy(() -> identities.actor("Bearer invalid", "guest-123"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatus().value()).isEqualTo(401));
    }
}
