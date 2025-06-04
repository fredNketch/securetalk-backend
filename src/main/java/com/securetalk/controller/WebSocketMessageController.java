package com.securetalk.controller;

import com.securetalk.payload.dto.MessageDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketMessageController {
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Utilisé pour envoyer un message à un utilisateur spécifique
    public void sendToUser(Long userId, MessageDto messageDto) {
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", messageDto);
    }
}
