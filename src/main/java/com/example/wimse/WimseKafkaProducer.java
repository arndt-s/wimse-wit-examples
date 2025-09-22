package com.example.wimse;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

public class WimseKafkaProducer {
    private final KafkaProducer<String, String> producer;
    private final String wit;
    private final KeyPair keyPair;
    private final String clusterName;
    
    public WimseKafkaProducer(String workloadId, String clusterName) {
        this.clusterName = clusterName;
        
        // Generate WIT at startup
        SelfIssuedWitGenerator witGen = new SelfIssuedWitGenerator(workloadId);
        this.wit = witGen.generateWit();
        this.keyPair = witGen.getKeyPair();
        
        // Initialize Kafka producer
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:33237");
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(props);
        
        System.out.println("Producer initialized with WIT for: " + workloadId);
    }
    
    public void send(String topic, String key, String value) {
        // Generate fresh WPT for each message
        String wpt = generateWpt(topic);
        
        // Create record with WIMSE headers
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        
        record.headers()
            .add("wimse-identity-token", wit.getBytes(UTF_8))
            .add("wimse-proof-token", wpt.getBytes(UTF_8));
        
        producer.send(record, (metadata, exception) -> {
            if (exception == null) {
                System.out.printf("Sent message to %s with WIMSE tokens%n", topic);
            } else {
                System.err.println("Failed to send message: " + exception.getMessage());
            }
        });
    }
    
    private String generateWpt(String topic) {
        long now = Instant.now().getEpochSecond();
        
        Map<String, Object> header = Map.of(
            "alg", "ES256",
            "typ", "wpt+jwt"
        );
        
        Map<String, Object> payload = Map.of(
            "aud", String.format("kafka://%s/%s", clusterName, topic),
            "exp", now + 300, // 5 minutes
            "jti", "wpt-" + UUID.randomUUID().toString(),
            "wth", CryptoUtils.sha256Base64(wit) // WIT hash
        );
        
        return CryptoUtils.createJwt(header, payload, keyPair.getPrivate());
    }
    
    public void close() {
        producer.close();
    }
}