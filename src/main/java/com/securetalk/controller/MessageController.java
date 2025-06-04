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

    // --- Marquer une conversation comme lue ---
    @PutMapping("/conversation/{conversationId}/read")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> markConversationAsRead(@PathVariable Long conversationId) {
        UserDetailsImpl currentUser = getCurrentUser();
        System.out.println("Marquage de la conversation " + conversationId + " comme lue par l'utilisateur " + currentUser.getId());
        try {
            messageService.markConversationAsRead(conversationId, currentUser.getId());
            return ResponseEntity.ok(new MessageResponse("Conversation marquée comme lue"));
        } catch (Exception e) {
            System.out.println("Erreur lors du marquage de la conversation comme lue: " + e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @Autowired
    private WebSocketMessageController webSocketMessageController;
    
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
            
            // Vérifier que l'utilisateur n'essaie pas de s'envoyer un message à lui-même
            if (currentUser.getId().equals(request.getRecipientId())) {
                throw new IllegalArgumentException("Vous ne pouvez pas vous envoyer un message à vous-même");
            }
            
            Message message = messageService.sendMessage(
                currentUser.getId(), 
                request.getRecipientId(), 
                request.getContent()
            );
            
            // Convertir le message en MessageDto - maintenant l'expéditeur peut déchiffrer son message
            MessageDto messageDto = convertToDto(message, currentUser.getId());
            System.out.println("Message envoyé avec succès, ID: " + messageDto.getId());

            // Notifier en temps réel via WebSocket
            // Notifier l'expéditeur (pour accusé d'envoi instantané)
            webSocketMessageController.sendToUser(currentUser.getId(), messageDto);
            
            // Notifier le destinataire (qui peut maintenant déchiffrer le message avec sa propre clé)
            MessageDto recipientDto = convertToDto(message, request.getRecipientId());
            webSocketMessageController.sendToUser(request.getRecipientId(), recipientDto);

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
        System.out.println("Récupération des messages de la conversation entre l'utilisateur " + 
                          currentUser.getUsername() + " (ID: " + currentUser.getId() + 
                          ") et l'utilisateur avec ID: " + userId);
        
        try {
            // Vérification que l'utilisateur existe
            if (userId == null) {
                String errorMsg = "L'ID de l'utilisateur ne peut pas être null";
                System.out.println("Erreur: " + errorMsg);
                return ResponseEntity.badRequest().body(new MessageResponse(errorMsg));
            }
            
            // Vérifier que l'utilisateur n'essaie pas de récupérer une conversation avec lui-même
            if (currentUser.getId().equals(userId)) {
                String errorMsg = "Vous ne pouvez pas avoir une conversation avec vous-même";
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
                        System.out.println("Erreur lors de la conversion du message ID " + 
                                         message.getId() + ": " + e.getMessage());
                        e.printStackTrace();
                        // Retourner un message d'erreur au lieu de null
                        MessageDto errorDto = new MessageDto();
                        errorDto.setId(message.getId());
                        errorDto.setSenderId(message.getSender().getId());
                        errorDto.setRecipientId(message.getRecipient().getId());
                        errorDto.setContent("[Erreur de déchiffrement]");
                        errorDto.setTimestamp(message.getTimestamp());
                        errorDto.setStatus(message.getStatus().toString());
                        errorDto.setMessageType("text");
                        errorDto.setOwnMessage(message.getSender().getId().equals(currentUser.getId()));
                        return errorDto;
                    }
                })
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
        System.out.println("Récupération des conversations pour l'utilisateur: " + 
                          currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");
        
        try {
            List<User> conversationPartners = messageService.getUserConversations(currentUser.getId());
            System.out.println("Nombre de partenaires de conversation trouvés: " + conversationPartners.size());
            
            List<ConversationDto> conversations = conversationPartners.stream()
                    .map(partner -> {
                        System.out.println("Traitement de la conversation avec: " + 
                                         partner.getUsername() + " (ID: " + partner.getId() + ")");
                        
                        ConversationDto conversation = new ConversationDto();
                        conversation.setId(partner.getId());
                        conversation.setParticipant(new UserInfoDto(partner));
                        
                        try {
                            List<Message> messages = messageService.getConversation(currentUser.getId(), partner.getId());
                            System.out.println("Nombre de messages dans la conversation: " + messages.size());
                            
                            if (!messages.isEmpty()) {
                                Message lastMessage = messages.get(messages.size() - 1);
                                
                                try {
                                    MessageDto lastMessageDto = convertToDto(lastMessage, currentUser.getId());
                                    conversation.setLastMessage(lastMessageDto);
                                    conversation.setLastActivity(lastMessage.getTimestamp());
                                    
                                    // Compter les messages non lus
                                    long unreadCount = messages.stream()
                                            .filter(m -> m.getRecipient().getId().equals(currentUser.getId()) && 
                                                       m.getStatus() != MessageStatus.READ)
                                            .count();
                                    conversation.setUnreadCount((int) unreadCount);
                                    
                                } catch (Exception e) {
                                    System.out.println("Erreur lors de la conversion du dernier message: " + e.getMessage());
                                    e.printStackTrace();
                                    // Initialiser avec des valeurs par défaut
                                    conversation.setUnreadCount(0);
                                    conversation.setLastActivity(lastMessage.getTimestamp());
                                }
                            } else {
                                System.out.println("Aucun message dans cette conversation");
                                // Initialiser avec des valeurs par défaut pour éviter les NullPointerException
                                conversation.setUnreadCount(0);
                                conversation.setLastActivity(partner.getCreatedAt());
                            }
                            
                        } catch (Exception e) {
                            System.out.println("Erreur lors de la récupération des messages pour la conversation avec " + 
                                             partner.getUsername() + ": " + e.getMessage());
                            // Initialiser avec des valeurs par défaut
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
    
    /**
     * Marquer un message comme lu
     */
    @PutMapping("/{messageId}/read")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> markMessageAsRead(@PathVariable Long messageId) {
        UserDetailsImpl currentUser = getCurrentUser();
        System.out.println("Marquage du message " + messageId + " comme lu par l'utilisateur " + currentUser.getId());
        
        try {
            // Cette fonctionnalité pourrait être ajoutée au MessageService
            // messageService.markAsRead(messageId, currentUser.getId());
            return ResponseEntity.ok(new MessageResponse("Message marqué comme lu"));
        } catch (Exception e) {
            System.out.println("Erreur lors du marquage du message comme lu: " + e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
    
    /**
     * Récupérer le nombre de messages non lus
     */
    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUnreadMessageCount() {
        UserDetailsImpl currentUser = getCurrentUser();
        
        try {
            // Cette fonctionnalité pourrait être ajoutée au MessageService
            // int unreadCount = messageService.getUnreadCount(currentUser.getId());
            int unreadCount = 0; // Placeholder
            return ResponseEntity.ok(unreadCount);
        } catch (Exception e) {
            System.out.println("Erreur lors de la récupération du nombre de messages non lus: " + e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
    
    /**
     * Récupère l'utilisateur actuellement connecté
     */
    private UserDetailsImpl getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UserDetailsImpl) authentication.getPrincipal();
    }
    
    /**
     * Convertit un Message en MessageDto avec le contenu déchiffré
     * Utilise maintenant le nouveau système de double chiffrement
     */
    private MessageDto convertToDto(Message message, Long currentUserId) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setSenderId(message.getSender().getId());
        dto.setRecipientId(message.getRecipient().getId());

        // Avec le nouveau système, chaque utilisateur peut déchiffrer le message
        // qu'il soit expéditeur ou destinataire
        String decryptedContent = messageService.safeDecryptMessage(message, currentUserId);
        dto.setContent(decryptedContent);

        dto.setTimestamp(message.getTimestamp());
        dto.setStatus(message.getStatus().toString());
        dto.setMessageType("text");
        dto.setOwnMessage(message.getSender().getId().equals(currentUserId));
        
        return dto;
    }
}