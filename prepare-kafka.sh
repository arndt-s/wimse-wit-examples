#!/bin/bash
set -e

# Download latest Kafka release
KAFKA_URL="https://downloads.apache.org/kafka/4.1.0/kafka_2.13-4.1.0.tgz"
KAFKA_DIR="kafka_2.13-4.1.0"

echo "Downloading Kafka version 4.1.0..."
wget -q --show-progress "$KAFKA_URL" -O kafka.tgz
tar -xzf kafka.tgz
rm kafka.tgz

echo "Starting ZooKeeper..."
nohup ./${KAFKA_DIR}/bin/zookeeper-server-start.sh ./${KAFKA_DIR}/config/zookeeper.properties > zookeeper.log 2>&1 &
sleep 5

echo "Starting Kafka server..."
nohup ./${KAFKA_DIR}/bin/kafka-server-start.sh ./${KAFKA_DIR}/config/server.properties > kafka.log 2>&1 &
echo "Kafka is starting. Check kafka.log and zookeeper.log for details."
