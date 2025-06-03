package com.securetalk.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_keys")
@Data
@NoArgsConstructor
public class UserKey {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;
    
    @Column(name = "encryption_key", nullable = false, length = 1000)
    private String encryptionKey;
    
    public UserKey(User user, String encryptionKey) {
        this.user = user;
        this.encryptionKey = encryptionKey;
    }
}
