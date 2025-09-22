# syntax=docker/dockerfile:1
FROM mcr.microsoft.com/devcontainers/java:22

# Install additional tools if needed
# RUN apt-get update && apt-get install -y wget tar

# Set working directory
WORKDIR /workspaces/wimse-wit-examples

# Default shell
SHELL ["/bin/bash", "-c"]

# Expose Kafka and ZooKeeper ports
EXPOSE 9092 2181
