package com.example.wimse;

import java.util.List;

public class WimseDemoRunner {
    
    public static void main(String[] args) throws InterruptedException {
        String clusterName = "demo-cluster";
        String producerWorkload = "wimse://demo.local/order-producer";
        String consumerWorkload = "wimse://demo.local/order-consumer";
        
        System.out.println("WIMSE Kafka Demo Starting...");
        
        // Generate keys for both workloads
        System.out.println("Generating keypairs...");
        KeyPairGenerator.generateAndSaveKeys(producerWorkload);
        KeyPairGenerator.generateAndSaveKeys(consumerWorkload);
        
        // Start consumer in separate thread
        Thread consumerThread = new Thread(() -> {
            try {
                WimseKafkaConsumer consumer = new WimseKafkaConsumer(
                    consumerWorkload, clusterName, List.of("orders"));
                consumer.startConsuming();
            } catch (Exception e) {
                System.err.println("Consumer error: " + e.getMessage());
                e.printStackTrace();
            }
        });
        consumerThread.setDaemon(true);
        consumerThread.start();
        
        // Give consumer time to start
        Thread.sleep(3000);
        
        // Start producer and send messages
        try {
            WimseKafkaProducer producer = new WimseKafkaProducer(
                producerWorkload, clusterName);
                
            System.out.println("Sending messages...");
            for (int i = 0; i < 5; i++) {
                producer.send("orders", 
                             "order-" + i, 
                             "Order details for order " + i);
                Thread.sleep(2000);
            }
            
            producer.close();
            System.out.println("Demo completed successfully");
            
        } catch (Exception e) {
            System.err.println("Producer error: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Keep running to see consumer output
        Thread.sleep(5000);
        System.exit(0);
    }
}