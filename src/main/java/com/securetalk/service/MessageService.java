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

    /**
     * Marque tous les messages reçus non lus d'une conversation comme lus pour un utilisateur
     * @param conversationId L'ID de l'utilisateur partenaire (autre participant de la conversation)
     * @param userId L'ID de l'utilisateur connecté
     */
    @Transactional
    public void markConversationAsRead(Long conversationId, Long userId) {
        User currentUser = userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("Utilisateur non trouvé"));
        User partner = userRepository.findById(conversationId)
            .orElseThrow(() -> new NoSuchElementException("Partenaire de conversation non trouvé"));
        List<Message> messages = messageRepository.findConversation(partner, currentUser);
        messages.stream()
            .filter(m -> m.getRecipient().getId().equals(userId) && m.getStatus() != MessageStatus.READ)
            .forEach(m -> {
                m.setStatus(MessageStatus.READ);
                messageRepository.save(m);
            });
    }

    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EncryptionUtil encryptionUtil;
    
     /**
     * Envoie un message d'un utilisateur à un autre avec double chiffrement
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
        
        // Chiffrer le contenu du message pour les deux parties
        String[] encryptionResult = encryptionUtil.encryptMessageForBoth(content, senderId, recipientId);
        String encryptedForRecipient = encryptionResult[0];
        String ivRecipient = encryptionResult[1];
        String encryptedForSender = encryptionResult[2];
        String ivSender = encryptionResult[3];
        
        Message message = new Message(sender, recipient, encryptedForRecipient, ivRecipient, 
                                     encryptedForSender, ivSender);
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
     * Utilise automatiquement la bonne version chiffrée selon l'utilisateur
     * 
     * @param message Le message à déchiffrer
     * @param userId L'ID de l'utilisateur qui souhaite lire le message
     * @return Le contenu déchiffré du message
     */
    public String decryptMessage(Message message, Long userId) {
        try {
            return encryptionUtil.decryptMessageForUser(message, userId);
        } catch (SecurityException e) {
            // Re-lancer les exceptions de sécurité
            throw e;
        } catch (Exception e) {
            // Log l'erreur mais retourne un message par défaut pour éviter de bloquer l'application
            System.err.println("Erreur lors du déchiffrement du message " + message.getId() + 
                             " pour l'utilisateur " + userId + ": " + e.getMessage());
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

    /**
     * Méthode utilitaire pour récupérer le contenu déchiffré d'un message
     * sans lever d'exception en cas d'erreur
     * 
     * @param message Le message à déchiffrer
     * @param userId L'ID de l'utilisateur qui souhaite lire le message
     * @return Le contenu déchiffré ou un message d'erreur si le déchiffrement échoue
     */
    public String safeDecryptMessage(Message message, Long userId) {
        try {
            return decryptMessage(message, userId);
        } catch (Exception e) {
            return "[Message non déchiffrable]";
        }
    }
}
