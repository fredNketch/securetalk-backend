package com.securetalk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    // Contenu chiffré avec la clé du destinataire
    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedContentRecipient;

    // Contenu chiffré avec la clé de l'expéditeur
    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedContentSender;

    // Vecteur d'initialisation pour le chiffrement du destinataire
    @Column(nullable = false)
    private String ivRecipient;

    // Vecteur d'initialisation pour le chiffrement de l'expéditeur
    @Column(nullable = false)
    private String ivSender;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    private MessageStatus status = MessageStatus.SENT;

    // Constructeur avec les champs obligatoires
    public Message(User sender, User recipient, String encryptedContentRecipient, String ivRecipient, 
                   String encryptedContentSender, String ivSender) {
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedContentRecipient = encryptedContentRecipient;
        this.ivRecipient = ivRecipient;
        this.encryptedContentSender = encryptedContentSender;
        this.ivSender = ivSender;
        this.timestamp = LocalDateTime.now();
    }

    // Méthodes utilitaires pour récupérer le bon contenu chiffré selon l'utilisateur
    public String getEncryptedContentForUser(Long userId) {
        if (sender.getId().equals(userId)) {
            return encryptedContentSender;
        } else if (recipient.getId().equals(userId)) {
            return encryptedContentRecipient;
        }
        throw new SecurityException("Utilisateur non autorisé à accéder à ce message");
    }

    public String getIvForUser(Long userId) {
        if (sender.getId().equals(userId)) {
            return ivSender;
        } else if (recipient.getId().equals(userId)) {
            return ivRecipient;
        }
        throw new SecurityException("Utilisateur non autorisé à accéder à ce message");
    }

    // Méthodes pour la compatibilité avec l'ancien code
    @Deprecated
    public String getEncryptedContent() {
        return encryptedContentRecipient;
    }

    @Deprecated
    public void setEncryptedContent(String encryptedContent) {
        this.encryptedContentRecipient = encryptedContent;
    }

    @Deprecated
    public String getIv() {
        return ivRecipient;
    }

    @Deprecated
    public void setIv(String iv) {
        this.ivRecipient = iv;
    }
}