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
        
        try {
            return encryptionUtil.decryptMessage(message.getEncryptedContent(), message.getIv(), userId);
        } catch (Exception e) {
            // Log l'erreur mais retourne un message par défaut pour éviter de bloquer l'application
            System.err.println("Erreur lors du déchiffrement du message " + message.getId() + ": " + e.getMessage());
            return "[Message non déchiffrable]"; 
        }
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
