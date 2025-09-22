# WIMSE Kafka Demo Implementation Guide

## Context and Overview

This is a proof-of-concept implementation demonstrating how to use **WIMSE (Workload Identity in Multi System Environments)** tokens with Apache Kafka. 

### What is WIMSE?

WIMSE is an emerging IETF standard for workload-to-workload authentication. It defines:

- **Workload Identity Token (WIT)**: A JWT that represents a workload's identity, containing a public key
- **Workload Proof Token (WPT)**: A short-lived JWT that proves possession of the private key corresponding to the WIT's public key

The standard is designed for HTTP services but this demo adapts it for Kafka messaging.

### Demo Architecture

This is a **simplified demonstration** that takes shortcuts for feasibility testing:
- **Self-signed credentials**: Generate keypairs locally (no real identity server)
- **Self-issued WITs**: Each workload creates its own WIT at startup
- **Per-message WPTs**: Producer generates a fresh WPT for every message
- **Kafka topic as audience**: The Kafka topic becomes the authentication target
- **Header-based transport**: WIMSE tokens are carried in Kafka message headers

### Key Adaptations for Kafka

1. **Audience Format**: `kafka://<cluster-name>/<topic-name>` (instead of HTTP URLs)
2. **Message Headers**: Use `wimse-identity-token` and `wimse-proof-token` headers
3. **No Message Hash**: Simplified by removing message payload integrity checks
4. **Topic-based Security**: Each topic acts as a separate authentication domain

## Implementation Structure

### Core Files

- **`CryptoUtils.java`**: Cryptographic utilities for EC key operations, JWT creation/parsing, JWK handling
- **`KeyPairGenerator.java`**: Utility for generating and saving workload keypairs  
- **`SelfIssuedWitGenerator.java`**: Creates self-issued WITs containing public keys
- **`WimseKafkaProducer.java`**: Kafka producer that attaches WIMSE tokens to messages
- **`WimseKafkaConsumer.java`**: Kafka consumer that validates WIMSE tokens
- **`WimseDemoRunner.java`**: Main demo application that orchestrates the end-to-end flow

## Prerequisites

### Dependencies

The project uses Maven with these key dependencies:

- **Apache Kafka Clients 3.5.0**: For Kafka producer/consumer functionality
- **JJWT 0.11.5**: For JWT creation, parsing and validation
- **BouncyCastle 1.70**: For elliptic curve cryptography operations
- **Jackson Databind**: For JSON parsing of JWT payloads

### Environment Setup

1. **Java**: Version 17 or higher
2. **Maven**: For building the project
3. **Kafka**: Running locally on `localhost:9092`

#### Setting up Kafka

```bash
# Download and extract Kafka
wget https://downloads.apache.org/kafka/2.13-3.5.0/kafka_2.13-3.5.0.tgz
tar -xzf kafka_2.13-3.5.0.tgz
cd kafka_2.13-3.5.0

# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# In another terminal, start Kafka
bin/kafka-server-start.sh config/server.properties

# Create the test topic
bin/kafka-topics.sh --create --topic orders --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

## Running the Demo

### Step 1: Build the Project

```bash
mvn clean compile
```

### Step 2: Run the Demo

```bash
mvn exec:java -Dexec.mainClass="com.example.wimse.WimseDemoRunner"
```

### Expected Output

```
WIMSE Kafka Demo Starting...
Generating keypairs...
Generated keys for wimse://demo.local/order-producer
Generated keys for wimse://demo.local/order-consumer
Producer initialized with WIT for: wimse://demo.local/order-producer
Consumer initialized with WIT for: wimse://demo.local/order-consumer
Starting to consume messages...
Sending messages...
Sent message to orders with WIMSE tokens
✓ Validated message from topic orders: Order details for order 0
Sent message to orders with WIMSE tokens
✓ Validated message from topic orders: Order details for order 1
...
Demo completed successfully
```

## How It Works

### Token Flow

1. **Startup**: Each workload (producer/consumer) generates an EC keypair and creates a self-issued WIT
2. **Message Send**: Producer creates a fresh WPT for each message, signs it with private key
3. **Transport**: Both WIT and WPT are attached as Kafka message headers
4. **Validation**: Consumer extracts public key from WIT, validates WPT signature and claims

### Security Features Demonstrated

- **Public Key Cryptography**: EC P-256 keys for signing/verification
- **Token Binding**: WPT includes hash of the WIT it's bound to
- **Audience Validation**: WPT audience must match Kafka topic
- **Expiry Checking**: Both WIT (1 hour) and WPT (5 minutes) have expiration
- **Replay Protection**: JTI (JWT ID) tracking prevents token reuse

### Validation Steps

The consumer performs these validation steps for each message:

1. Extract `wimse-identity-token` and `wimse-proof-token` headers
2. Parse WIT to extract the sender's public key from the `cnf.jwk` claim
3. Verify WPT signature using the extracted public key
4. Validate WPT audience matches `kafka://<cluster>/<topic>`
5. Check WPT expiration time
6. Verify WPT's `wth` claim matches SHA-256 hash of the WIT
7. Check JTI for replay protection

## Key Implementation Details

### Cryptographic Operations

- **EC Key Generation**: Uses BouncyCastle provider for P-256 curve
- **JWT Signing**: ES256 algorithm (ECDSA with SHA-256)
- **JWK Format**: Standard RFC 7517 JSON Web Key representation
- **Key Storage**: PEM format files (demo only - use HSM/vault in production)

### WIMSE Token Structure

**WIT (Workload Identity Token)**:
```json
{
  "alg": "ES256",
  "typ": "wit+jwt"
}
{
  "sub": "wimse://demo.local/order-producer", 
  "iat": 1695123456,
  "exp": 1695127056,
  "jti": "wit-12345678-1234-1234-1234-123456789012",
  "cnf": {
    "jwk": {
      "kty": "EC",
      "crv": "P-256", 
      "alg": "ES256",
      "x": "base64url-encoded-x-coordinate",
      "y": "base64url-encoded-y-coordinate"
    }
  }
}
```

**WPT (Workload Proof Token)**:
```json
{
  "alg": "ES256",
  "typ": "wpt+jwt"  
}
{
  "aud": "kafka://demo-cluster/orders",
  "exp": 1695123756,
  "jti": "wpt-87654321-4321-4321-4321-210987654321", 
  "wth": "base64url-encoded-sha256-hash-of-wit"
}
```

## Production Considerations

### Security Limitations (Demo Shortcuts)

⚠️ **This is a proof-of-concept with several shortcuts**:

1. **Self-signed tokens**: Production should use a proper identity server/PKI
2. **No certificate validation**: Demo doesn't validate certificate chains  
3. **Simplified JWK extraction**: Production needs robust JWT parsing
4. **Basic replay protection**: Use distributed cache (Redis) for production
5. **No key rotation**: Implement regular key rotation policies
6. **Local key storage**: Use HSM or secure key management service

### Production Roadmap

To make this production-ready, implement:

1. **Identity Server Integration**: Use OAuth/OIDC flows for WIT issuance
2. **Certificate-based WITs**: Replace self-signed with PKI-issued certificates
3. **Fine-grained Authorization**: Role/scope-based access control  
4. **Multi-cluster Support**: Trust domains and cross-cluster validation
5. **Performance Optimization**: Token caching, batching, async validation
6. **Monitoring & Observability**: Metrics, logging, audit trails
7. **Key Management**: Secure key storage, rotation, escrow

### Scaling Considerations

- **Token Caching**: Cache validated WITs to avoid repeated public key extraction
- **Async Validation**: Move validation to separate thread pool
- **Batch Processing**: Process multiple messages together for efficiency
- **Distributed Replay Cache**: Use Redis/Hazelcast for JTI tracking across instances

## Troubleshooting

### Common Issues

1. **Compilation Errors**: Ensure Java 17+ and all Maven dependencies are available
2. **Kafka Connection**: Verify Kafka is running on `localhost:9092`
3. **Topic Not Found**: Create the `orders` topic before running the demo
4. **Key File Permissions**: Check that generated `*.pem` files are readable
5. **JWT Parsing Errors**: Verify proper Base64 encoding and JSON structure

### Debug Tips

- Add `-X` flag to Maven for verbose dependency resolution
- Use `kafka-console-consumer.sh` to inspect message headers
- Enable debug logging for JWT validation errors
- Check generated key files exist and have proper format

## Extension Points

This demo provides a foundation for more advanced implementations:

### Message Integrity
Add message payload hashing to WPT claims for end-to-end integrity.

### Multi-Tenant Support  
Extend audience format to include tenant: `kafka://cluster/tenant/topic`

### Fine-grained Permissions
Add scope claims to control read/write permissions per topic.

### Key Rotation
Implement automatic key rotation with overlapping validity periods.

### Cross-Cluster Authentication
Support trust relationships between different Kafka clusters.

## Standards Compliance

This implementation adapts these specifications for Kafka:

- **RFC 7519**: JSON Web Token (JWT)
- **RFC 7517**: JSON Web Key (JWK) 
- **RFC 7515**: JSON Web Signature (JWS)
- **IETF WIMSE Draft**: Workload Identity in Multi System Environments

The adaptations maintain the core security properties while fitting Kafka's messaging model.

## License

This demonstration code is provided for educational and testing purposes. Refer to individual dependency licenses for production use.