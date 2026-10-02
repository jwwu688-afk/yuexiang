package com.hmall.customer.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerConversationIntegrationTest {
    @Autowired CustomerConversationService conversations;

    @Test
    void guest_ownership_idempotency_and_event_sequence_survive_database_reads() {
        Map<String, Object> created = conversations.create(new CustomerActor(null, null));
        String id = (String) created.get("conversationId");
        CustomerActor owner = new CustomerActor(null, (String) created.get("guestKey"));
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> conversations.history(id, new CustomerActor(null, "other-key")));
        assertEquals(HttpStatus.NOT_FOUND.value(), forbidden.getRawStatusCode());

        Map<String, Object> first = conversations.submit(id, owner, "查询商品", "browser-retry-key");
        Map<String, Object> retry = conversations.submit(id, owner, "查询商品", "browser-retry-key");
        assertEquals(first.get("messageId"), retry.get("messageId"));
        assertEquals(first.get("runId"), retry.get("runId"));
        assertEquals(false, retry.get("newRun"));

        String runId = (String) first.get("runId");
        long eventId = conversations.appendEvent(runId, 1, "completed", "答复");
        assertEquals(eventId, conversations.appendEvent(runId, 1, "completed", "答复"));
        assertEquals(1, conversations.events(id, owner, 0).size());
        assertTrue(conversations.events(id, owner, eventId).isEmpty());
        assertEquals(2, ((java.util.List<?>) conversations.history(id, owner).get("messages")).size());
    }

    @Test
    void sources_and_ticket_events_are_persisted_before_completion() {
        String id = (String) conversations.create(new CustomerActor(1L, null)).get("conversationId");
        CustomerActor actor = new CustomerActor(1L, null);
        String runId = (String) conversations.submit(id, actor, "退货规则", "event-key").get("runId");
        conversations.appendEvent(runId, 1, "sources", "[{\"policyId\":5}]");
        conversations.appendEvent(runId, 2, "ticket", "{\"ticketId\":\"ticket-1\",\"status\":\"QUEUED\"}");
        conversations.appendEvent(runId, 3, "completed", "已创建排队工单");
        assertEquals(3, conversations.events(id, actor, 0).size());
        assertEquals(3, ((java.util.List<?>) conversations.history(id, actor).get("events")).size());
    }

    @Test
    void images_event_is_persisted_before_completion() {
        String id = (String) conversations.create(new CustomerActor(1L, null)).get("conversationId");
        CustomerActor actor = new CustomerActor(1L, null);
        String runId = (String) conversations.submit(id, actor, "怎么安装", "images-key").get("runId");

        conversations.appendEvent(runId, 1, "images",
                "[\"http://127.0.0.1:9000/manual/panel.jpg\"]");
        conversations.appendEvent(runId, 2, "completed", "说明书答案");

        java.util.List<Map<String, Object>> events = conversations.events(id, actor, 0);
        assertEquals(2, events.size());
        assertEquals("images", events.get(0).get("type"));
        assertEquals("completed", events.get(1).get("type"));
    }

    @Test
    void images_event_rejects_non_array_unsafe_or_excessive_urls() {
        String id = (String) conversations.create(new CustomerActor(1L, null)).get("conversationId");
        CustomerActor actor = new CustomerActor(1L, null);
        String runId = (String) conversations.submit(id, actor, "怎么安装", "invalid-images-key").get("runId");
        String[] invalid = {
                "{\"url\":\"http://127.0.0.1:9000/a.jpg\"}",
                "[\"javascript:alert(1)\"]",
                "[\"http://img/1.jpg\",\"http://img/2.jpg\",\"http://img/3.jpg\",\"http://img/4.jpg\",\"http://img/5.jpg\",\"http://img/6.jpg\"]"
        };

        for (String data : invalid) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> conversations.appendEvent(runId, 1, "images", data));
            assertEquals(HttpStatus.BAD_REQUEST.value(), error.getRawStatusCode());
        }
    }

    @Test
    void duplicate_images_sequence_is_idempotent() {
        String id = (String) conversations.create(new CustomerActor(1L, null)).get("conversationId");
        CustomerActor actor = new CustomerActor(1L, null);
        String runId = (String) conversations.submit(id, actor, "怎么安装", "duplicate-images-key").get("runId");
        String data = "[\"https://images.example/manual.jpg\"]";

        long first = conversations.appendEvent(runId, 1, "images", data);

        assertEquals(first, conversations.appendEvent(runId, 1, "images", data));
        assertEquals(1, conversations.events(id, actor, 0).size());
    }
}
