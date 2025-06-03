package com.securetalk.util;

import com.securetalk.model.User;
import com.securetalk.model.UserKey;
import com.securetalk.repository.UserKeyRepository;
import com.securetalk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class EncryptionUtil {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    
    @Autowired
    private UserKeyRepository userKeyRepository;
    
    @Autowired
    private UserRepository userRepository;

    /**
     * Génère une clé AES-256 pour le chiffrement
     * @return Clé encodée en Base64
     */
    public String generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, SecureRandom.getInstanceStrong());
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erreur lors de la génération de la clé", e);
        }
    }

    /**
     * Génère un vecteur d'initialisation (IV) pour le chiffrement AES-GCM
     * @return IV encodé en Base64
     */
    public String generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return Base64.getEncoder().encodeToString(iv);
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
            byte[] keyBytes = Base64.getDecoder().decode(keyStr);
            byte[] ivBytes = Base64.getDecoder().decode(ivStr);
            
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBytes);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
            
            byte[] cipherText = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception e) {
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
            // Logs pour déboguer
            System.out.println("Tentative de déchiffrement avec les paramètres suivants:");
            System.out.println("- Longueur du message chiffré: " + (encryptedMessage != null ? encryptedMessage.length() : "null"));
            System.out.println("- Longueur de la clé: " + (keyStr != null ? keyStr.length() : "null"));
            System.out.println("- Longueur de l'IV: " + (ivStr != null ? ivStr.length() : "null"));
            
            // Vérification des entrées
            if (encryptedMessage == null || keyStr == null || ivStr == null) {
                throw new IllegalArgumentException("Les paramètres de déchiffrement ne peuvent pas être null");
            }
            
            byte[] keyBytes = Base64.getDecoder().decode(keyStr);
            byte[] ivBytes = Base64.getDecoder().decode(ivStr);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage);
            
            System.out.println("- Taille des keyBytes: " + keyBytes.length);
            System.out.println("- Taille des ivBytes: " + ivBytes.length);
            System.out.println("- Taille des encryptedBytes: " + encryptedBytes.length);
            
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBytes);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String result = new String(decryptedBytes, StandardCharsets.UTF_8);
            System.out.println("Déchiffrement réussi, longueur du résultat: " + result.length());
            return result;
        } catch (Exception e) {
            System.err.println("Erreur lors du déchiffrement: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            // Pour les messages existants qui ne peuvent pas être déchiffrés, retourner un message par défaut
            if (e instanceof javax.crypto.AEADBadTagException || 
                e instanceof javax.crypto.IllegalBlockSizeException) {
                System.out.println("Erreur de déchiffrement détectée, retour d'un message par défaut");
                return "[Message chiffré avec une ancienne clé]"; 
            }
            throw new RuntimeException("Erreur lors du déchiffrement", e);
        }
    }
    
    /**
     * Méthode pour chiffrer un message en utilisant la clé de l'utilisateur destinataire
     * 
     * @param content Contenu du message à chiffrer
     * @param userId ID de l'utilisateur destinataire
     * @return Tableau contenant le message chiffré et le vecteur d'initialisation
     */
    public String[] encryptMessage(String content, Long userId) {
        // Récupérer la clé de l'utilisateur ou en générer une nouvelle si elle n'existe pas
        String userKey = getUserKey(userId);
        String iv = generateIv();
        String encryptedContent = encrypt(content, userKey, iv);
        return new String[] { encryptedContent, iv };
    }
    
    /**
     * Méthode pour déchiffrer un message
     * 
     * @param encryptedContent Contenu chiffré du message
     * @param iv Vecteur d'initialisation utilisé pour le chiffrement
     * @param userId ID de l'utilisateur qui souhaite lire le message
     * @return Contenu déchiffré du message
     */
    public String decryptMessage(String encryptedContent, String iv, Long userId) {
        // Récupérer la clé de l'utilisateur depuis la base de données
        Optional<UserKey> userKeyOpt = userKeyRepository.findByUserId(userId);
        
        if (userKeyOpt.isEmpty()) {
            // Si la clé n'existe pas, nous ne pouvons pas déchiffrer le message
            System.out.println("Clé de déchiffrement non trouvée pour l'utilisateur " + userId);
            return "[Message non déchiffrable]"; // Retourner un message par défaut au lieu de lancer une exception
        }
        
        try {
            // Déchiffrer le message avec la clé récupérée
            return decrypt(encryptedContent, userKeyOpt.get().getEncryptionKey(), iv);
        } catch (RuntimeException e) {
            // Si une erreur se produit lors du déchiffrement, retourner un message par défaut
            System.err.println("Erreur lors du déchiffrement d'un message pour l'utilisateur " + userId + ": " + e.getMessage());
            return "[Message chiffré avec une ancienne clé]"; 
        }
    }
    
    /**
     * Récupère la clé de chiffrement d'un utilisateur ou en génère une nouvelle si elle n'existe pas
     * @param userId ID de l'utilisateur
     * @return La clé de chiffrement
     */
    private String getUserKey(Long userId) {
        Optional<UserKey> userKeyOpt = userKeyRepository.findByUserId(userId);
        
        if (userKeyOpt.isPresent()) {
            return userKeyOpt.get().getEncryptionKey();
        } else {
            // Générer une nouvelle clé
            String newKey = generateKey();
            
            // Récupérer l'utilisateur
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));
            
            // Sauvegarder la clé
            UserKey userKey = new UserKey(user, newKey);
            userKeyRepository.save(userKey);
            
            return newKey;
        }
    }
}
