package com.hmall.customer.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerTicketControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Test
    void only_browser_handoff_creates_ticket() throws Exception {
        String created = mvc.perform(post("/customer-service/conversations"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode conversation = json.readTree(created);
        String id = conversation.get("conversationId").asText();
        String guestKey = conversation.get("guestKey").asText();
        String body = "{\"reason\":\"需要人工\",\"idempotencyKey\":\"browser-1\"}";

        String first = mvc.perform(post("/customer-service/conversations/" + id + "/handoff")
                        .header("X-Guest-Key", guestKey).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String retry = mvc.perform(post("/customer-service/conversations/" + id + "/handoff")
                        .header("X-Guest-Key", guestKey).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode a = json.readTree(first);
        JsonNode b = json.readTree(retry);
        assertEquals("QUEUED", a.get("status").asText());
        assertEquals(a.get("ticketId").asText(), b.get("ticketId").asText());

        mvc.perform(post("/internal/customer/tickets").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }
}
