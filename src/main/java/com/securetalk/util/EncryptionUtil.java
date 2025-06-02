package com.securetalk.util;

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

@Component
public class EncryptionUtil {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    
    // Simulation de stockage des clés pour chaque utilisateur
    private Map<Long, String> userKeys = new HashMap<>();

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
            byte[] keyBytes = Base64.getDecoder().decode(keyStr);
            byte[] ivBytes = Base64.getDecoder().decode(ivStr);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage);
            
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBytes);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
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
        // Dans un système réel, nous récupérerions la clé de l'utilisateur depuis une base de données sécurisée
        // Pour simplifier, nous générons une clé si elle n'existe pas déjà
        String userKey = userKeys.computeIfAbsent(userId, k -> generateKey());
        
        // Générer un nouveau vecteur d'initialisation pour chaque message
        String iv = generateIv();
        
        // Chiffrer le message
        String encryptedContent = encrypt(content, userKey, iv);
        
        // Retourner le message chiffré et le vecteur d'initialisation
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
        // Dans un système réel, nous récupérerions la clé de l'utilisateur depuis une base de données sécurisée
        // Pour simplifier, nous vérifions si la clé existe déjà dans notre map
        String userKey = userKeys.get(userId);
        if (userKey == null) {
            // Si la clé n'existe pas, nous ne pouvons pas déchiffrer le message
            throw new RuntimeException("Clé de déchiffrement non trouvée pour l'utilisateur " + userId);
        }
        
        // Déchiffrer le message
        return decrypt(encryptedContent, userKey, iv);
    }
}
