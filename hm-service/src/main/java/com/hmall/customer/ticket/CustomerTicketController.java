package com.hmall.customer.ticket;

import com.hmall.customer.chat.CustomerActor;
import com.hmall.customer.chat.CustomerIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CustomerTicketController {
    private final CustomerTicketService tickets;
    private final CustomerIdentityService identities;

    private CustomerActor actor(String authorization, String guestKey) {
        return identities.actor(authorization, guestKey);
    }

    @PostMapping("/customer-service/conversations/{id}/handoff")
    public CustomerTicket handoff(@PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Guest-Key", required = false) String guestKey,
            @RequestBody Map<String, Object> request) {
        return tickets.create(id, actor(authorization, guestKey),
                string(request, "reason"), longOrNull(request.get("orderId")), string(request, "idempotencyKey"));
    }

    @GetMapping("/customer-service/conversations/{id}/ticket")
    public CustomerTicket latest(@PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Guest-Key", required = false) String guestKey) {
        return tickets.latest(id, actor(authorization, guestKey));
    }

    private static String string(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? null : value.toString();
    }

    private static Long longOrNull(Object value) {
        if (value == null) return null;
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单 ID 无效"); }
    }
}
