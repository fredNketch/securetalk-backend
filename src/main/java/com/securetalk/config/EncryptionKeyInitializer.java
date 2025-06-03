package com.securetalk.config;

import com.securetalk.model.User;
import com.securetalk.model.UserKey;
import com.securetalk.repository.UserKeyRepository;
import com.securetalk.repository.UserRepository;
import com.securetalk.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Composant qui s'exécute au démarrage de l'application pour initialiser les clés de chiffrement
 * pour tous les utilisateurs qui n'en ont pas encore.
 */
@Component
public class EncryptionKeyInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionKeyInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserKeyRepository userKeyRepository;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Override
    public void run(String... args) {
        logger.info("Initialisation des clés de chiffrement pour les utilisateurs...");
        
        // Récupérer tous les utilisateurs
        List<User> users = userRepository.findAll();
        logger.info("Nombre d'utilisateurs trouvés : {}", users.size());
        
        for (User user : users) {
            // Vérifier si l'utilisateur a déjà une clé
            if (userKeyRepository.findByUserId(user.getId()).isEmpty()) {
                logger.info("Génération d'une clé de chiffrement pour l'utilisateur {} (ID: {})", 
                           user.getUsername(), user.getId());
                
                // Générer une nouvelle clé
                String newKey = encryptionUtil.generateKey();
                
                // Créer et sauvegarder l'entité UserKey
                UserKey userKey = new UserKey(user, newKey);
                userKeyRepository.save(userKey);
                
                logger.info("Clé de chiffrement générée avec succès pour l'utilisateur {}", user.getUsername());
            } else {
                logger.debug("L'utilisateur {} possède déjà une clé de chiffrement", user.getUsername());
            }
        }
        
        logger.info("Initialisation des clés de chiffrement terminée");
    }
}
