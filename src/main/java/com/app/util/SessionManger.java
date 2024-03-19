package com.app.util;

import com.app.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SessionManger {
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public SessionManger(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public User getUserFromSession(HttpServletRequest request) {
        String sessionId = new SessionCookie().getSessionIdFromCookie(request);

        if (sessionId == null) {
            return null;
        }

        String userJson = redisTemplate.opsForValue().get("user: " + sessionId);
        if (userJson == null) {
            return null;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(userJson, User.class);
        } catch (IOException e) {
            return null;
        }
    }
}
