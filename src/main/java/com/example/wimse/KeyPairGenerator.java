package com.example.wimse;

import java.security.KeyPair;

public class KeyPairGenerator {
    
    public static void generateAndSaveKeys(String workloadId) {
        try {
            KeyPair keyPair = CryptoUtils.generateECKeyPair();
            CryptoUtils.saveKeyPair(keyPair, workloadId);
            System.out.println("Generated keys for " + workloadId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate keys for " + workloadId, e);
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: KeyPairGenerator <workload-id>");
            System.exit(1);
        }
        
        generateAndSaveKeys(args[0]);
    }
}