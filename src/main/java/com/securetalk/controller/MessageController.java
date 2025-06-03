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
    
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> sendMessage(@RequestBody SendMessageRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        
        // Logs de débogage
        System.out.println("Requête reçue pour envoi de message:");
        System.out.println("- Utilisateur courant: " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");
        System.out.println("- Destinataire ID: " + request.getRecipientId());
        System.out.println("- Contenu: " + (request.getContent() != null ? "[présent]" : "[null]"));
        System.out.println("- Type de message: " + request.getMessageType());
        
        try {
            // Validation des données requises
            if (request.getRecipientId() == null) {
                throw new IllegalArgumentException("L'ID du destinataire est requis");
            }
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                throw new IllegalArgumentException("Le contenu du message ne peut pas être vide");
            }
            
            Message message = messageService.sendMessage(
                currentUser.getId(), 
                request.getRecipientId(), 
                request.getContent()
            );
            
            // Convertir le message en MessageDto et le renvoyer
            MessageDto messageDto = convertToDto(message, currentUser.getId());
            System.out.println("Message envoyé avec succès, ID: " + messageDto.getId());
            return ResponseEntity.ok(messageDto);
        } catch (Exception e) {
            System.out.println("Erreur lors de l'envoi du message: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
    
    @GetMapping("/conversation/{userId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getConversation(@PathVariable Long userId) {
        UserDetailsImpl currentUser = getCurrentUser();
        System.out.println("Récupération des messages de la conversation entre l'utilisateur " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ") et l'utilisateur avec ID: " + userId);
        
        try {
            // Vérification que l'utilisateur existe
            if (userId == null) {
                String errorMsg = "L'ID de l'utilisateur ne peut pas être null";
                System.out.println("Erreur: " + errorMsg);
                return ResponseEntity.badRequest().body(new MessageResponse(errorMsg));
            }
            
            List<Message> messages = messageService.getConversation(currentUser.getId(), userId);
            System.out.println("Nombre de messages trouvés dans la conversation: " + messages.size());
            
            List<MessageDto> messageDtos = messages.stream()
                .map(message -> {
                    try {
                        return convertToDto(message, currentUser.getId());
                    } catch (Exception e) {
                        System.out.println("Erreur lors de la conversion du message ID " + message.getId() + ": " + e.getMessage());
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(dto -> dto != null) // Filtrer les messages qui n'ont pas pu être convertis
                .collect(Collectors.toList());
            
            System.out.println("Nombre de messages DTO après conversion: " + messageDtos.size());
            return ResponseEntity.ok(messageDtos);
        } catch (Exception e) {
            System.out.println("Erreur lors de la récupération des messages: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
    
    @GetMapping("/conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserConversations() {
        UserDetailsImpl currentUser = getCurrentUser();
        System.out.println("Récupération des conversations pour l'utilisateur: " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");
        
        try {
            List<User> conversationPartners = messageService.getUserConversations(currentUser.getId());
            System.out.println("Nombre de partenaires de conversation trouvés: " + conversationPartners.size());
            
            List<ConversationDto> conversations = conversationPartners.stream()
                    .map(partner -> {
                        System.out.println("Traitement de la conversation avec: " + partner.getUsername() + " (ID: " + partner.getId() + ")");
                        List<Message> messages = messageService.getConversation(currentUser.getId(), partner.getId());
                        System.out.println("Nombre de messages dans la conversation: " + messages.size());
                        
                        Message lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);
                        
                        ConversationDto conversation = new ConversationDto();
                        conversation.setId(partner.getId());
                        conversation.setParticipant(new UserInfoDto(partner));
                        
                        if (lastMessage != null) {
                            try {
                                MessageDto lastMessageDto = convertToDto(lastMessage, currentUser.getId());
                                conversation.setLastMessage(lastMessageDto);
                                conversation.setLastActivity(lastMessage.getTimestamp());
                                long unreadCount = messages.stream()
                                        .filter(m -> m.getRecipient().getId().equals(currentUser.getId()) && m.getStatus() != MessageStatus.READ)
                                        .count();
                                conversation.setUnreadCount((int) unreadCount);
                            } catch (Exception e) {
                                System.out.println("Erreur lors de la conversion du dernier message: " + e.getMessage());
                                e.printStackTrace();
                            }
                        } else {
                            System.out.println("Aucun message dans cette conversation");
                            // Initialiser avec des valeurs par défaut pour éviter les NullPointerException
                            conversation.setUnreadCount(0);
                            conversation.setLastActivity(partner.getCreatedAt());
                        }
                        
                        return conversation;
                    })
                    .collect(Collectors.toList());
            
            System.out.println("Conversations récupérées avec succès: " + conversations.size());
            return ResponseEntity.ok(conversations);
        } catch (Exception e) {
            System.out.println("Erreur lors de la récupération des conversations: " + e.getMessage());
            e.printStackTrace();
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
