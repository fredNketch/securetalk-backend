#!/bin/bash

# Corriger UserInfoDto.java
cat > src/main/java/com/securetalk/payload/dto/UserInfoDto.java << 'EOL'
package com.securetalk.payload.dto;

import com.securetalk.model.User;
import com.securetalk.model.UserStatus;

public class UserInfoDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String profilePicture;
    private String status;
    private boolean online;

    public UserInfoDto() {
    }

    public UserInfoDto(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        // Si l'utilisateur a un statut, on le convertit en chaîne
        if (user.getStatus() != null) {
            this.status = user.getStatus().toString();
        }
        this.online = user.isOnline();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
EOL

# Corriger MessageDto.java
cat > src/main/java/com/securetalk/payload/dto/MessageDto.java << 'EOL'
package com.securetalk.payload.dto;

import java.time.LocalDateTime;

public class MessageDto {
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String content;
    private LocalDateTime timestamp;
    private String status;
    private String messageType;
    private boolean ownMessage;

    public MessageDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public boolean isOwnMessage() {
        return ownMessage;
    }

    public void setOwnMessage(boolean ownMessage) {
        this.ownMessage = ownMessage;
    }
}
EOL

# Corriger ConversationDto.java
cat > src/main/java/com/securetalk/payload/dto/ConversationDto.java << 'EOL'
package com.securetalk.payload.dto;

import java.time.LocalDateTime;

public class ConversationDto {
    private Long id;
    private UserInfoDto participant;
    private MessageDto lastMessage;
    private LocalDateTime lastActivity;
    private int unreadCount;

    public ConversationDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserInfoDto getParticipant() {
        return participant;
    }

    public void setParticipant(UserInfoDto participant) {
        this.participant = participant;
    }

    public MessageDto getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(MessageDto lastMessage) {
        this.lastMessage = lastMessage;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
}
EOL

# Corriger MessageController.java
cat > src/main/java/com/securetalk/controller/MessageController.java << 'EOL'
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
EOL

# Corriger MessageService.java
cat > src/main/java/com/securetalk/service/MessageService.java << 'EOL'
package com.securetalk.service;

import com.securetalk.model.Message;
import com.securetalk.model.MessageStatus;
import com.securetalk.model.User;
import com.securetalk.repository.MessageRepository;
import com.securetalk.repository.UserRepository;
import com.securetalk.util.EncryptionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class MessageService {
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EncryptionUtil encryptionUtil;
    
    /**
     * Envoie un message d'un utilisateur à un autre
     * 
     * @param senderId ID de l'expéditeur
     * @param recipientId ID du destinataire
     * @param content Contenu du message en clair
     * @return Le message créé
     */
    @Transactional
    public Message sendMessage(Long senderId, Long recipientId, String content) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new NoSuchElementException("Expéditeur non trouvé"));
        
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new NoSuchElementException("Destinataire non trouvé"));
        
        // Chiffrer le contenu du message avec la clé publique du destinataire
        String[] encryptionResult = encryptionUtil.encryptMessage(content, recipientId);
        String encryptedContent = encryptionResult[0];
        String iv = encryptionResult[1];
        
        Message message = new Message(sender, recipient, encryptedContent, iv);
        return messageRepository.save(message);
    }
    
    /**
     * Récupère la conversation entre deux utilisateurs
     * 
     * @param userId1 ID du premier utilisateur
     * @param userId2 ID du deuxième utilisateur
     * @return Liste des messages échangés entre les deux utilisateurs
     */
    @Transactional(readOnly = true)
    public List<Message> getConversation(Long userId1, Long userId2) {
        User user1 = userRepository.findById(userId1)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur 1 non trouvé"));
        
        User user2 = userRepository.findById(userId2)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur 2 non trouvé"));
        
        List<Message> messages = messageRepository.findConversation(user1, user2);
        
        // Marquer les messages reçus comme lus
        messages.stream()
                .filter(m -> m.getRecipient().getId().equals(userId1) && m.getStatus() != MessageStatus.READ)
                .forEach(m -> {
                    m.setStatus(MessageStatus.READ);
                    messageRepository.save(m);
                });
        
        return messages;
    }
    
    /**
     * Déchiffre le contenu d'un message pour un utilisateur spécifique
     * 
     * @param message Le message à déchiffrer
     * @param userId L'ID de l'utilisateur qui souhaite lire le message
     * @return Le contenu déchiffré du message
     */
    public String decryptMessage(Message message, Long userId) {
        // Vérifier si l'utilisateur est autorisé à lire ce message
        if (!message.getSender().getId().equals(userId) && !message.getRecipient().getId().equals(userId)) {
            throw new SecurityException("Non autorisé à lire ce message");
        }
        
        return encryptionUtil.decryptMessage(message.getEncryptedContent(), message.getIv(), userId);
    }
    
    /**
     * Récupère tous les utilisateurs avec qui l'utilisateur courant a échangé des messages
     * 
     * @param userId ID de l'utilisateur
     * @return Liste des utilisateurs avec qui l'utilisateur a échangé des messages
     */
    @Transactional(readOnly = true)
    public List<User> getUserConversations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur non trouvé"));
        
        return messageRepository.findConversationPartners(user);
    }
}
EOL

# Corriger MessageRepository.java
cat > src/main/java/com/securetalk/repository/MessageRepository.java << 'EOL'
package com.securetalk.repository;

import com.securetalk.model.Message;
import com.securetalk.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    @Query("SELECT m FROM Message m WHERE (m.sender = :user1 AND m.recipient = :user2) OR (m.sender = :user2 AND m.recipient = :user1) ORDER BY m.timestamp ASC")
    List<Message> findConversation(@Param("user1") User user1, @Param("user2") User user2);
    
    @Query("SELECT DISTINCT u FROM User u WHERE u IN (SELECT m.sender FROM Message m WHERE m.recipient = :user) OR u IN (SELECT m.recipient FROM Message m WHERE m.sender = :user)")
    List<User> findConversationPartners(@Param("user") User user);
}
EOL

echo "Correction des imports terminée"
