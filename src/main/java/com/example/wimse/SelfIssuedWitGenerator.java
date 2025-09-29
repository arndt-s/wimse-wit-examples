package com.example.wimse;

import java.security.KeyPair;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelfIssuedWitGenerator {
    private final KeyPair keyPair;
    private final String workloadId;
    
    public SelfIssuedWitGenerator(String workloadId) {
        this.workloadId = workloadId;
        try {
            this.keyPair = CryptoUtils.loadKeyPair(workloadId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load keypair for " + workloadId, e);
        }
    }
    
    public String generateWit() {
        long now = Instant.now().getEpochSecond();
        long expiry = now + 3600; // 1 hour
        
        // Create WIT header
        Map<String, Object> header = Map.of(
            "alg", "ES256",
            "typ", "wit+jwt"
        );
        
        // Create WIT payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", workloadId);
        payload.put("iat", now);
        payload.put("exp", expiry);
        payload.put("jti", "wit-" + UUID.randomUUID().toString());
        
        // Add confirmation claim with public key
        Map<String, Object> jwk = CryptoUtils.createJwkFromPublicKey(keyPair.getPublic());
        payload.put("cnf", Map.of("jwk", jwk));
        
        return CryptoUtils.createJwt(header, payload, keyPair.getPrivate());
    }
    
    public KeyPair getKeyPair() {
        return keyPair;
    }
}