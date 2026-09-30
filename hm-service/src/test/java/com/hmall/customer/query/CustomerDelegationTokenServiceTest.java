package com.hmall.customer.query;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerDelegationTokenServiceTest {
    private final CustomerDelegationTokenService tokens =
            new CustomerDelegationTokenService("local-test-secret-with-at-least-32-characters");

    @Test
    void verifiesRequiredScopeAndAuthenticatedSubject() {
        String token = tokens.issue(42L, Set.of("catalog:read", "order:read"), "conversation-1", "run-1");
        assertThat(tokens.verify("Bearer " + token, "order:read")).isEqualTo(42L);
    }

    @Test
    void rejectsMissingOrWrongScope() {
        String token = tokens.issue(null, Set.of("catalog:read"), "conversation-1", "run-1");
        assertThatThrownBy(() -> tokens.verify(null, "catalog:read"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> tokens.verify("Bearer " + token, "order:read"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatus().value()).isEqualTo(401));
    }
}
