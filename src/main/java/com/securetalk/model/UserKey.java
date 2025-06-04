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
@Table(name = "user_keys")
public class UserKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptionKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean isActive = true;

    // Constructeur avec les champs obligatoires
    public UserKey(User user, String encryptionKey) {
        this.user = user;
        this.encryptionKey = encryptionKey;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        // Par défaut, les clés n'expirent pas, mais on peut ajouter une logique d'expiration
        // this.expiresAt = LocalDateTime.now().plusYears(1);
    }

    // Méthode utilitaire pour vérifier si la clé est valide
    public boolean isValid() {
        return isActive && (expiresAt == null || LocalDateTime.now().isBefore(expiresAt));
    }
}