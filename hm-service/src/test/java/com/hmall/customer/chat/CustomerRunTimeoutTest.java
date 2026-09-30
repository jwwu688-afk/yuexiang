package com.hmall.customer.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerRunTimeoutTest {
    @Autowired CustomerConversationService conversations;
    @Autowired JdbcTemplate db;

    @Test
    void expiresOnlyStalePendingRunsAndLeavesCompletedRunsUntouched() {
        CustomerActor actor = new CustomerActor(1L, null);
        String conversationId = (String) conversations.create(actor).get("conversationId");
        Map<String, Object> stale = conversations.submit(conversationId, actor, "旧请求", "stale-key");
        Map<String, Object> completed = conversations.submit(conversationId, actor, "已完成请求", "done-key");
        String staleRunId = (String) stale.get("runId");
        String completedRunId = (String) completed.get("runId");
        conversations.appendEvent(completedRunId, 1, "completed", "已完成");

        Timestamp old = Timestamp.from(Instant.now().minus(20, ChronoUnit.MINUTES));
        db.update("UPDATE customer_run SET updated_at=? WHERE id IN (?,?)", old, staleRunId, completedRunId);

        assertThat(conversations.expirePendingRuns(Instant.now().minus(10, ChronoUnit.MINUTES))).isEqualTo(1);
        assertThat(db.queryForObject("SELECT status FROM customer_run WHERE id=?", String.class, staleRunId))
                .isEqualTo("FAILED");
        assertThat(db.queryForObject("SELECT event_type FROM customer_event WHERE run_id=?", String.class, staleRunId))
                .isEqualTo("error");
        assertThat(db.queryForObject("SELECT status FROM customer_run WHERE id=?", String.class, completedRunId))
                .isEqualTo("COMPLETED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM customer_event WHERE run_id=?", Integer.class, completedRunId))
                .isEqualTo(1);
    }
}
