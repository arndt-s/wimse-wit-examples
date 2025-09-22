package com.example.wimse;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.security.MessageDigest;
import java.math.BigInteger;

public class CryptoUtils {
    
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    
    public static KeyPair generateECKeyPair() throws Exception {
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("EC", "BC");
        keyGen.initialize(256);
        return keyGen.generateKeyPair();
    }
    
    public static void saveKeyPair(KeyPair keyPair, String workloadId) throws Exception {
        String sanitizedId = workloadId.replaceAll("[^a-zA-Z0-9-]", "_");
        
        // Save private key
        try (PemWriter pemWriter = new PemWriter(new FileWriter(sanitizedId + "_private.pem"))) {
            pemWriter.writeObject(new PemObject("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        }
        
        // Save public key
        try (PemWriter pemWriter = new PemWriter(new FileWriter(sanitizedId + "_public.pem"))) {
            pemWriter.writeObject(new PemObject("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        }
    }
    
    public static KeyPair loadKeyPair(String workloadId) throws Exception {
        String sanitizedId = workloadId.replaceAll("[^a-zA-Z0-9-]", "_");
        
        // Load private key
        PrivateKey privateKey;
        try (PemReader pemReader = new PemReader(new FileReader(sanitizedId + "_private.pem"))) {
            PemObject pemObject = pemReader.readPemObject();
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pemObject.getContent());
            KeyFactory kf = KeyFactory.getInstance("EC");
            privateKey = kf.generatePrivate(spec);
        }
        
        // Load public key
        PublicKey publicKey;
        try (PemReader pemReader = new PemReader(new FileReader(sanitizedId + "_public.pem"))) {
            PemObject pemObject = pemReader.readPemObject();
            X509EncodedKeySpec spec = new X509EncodedKeySpec(pemObject.getContent());
            KeyFactory kf = KeyFactory.getInstance("EC");
            publicKey = kf.generatePublic(spec);
        }
        
        return new KeyPair(publicKey, privateKey);
    }
    
    public static String createJwt(Map<String, Object> header, Map<String, Object> payload, PrivateKey privateKey) {
        return Jwts.builder()
                .setHeaderParams(header)
                .setClaims(payload)
                .signWith(privateKey, SignatureAlgorithm.ES256)
                .compact();
    }
    
    public static Claims parseJwt(String token, PublicKey publicKey) {
        return Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    public static Claims parseJwtWithoutValidation(String token) {
        String[] chunks = token.split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(chunks[1]));
        
        // Simple JSON parsing - in production use proper JSON library
        // This is a demo shortcut
        return Jwts.claims();
    }
    
    public static Map<String, Object> createJwkFromPublicKey(PublicKey publicKey) {
        if (publicKey instanceof ECPublicKey) {
            ECPublicKey ecKey = (ECPublicKey) publicKey;
            
            // Get the coordinates
            byte[] xBytes = ecKey.getW().getAffineX().toByteArray();
            byte[] yBytes = ecKey.getW().getAffineY().toByteArray();
            
            // Ensure proper length (32 bytes for P-256)
            xBytes = padOrTrimTo32Bytes(xBytes);
            yBytes = padOrTrimTo32Bytes(yBytes);
            
            return Map.of(
                "kty", "EC",
                "crv", "P-256",
                "alg", "ES256",
                "x", Base64.getUrlEncoder().withoutPadding().encodeToString(xBytes),
                "y", Base64.getUrlEncoder().withoutPadding().encodeToString(yBytes)
            );
        }
        throw new IllegalArgumentException("Unsupported key type");
    }
    
    private static byte[] padOrTrimTo32Bytes(byte[] input) {
        if (input.length == 32) {
            return input;
        } else if (input.length > 32) {
            // Trim leading zeros if needed
            byte[] trimmed = new byte[32];
            System.arraycopy(input, input.length - 32, trimmed, 0, 32);
            return trimmed;
        } else {
            // Pad with leading zeros
            byte[] padded = new byte[32];
            System.arraycopy(input, 0, padded, 32 - input.length, input.length);
            return padded;
        }
    }
    
    public static String sha256Base64(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
    
    public static PublicKey extractPublicKeyFromWit(String witToken) {
        try {
            // Parse JWT header and payload without validation
            String[] parts = witToken.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            // Decode payload
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payloadJson = new String(decoder.decode(parts[1]));
            
            // Parse JSON to extract JWK
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> cnf = (Map<String, Object>) payload.get("cnf");
            if (cnf == null) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> jwk = (Map<String, Object>) cnf.get("jwk");
            if (jwk == null) {
                return null;
            }
            
            return jwkToPublicKey(jwk);
            
        } catch (Exception e) {
            System.err.println("Failed to extract public key from WIT: " + e.getMessage());
            return null;
        }
    }
    
    private static PublicKey jwkToPublicKey(Map<String, Object> jwk) throws Exception {
        if (!"EC".equals(jwk.get("kty")) || !"P-256".equals(jwk.get("crv"))) {
            throw new IllegalArgumentException("Only EC P-256 keys are supported");
        }
        
        String xStr = (String) jwk.get("x");
        String yStr = (String) jwk.get("y");
        
        if (xStr == null || yStr == null) {
            throw new IllegalArgumentException("Missing x or y coordinates in JWK");
        }
        
        Base64.Decoder decoder = Base64.getUrlDecoder();
        byte[] x = decoder.decode(xStr);
        byte[] y = decoder.decode(yStr);
        
        // Create the uncompressed point format (0x04 prefix + x + y)
        byte[] encoded = new byte[1 + x.length + y.length];
        encoded[0] = 0x04;
        System.arraycopy(x, 0, encoded, 1, x.length);
        System.arraycopy(y, 0, encoded, 1 + x.length, y.length);
        
        // Create EC public key
        KeyFactory kf = KeyFactory.getInstance("EC");
        java.security.spec.ECPoint point = new java.security.spec.ECPoint(
            new BigInteger(1, x), 
            new BigInteger(1, y)
        );
        
        // Use the NIST P-256 curve parameters
        java.security.spec.ECParameterSpec params = getP256ParameterSpec();
        java.security.spec.ECPublicKeySpec spec = new java.security.spec.ECPublicKeySpec(point, params);
        
        return kf.generatePublic(spec);
    }
    
    private static java.security.spec.ECParameterSpec getP256ParameterSpec() {
        // NIST P-256 curve parameters
        BigInteger p = new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16);
        BigInteger a = new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16);
        BigInteger b = new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16);
        BigInteger gx = new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16);
        BigInteger gy = new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16);
        BigInteger n = new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16);
        
        java.security.spec.EllipticCurve curve = new java.security.spec.EllipticCurve(
            new java.security.spec.ECFieldFp(p), a, b
        );
        
        java.security.spec.ECPoint g = new java.security.spec.ECPoint(gx, gy);
        
        return new java.security.spec.ECParameterSpec(curve, g, n, 1);
    }
}