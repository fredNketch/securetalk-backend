package com.securetalk.config;

import com.securetalk.model.ERole;
import com.securetalk.model.Role;
import com.securetalk.model.User;
import com.securetalk.model.UserKey;
import com.securetalk.repository.RoleRepository;
import com.securetalk.repository.UserKeyRepository;
import com.securetalk.repository.UserRepository;
import com.securetalk.util.EncryptionUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserKeyRepository userKeyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Override
    public void run(String... args) throws Exception {
        // Initialiser les rôles si nécessaire
        initRoles();
        
        // Créer des utilisateurs de test si nécessaire
        initUsers();
        
        // Générer les clés de chiffrement pour les utilisateurs existants qui n'en ont pas
        initUserKeys();
    }

    private void initRoles() {
        if (roleRepository.count() == 0) {
            Role userRole = new Role();
            userRole.setName(ERole.ROLE_USER);
            roleRepository.save(userRole);

            Role adminRole = new Role();
            adminRole.setName(ERole.ROLE_ADMIN);
            roleRepository.save(adminRole);
            
            System.out.println("Rôles initialisés avec succès");
        }
    }

    private void initUsers() {
        if (userRepository.count() == 0) {
            // Créer un utilisateur admin
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@securetalk.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setLastSeen(LocalDateTime.now());
            
            Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Role admin non trouvé"));
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Role user non trouvé"));
            
            Set<Role> adminRoles = new HashSet<>();
            adminRoles.add(adminRole);
            adminRoles.add(userRole);
            admin.setRoles(adminRoles);
            
            userRepository.save(admin);
            
            // Créer quelques utilisateurs de test
            createTestUser("alice", "alice@securetalk.com", "alice123");
            createTestUser("bob", "bob@securetalk.com", "bob123");
            createTestUser("charlie", "charlie@securetalk.com", "charlie123");
            createTestUser("diana", "diana@securetalk.com", "diana123");
            createTestUser("eve", "eve@securetalk.com", "eve123");
            
            System.out.println("Utilisateurs de test créés avec succès");
        }
    }
    
    private void createTestUser(String username, String email, String password) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setLastSeen(LocalDateTime.now());
        
        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role user non trouvé"));
        
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);
        
        userRepository.save(user);
    }

    /**
     * Génère automatiquement les clés de chiffrement pour tous les utilisateurs
     * qui n'en ont pas encore
     */
    private void initUserKeys() {
        System.out.println("Vérification et génération des clés de chiffrement utilisateur...");
        
        // Récupérer tous les utilisateurs
        Iterable<User> users = userRepository.findAll();
        int keysGenerated = 0;
        
        for (User user : users) {
            // Vérifier si l'utilisateur a déjà une clé
            if (userKeyRepository.findByUser(user).isEmpty()) {
                try {
                    // Générer une nouvelle clé AES-256
                    String encryptionKey = encryptionUtil.generateKey();
                    
                    // Créer et sauvegarder la clé utilisateur
                    UserKey userKey = new UserKey(user, encryptionKey);
                    userKeyRepository.save(userKey);
                    
                    keysGenerated++;
                    System.out.println("Clé de chiffrement générée pour l'utilisateur: " + user.getUsername());
                    
                } catch (Exception e) {
                    System.err.println("Erreur lors de la génération de la clé pour l'utilisateur " + 
                                     user.getUsername() + ": " + e.getMessage());
                }
            }
        }
        
        if (keysGenerated > 0) {
            System.out.println("Génération terminée: " + keysGenerated + " clés de chiffrement créées");
        } else {
            System.out.println("Toutes les clés de chiffrement utilisateur sont déjà présentes");
        }
    }
}
