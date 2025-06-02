package com.securetalk.controller;

import com.securetalk.model.Message;
import com.securetalk.model.MessageStatus;
import com.securetalk.model.User;
import com.securetalk.payload.dto.ConversationDto;
import com.securetalk.payload.dto.MessageDto;
import com.securetalk.payload.dto.UserInfoDto;
import com.securetalk.payload.request.SendMessageRequest;
import com.securetalk.payload.response.MessageResponse;
import com.securetalk.security.services.UserDetailsImpl;
import com.securetalk.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/messages")
public class MessageController {
    
    @Autowired
    private MessageService messageService;
    
    @PostMapping("/send")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> sendMessage(@RequestBody SendMessageRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        
        try {
            Message message = messageService.sendMessage(
                currentUser.getId(), 
                request.getRecipientId(), 
                request.getContent()
            );
            
            return ResponseEntity.ok(new MessageResponse("Message envoyé avec succès"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
    
    @GetMapping("/conversation/{userId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getConversation(@PathVariable Long userId) {
        UserDetailsImpl currentUser = getCurrentUser();
        
        try {
            List<Message> messages = messageService.getConversation(currentUser.getId(), userId);
            
            List<MessageDto> messageDtos = messages.stream()
                .map(message -> convertToDto(message, currentUser.getId()))
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(messageDtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
    
    @GetMapping("/conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserConversations() {
        UserDetailsImpl currentUser = getCurrentUser();
        
        try {
            List<User> conversationPartners = messageService.getUserConversations(currentUser.getId());
            
            List<ConversationDto> conversations = conversationPartners.stream()
                    .map(partner -> {
                        List<Message> messages = messageService.getConversation(currentUser.getId(), partner.getId());
                        Message lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);
                        
                        ConversationDto conversation = new ConversationDto();
                        conversation.setId(partner.getId());
                        conversation.setParticipant(new UserInfoDto(partner));
                        
                        if (lastMessage != null) {
                            conversation.setLastMessage(convertToDto(lastMessage, currentUser.getId()));
                            conversation.setLastActivity(lastMessage.getTimestamp());
                            long unreadCount = messages.stream()
                                    .filter(m -> m.getRecipient().getId().equals(currentUser.getId()) && m.getStatus() != MessageStatus.READ)
                                    .count();
                            conversation.setUnreadCount((int) unreadCount);
                        }
                        
                        return conversation;
                    })
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(conversations);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
    
    private UserDetailsImpl getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UserDetailsImpl) authentication.getPrincipal();
    }
    
    /**
     * Convertit un Message en MessageDto avec le contenu déchiffré
     */
    private MessageDto convertToDto(Message message, Long currentUserId) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setSenderId(message.getSender().getId());
        dto.setRecipientId(message.getRecipient().getId());
        
        // Déchiffrer le contenu du message
        String decryptedContent = messageService.decryptMessage(message, currentUserId);
        dto.setContent(decryptedContent);
        
        dto.setTimestamp(message.getTimestamp());
        dto.setStatus(message.getStatus().toString());
        dto.setMessageType("text"); // Par défaut pour l'instant
        
        // Déterminer si c'est un message envoyé par l'utilisateur courant
        dto.setOwnMessage(message.getSender().getId().equals(currentUserId));
        
        return dto;
    }

}
