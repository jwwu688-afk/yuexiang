package com.hmall.customer.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hm.agent", name = "timeout-job-enabled", havingValue = "true", matchIfMissing = true)
public class CustomerRunTimeoutJob {
    private final CustomerConversationService conversations;

    @Value("${hm.agent.run-timeout:10m}")
    private Duration runTimeout;

    @Scheduled(fixedDelayString = "${hm.agent.timeout-scan-delay-ms:60000}",
            initialDelayString = "${hm.agent.timeout-scan-delay-ms:60000}")
    public void recoverStaleRuns() {
        conversations.expirePendingRuns(Instant.now().minus(runTimeout));
    }
}
