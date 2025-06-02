package com.securetalk.service;

import com.securetalk.model.User;
import com.securetalk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    /**
     * Récupère tous les utilisateurs
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    /**
     * Récupère un utilisateur par son ID
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    /**
     * Récupère un utilisateur par son nom d'utilisateur
     */
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    /**
     * Met à jour le statut de connexion d'un utilisateur
     */
    public User updateUserStatus(Long userId, boolean online) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        user.setOnline(online);
        if (!online) {
            user.setLastSeen(LocalDateTime.now());
        }
        
        return userRepository.save(user);
    }
    
    /**
     * Met à jour le mot de passe d'un utilisateur
     */
    public User updatePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mot de passe actuel incorrect");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }
}
