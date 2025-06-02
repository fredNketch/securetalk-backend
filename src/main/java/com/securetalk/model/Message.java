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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedContent;

    // Vecteur d'initialisation pour le chiffrement AES
    @Column(nullable = false)
    private String iv;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    private MessageStatus status = MessageStatus.SENT;

    // Constructeur avec les champs obligatoires
    public Message(User sender, User recipient, String encryptedContent, String iv) {
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedContent = encryptedContent;
        this.iv = iv;
        this.timestamp = LocalDateTime.now();
    }
}
