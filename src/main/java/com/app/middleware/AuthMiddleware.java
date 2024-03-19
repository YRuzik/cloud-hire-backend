package com.app.middleware;

import com.app.util.SessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;

public class AuthMiddleware {

    public static boolean sessionExists(HttpServletRequest request, RedisTemplate<String, String> redisTemplate) {
        String sessionId = new SessionCookie().getSessionIdFromCookie(request);

        if (sessionId == null) {
            return false;
        }

        return Boolean.TRUE.equals(redisTemplate.hasKey("user: " + sessionId));
    }
}
