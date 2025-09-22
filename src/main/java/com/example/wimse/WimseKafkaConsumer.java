package com.example.wimse;

import io.jsonwebtoken.Claims;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WimseKafkaConsumer {
    private final KafkaConsumer<String, String> consumer;
    private final String wit;
    private final String clusterName;
    private final Set<String> processedJtis = ConcurrentHashMap.newKeySet();
    
    public WimseKafkaConsumer(String workloadId, String clusterName, List<String> topics) {
        this.clusterName = clusterName;
        
        // Generate own WIT at startup
        SelfIssuedWitGenerator witGen = new SelfIssuedWitGenerator(workloadId);
        this.wit = witGen.generateWit();
        
        // Initialize Kafka consumer
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "wimse-demo-consumer");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "earliest");
        
        this.consumer = new KafkaConsumer<>(props);
        consumer.subscribe(topics);
        
        System.out.println("Consumer initialized with WIT for: " + workloadId);
    }
    
    public void startConsuming() {
        System.out.println("Starting to consume messages...");
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            
            for (ConsumerRecord<String, String> record : records) {
                if (validateWimseMessage(record)) {
                    System.out.printf("✓ Validated message from topic %s: %s%n", 
                                    record.topic(), record.value());
                } else {
                    System.out.printf("✗ WIMSE validation failed for message: %s%n", 
                                    record.value());
                }
            }
        }
    }
    
    private boolean validateWimseMessage(ConsumerRecord<String, String> record) {
        try {
            // Extract WIMSE headers
            String witToken = getHeaderValue(record, "wimse-identity-token");
            String wptToken = getHeaderValue(record, "wimse-proof-token");
            
            if (witToken == null || wptToken == null) {
                System.out.println("Missing WIMSE headers");
                return false;
            }
            
            // Parse WIT to extract public key
            PublicKey senderPublicKey = CryptoUtils.extractPublicKeyFromWit(witToken);
            if (senderPublicKey == null) {
                System.out.println("Could not extract public key from WIT");
                return false;
            }
            
            // Validate WPT signature and claims
            Claims wptClaims = CryptoUtils.parseJwt(wptToken, senderPublicKey);
            return validateWptClaims(wptClaims, record.topic(), witToken);
            
        } catch (Exception e) {
            System.out.println("WIMSE validation error: " + e.getMessage());
            return false;
        }
    }
    
    private boolean validateWptClaims(Claims claims, String topic, String witToken) {
        // Check audience
        String expectedAudience = String.format("kafka://%s/%s", clusterName, topic);
        if (!expectedAudience.equals(claims.get("aud"))) {
            System.out.println("Audience mismatch: expected " + expectedAudience + 
                             ", got " + claims.get("aud"));
            return false;
        }
        
        // Check expiry
        Date expDate = claims.getExpiration();
        if (expDate.before(new Date())) {
            System.out.println("WPT expired");
            return false;
        }
        
        // Check WIT hash
        String expectedWitHash = CryptoUtils.sha256Base64(witToken);
        if (!expectedWitHash.equals(claims.get("wth"))) {
            System.out.println("WIT hash mismatch");
            return false;
        }
        
        // Replay protection
        String jti = claims.getId();
        if (processedJtis.contains(jti)) {
            System.out.println("Replay detected for JTI: " + jti);
            return false;
        }
        processedJtis.add(jti);
        
        return true;
    }
    
    private String getHeaderValue(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
    
    public void close() {
        consumer.close();
    }
}