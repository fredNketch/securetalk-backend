package com.securetalk.config;

import com.securetalk.model.ERole;
import com.securetalk.model.Role;
import com.securetalk.model.User;
import com.securetalk.repository.RoleRepository;
import com.securetalk.repository.UserRepository;
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
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Initialiser les rôles si nécessaire
        initRoles();
        
        // Créer des utilisateurs de test si nécessaire
        initUsers();
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
}
