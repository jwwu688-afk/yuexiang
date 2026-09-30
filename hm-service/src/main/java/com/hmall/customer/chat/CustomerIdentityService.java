package com.hmall.customer.chat;

import com.hmall.common.exception.UnauthorizedException;
import com.hmall.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomerIdentityService {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTool tokens;

    public CustomerActor actor(String authorization, String guestKey) {
        if (authorization == null || authorization.isBlank()) {
            return new CustomerActor(null, guestKey);
        }

        String token = authorization.trim();
        if (token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            token = token.substring(BEARER_PREFIX.length()).trim();
        }
        if (token.isEmpty()) {
            throw unauthorized(null);
        }

        try {
            return new CustomerActor(tokens.parseToken(token), null);
        } catch (UnauthorizedException error) {
            throw unauthorized(error);
        }
    }

    private static ResponseStatusException unauthorized(Throwable cause) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录凭证无效", cause);
    }
}
