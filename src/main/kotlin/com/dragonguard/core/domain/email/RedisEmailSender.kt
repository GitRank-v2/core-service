package com.dragonguard.core.domain.email

import com.dragonguard.core.domain.email.dto.EmailSendRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisEmailSender(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : EmailSender {

    override fun send(request: EmailSendRequest) {
        val message = objectMapper.writeValueAsString(request)
        redisTemplate.convertAndSend(TOPIC, message)
    }

    companion object {
        private const val TOPIC = "alert:email"
    }
}
