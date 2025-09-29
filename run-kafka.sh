#!/bin/bash
set -e

confluent local kafka start --plaintext-ports 33237
confluent local kafka topic create orders