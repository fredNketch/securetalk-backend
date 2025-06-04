package com.securetalk.util;

import com.securetalk.model.Message;
import com.securetalk.model.User;
import com.securetalk.model.UserKey;
import com.securetalk.repository.UserKeyRepository;
import com.securetalk.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class EncryptionUtil {
    private static final Logger logger = LoggerFactory.getLogger(EncryptionUtil.class);
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    @Value("${security.encryption.master-key:#{null}}")
    private String masterKeyEnv;
    
    // Sel pour le chiffrement des clés utilisateur (devrait être stocké dans un coffre-fort ou une variable d'environnement)
    @Value("${security.encryption.key-salt:securetalksalt123456}")
    private String keySalt;
    
    @Autowired
    private UserKeyRepository userKeyRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    // Cache des clés pour éviter les accès répétés à la base de données
    private final Map<Long, CachedKey> keyCache = new HashMap<>();
    
    // Classe pour mettre en cache les clés avec leur date d'expiration
    private static class CachedKey {
        private final String key;
        private final LocalDateTime expiry;
        
        public CachedKey(String key, LocalDateTime expiry) {
            this.key = key;
            this.expiry = expiry;
        }
        
        public String getKey() {
            return key;
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiry);
        }
    }

    /**
     * Génère une clé AES-256 pour le chiffrement
     * @return Clé encodée en Base64
     */
    public String generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, SecureRandom.getInstanceStrong());
            SecretKey key = keyGen.generateKey();
            String keyBase64 = Base64.getEncoder().encodeToString(key.getEncoded());
            logger.debug("Nouvelle clé de chiffrement générée");
            return keyBase64;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Erreur lors de la génération de la clé AES: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la génération de la clé", e);
        }
    }
    
    /**
     * Sécurise une clé utilisateur avec la clé maître
     * @param key Clé à sécuriser en Base64
     * @return Clé sécurisée en Base64
     */
    public String secureKey(String key) {
        try {
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("La clé ne peut pas être nulle ou vide");
            }
            
            // Utiliser une clé maître stockée dans une variable d'environnement ou la configuration
            String masterKey = getMasterKey();
            String salt = keySalt;
            
            // Générer un hash HMAC pour protéger la clé
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKey secretKey = new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            hmac.init(secretKey);
            
            // Combinaison du sel et de la clé pour l'HMAC
            byte[] hmacResult = hmac.doFinal((salt + key).getBytes(StandardCharsets.UTF_8));
            
            // Combiner le résultat HMAC avec la clé originale pour la protection
            byte[] keyBytes = Base64.getDecoder().decode(key);
            byte[] combined = new byte[hmacResult.length + keyBytes.length];
            System.arraycopy(hmacResult, 0, combined, 0, hmacResult.length);
            System.arraycopy(keyBytes, 0, combined, hmacResult.length, keyBytes.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Erreur lors de la sécurisation de la clé: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la sécurisation de la clé", e);
        }
    }
    
    /**
     * Récupère la clé maître depuis l'environnement ou génère une clé par défaut
     * (La clé par défaut devrait être stockée dans un service de gestion de clés sécurisé en production)
     */
    private String getMasterKey() {
        if (masterKeyEnv != null && !masterKeyEnv.isEmpty()) {
            return masterKeyEnv;
        }
        
        // Clé par défaut - À NE PAS UTILISER EN PRODUCTION
        logger.warn("Utilisation d'une clé maître par défaut - NON RECOMMANDÉ EN PRODUCTION");
        return "secureTalk_DefaultMasterKey2025!";
    }
    
    /**
     * Génère un vecteur d'initialisation (IV) pour le chiffrement AES-GCM
     * @return IV encodé en Base64
     */
    public String generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        try {
            SecureRandom.getInstanceStrong().nextBytes(iv);
            logger.debug("Génération d'un nouveau IV pour AES-GCM");
            return Base64.getEncoder().encodeToString(iv);
        } catch (NoSuchAlgorithmException e) {
            // Fallback si getInstanceStrong échoue
            logger.warn("Utilisation de SecureRandom standard suite à une erreur: {}", e.getMessage());
            new SecureRandom().nextBytes(iv);
            return Base64.getEncoder().encodeToString(iv);
        }
    }

    /**
     * Chiffre un message avec AES-GCM
     * @param message Message à chiffrer
     * @param keyStr Clé de chiffrement en Base64
     * @param ivStr Vecteur d'initialisation en Base64
     * @return Message chiffré encodé en Base64
     */
    public String encrypt(String message, String keyStr, String ivStr) {
        try {
            // Validation des entrées
            if (message == null || keyStr == null || ivStr == null) {
                throw new IllegalArgumentException("Les paramètres de chiffrement ne peuvent pas être null");
            }
            
            if (message.isEmpty()) {
                logger.debug("Message vide à chiffrer, retour d'une chaîne vide");
                return ""; // Message vide = résultat vide
            }
            
            // Vérifier que la clé et l'IV sont au bon format Base64
            if (!isValidBase64(keyStr) || !isValidBase64(ivStr)) {
                throw new IllegalArgumentException("La clé ou l'IV ne sont pas en format Base64 valide");
            }
            
            byte[] keyBytes = Base64.getDecoder().decode(keyStr);
            byte[] ivBytes = Base64.getDecoder().decode(ivStr);
            
            // Vérifier les longueurs
            if (keyBytes.length != 32) { // AES-256 = 32 bytes
                throw new IllegalArgumentException("La clé doit faire 256 bits (32 bytes)");
            }
            
            if (ivBytes.length != GCM_IV_LENGTH) {
                throw new IllegalArgumentException("L'IV doit faire " + GCM_IV_LENGTH + " bytes");
            }
            
            // Chiffrement
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBytes);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
            
            byte[] cipherText = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception e) {
            logger.error("Erreur lors du chiffrement: {}", e.getMessage());
            throw new RuntimeException("Erreur lors du chiffrement", e);
        }
    }

    /**
     * Déchiffre un message avec AES-GCM
     * @param encryptedMessage Message chiffré encodé en Base64
     * @param keyStr Clé de chiffrement en Base64
     * @param ivStr Vecteur d'initialisation en Base64
     * @return Message déchiffré
     */
    public String decrypt(String encryptedMessage, String keyStr, String ivStr) {
        try {
            // Validation des entrées
            if (encryptedMessage == null || keyStr == null || ivStr == null) {
                logger.warn("Tentative de déchiffrement avec des paramètres null");
                throw new IllegalArgumentException("Les paramètres de déchiffrement ne peuvent pas être null");
            }
            
            if (encryptedMessage.isEmpty()) {
                logger.debug("Message chiffré vide, retour d'une chaîne vide");
                return ""; // Message vide = résultat vide
            }
            
            if (!isValidBase64(encryptedMessage) || !isValidBase64(keyStr) || !isValidBase64(ivStr)) {
                logger.warn("Format Base64 invalide pour le déchiffrement");
                throw new IllegalArgumentException("Les données ne sont pas en format Base64 valide");
            }
            
            byte[] keyBytes = Base64.getDecoder().decode(keyStr);
            byte[] ivBytes = Base64.getDecoder().decode(ivStr);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage);
            
            // Vérification des longueurs
            if (keyBytes.length != 32) {
                logger.warn("Longueur de clé invalide: {} bytes au lieu de 32", keyBytes.length);
                throw new IllegalArgumentException("La clé doit faire 256 bits (32 bytes)");
            }
            
            if (ivBytes.length != GCM_IV_LENGTH) {
                logger.warn("Longueur d'IV invalide: {} bytes au lieu de {}", ivBytes.length, GCM_IV_LENGTH);
                throw new IllegalArgumentException("L'IV doit faire " + GCM_IV_LENGTH + " bytes");
            }
            
            // Déchiffrement
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBytes);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String result = new String(decryptedBytes, StandardCharsets.UTF_8);
            logger.debug("Déchiffrement réussi, longueur du résultat: {} caractères", result.length());
            return result;
        } catch (Exception e) {
            logger.error("Erreur lors du déchiffrement: {} - {}", e.getClass().getName(), e.getMessage());
            
            // Pour les messages existants qui ne peuvent pas être déchiffrés, catégoriser l'erreur
            if (e instanceof javax.crypto.AEADBadTagException) {
                logger.warn("Erreur d'authenticité détectée lors du déchiffrement (AEADBadTagException)");
                return "[Message altéré ou clé incorrecte]";
            } else if (e instanceof javax.crypto.IllegalBlockSizeException) {
                logger.warn("Format de données incorrect lors du déchiffrement (IllegalBlockSizeException)");
                return "[Format de message non valide]";
            }
            throw new RuntimeException("Erreur lors du déchiffrement", e);
        }
    }
    
    /**
     * Vérifie si une chaîne est en format Base64 valide
     * @param str Chaîne à vérifier
     * @return true si la chaîne est un Base64 valide
     */
    private boolean isValidBase64(String str) {
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
/**
     * Méthode pour chiffrer un message pour les deux parties (expéditeur et destinataire)
     * avec des clés et IV différents pour chaque utilisateur
     * 
     * @param content Contenu du message à chiffrer
     * @param senderId ID de l'utilisateur expéditeur
     * @param recipientId ID de l'utilisateur destinataire
     * @return Tableau contenant [encryptedForRecipient, ivRecipient, encryptedForSender, ivSender]
     */
    public String[] encryptMessageForBoth(String content, Long senderId, Long recipientId) {
        try {
            // Récupérer les clés des deux utilisateurs
            String recipientKey = getCachedUserKey(recipientId);
            String senderKey = getCachedUserKey(senderId);
            
            if (recipientKey == null) {
                throw new RuntimeException("Clé de chiffrement non trouvée pour le destinataire ID: " + recipientId);
            }
            
            if (senderKey == null) {
                throw new RuntimeException("Clé de chiffrement non trouvée pour l'expéditeur ID: " + senderId);
            }
            
            // Générer des IV différents pour chaque chiffrement
            String ivRecipient = generateIv();
            String ivSender = generateIv();
            
            // Chiffrer le message avec la clé du destinataire
            String encryptedForRecipient = encrypt(content, recipientKey, ivRecipient);
            
            // Chiffrer le message avec la clé de l'expéditeur
            String encryptedForSender = encrypt(content, senderKey, ivSender);
            
            logger.debug("Message chiffré pour l'expéditeur {} et le destinataire {}", senderId, recipientId);
            
            return new String[] { 
                encryptedForRecipient, 
                ivRecipient, 
                encryptedForSender, 
                ivSender 
            };
            
        } catch (Exception e) {
            logger.error("Erreur lors du double chiffrement du message pour l'expéditeur {} et le destinataire {}: {}", 
                        senderId, recipientId, e.getMessage());
            throw new RuntimeException("Erreur lors du chiffrement du message", e);
        }
    }
    
/**
     * Méthode pour déchiffrer un message en utilisant automatiquement la bonne clé 
     * selon l'utilisateur qui fait la demande
     * 
     * @param message L'objet Message contenant les données chiffrées
     * @param userId ID de l'utilisateur qui souhaite lire le message
     * @return Contenu déchiffré du message
     */
    public String decryptMessageForUser(Message message, Long userId) {
        try {
            // Vérifier que l'utilisateur est autorisé à lire ce message
            if (!message.getSender().getId().equals(userId) && !message.getRecipient().getId().equals(userId)) {
                throw new SecurityException("Non autorisé à lire ce message");
            }
            
            // Récupérer le contenu chiffré et l'IV appropriés selon l'utilisateur
            String encryptedContent = message.getEncryptedContentForUser(userId);
            String iv = message.getIvForUser(userId);
            
            // Récupérer la clé de l'utilisateur
            String userKey = getCachedUserKey(userId);
            
            if (userKey == null) {
                logger.warn("Clé de déchiffrement non trouvée pour l'utilisateur {}", userId);
                return "[Message non déchiffrable - Clé utilisateur manquante]";
            }
            
            // Déchiffrer le message
            return decrypt(encryptedContent, userKey, iv);
            
        } catch (SecurityException e) {
            logger.warn("Tentative d'accès non autorisé au message {} par l'utilisateur {}", 
                       message.getId(), userId);
            throw e;
        } catch (Exception e) {
            logger.error("Erreur lors du déchiffrement du message {} pour l'utilisateur {}: {}", 
                        message.getId(), userId, e.getMessage());
            
            // Différencier les types d'erreurs pour aider au diagnostic
            if (e.getCause() instanceof javax.crypto.AEADBadTagException) {
                return "[Message altéré ou clé incorrecte]";
            } else if (e.getCause() instanceof javax.crypto.IllegalBlockSizeException) {
                return "[Format de message non valide]";
            }
            return "[Message non déchiffrable]";
        }
    }
    
    /**
     * Récupère la clé de chiffrement d'un utilisateur depuis le cache ou la base de données
     * @param userId ID de l'utilisateur
     * @return La clé de chiffrement ou null si non trouvée
     */
    private String getCachedUserKey(Long userId) {
        // Vérifier le cache d'abord
        CachedKey cachedKey = keyCache.get(userId);
        if (cachedKey != null && !cachedKey.isExpired()) {
            logger.debug("Clé trouvée dans le cache pour l'utilisateur {}", userId);
            return cachedKey.getKey();
        }
        
        // Si pas dans le cache ou expirée, chercher dans la base de données
        Optional<UserKey> userKeyOpt = userKeyRepository.findByUserId(userId);
        
        if (userKeyOpt.isPresent()) {
            UserKey userKey = userKeyOpt.get();
            // Vérifier si la clé est active
            if (userKey.getIsActive()) {
                String key = userKey.getEncryptionKey();
                // Mettre en cache pour 5 minutes
                keyCache.put(userId, new CachedKey(key, LocalDateTime.now().plusMinutes(5)));
                logger.debug("Clé récupérée de la base de données pour l'utilisateur {} et mise en cache", userId);
                return key;
            } else {
                logger.warn("La clé de l'utilisateur {} est désactivée", userId);
                return null;
            }
        } else {
            // Générer une nouvelle clé
            logger.info("Génération d'une nouvelle clé pour l'utilisateur {}", userId);
            String newKey = generateKey();
            
            try {
                // Récupérer l'utilisateur
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));
                
                // Sauvegarder la clé avec date d'expiration
                UserKey userKey = new UserKey(user, newKey);
                userKeyRepository.save(userKey);
                
                // Mettre en cache
                keyCache.put(userId, new CachedKey(newKey, LocalDateTime.now().plusMinutes(5)));
                logger.info("Nouvelle clé générée et sauvegardée pour l'utilisateur {}", userId);
                
                return newKey;
            } catch (Exception e) {
                logger.error("Erreur lors de la génération d'une nouvelle clé pour l'utilisateur {}: {}", userId, e.getMessage());
                return null;
            }
        }
    }
}
