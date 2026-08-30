#!/bin/bash
set -euo pipefail

KEYS_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$KEYS_DIR"

# Production root CA (addons-public + content-signature-prod)
openssl genrsa -out production-root.key 4096
openssl req -new -x509 -key production-root.key -sha384 -days 62025 \
  -out production-root.pem \
  -subj "/C=US/O=Custom Firefox Distribution/OU=Extension Signing Service/CN=custom-root-ca-production-amo"

# Production intermediate (signingca1 - post 2018)
openssl genrsa -out production-intermediate-2018.key 4096
openssl req -new -key production-intermediate-2018.key -sha384 \
  -out production-intermediate-2018.csr \
  -subj "/C=US/O=Custom Firefox Distribution/OU=Extension Signing Service/CN=signingca1.addons.example.org/emailAddress=signing@example.org"
openssl x509 -req -in production-intermediate-2018.csr -CA production-root.pem -CAkey production-root.key \
  -CAcreateserial -sha384 -days 31025 -out production-intermediate-2018.pem

# Production intermediate (pre-2018 legacy CN)
openssl genrsa -out production-intermediate-legacy.key 4096
openssl req -new -key production-intermediate-legacy.key -sha384 \
  -out production-intermediate-legacy.csr \
  -subj "/C=US/O=Custom Firefox Distribution/OU=Extension Signing Service/CN=production-signing-ca.addons.example.org/emailAddress=signing@example.org"
openssl x509 -req -in production-intermediate-legacy.csr -CA production-root.pem -CAkey production-root.key \
  -CAcreateserial -sha384 -days 31025 -out production-intermediate-legacy.pem

# Staging root CA
openssl genrsa -out staging-root.key 4096
openssl req -new -x509 -key staging-root.key -sha384 -days 10000 \
  -out staging-root.pem \
  -subj "/C=US/O=Custom Firefox Distribution/OU=Staging Signing Service/CN=custom-cas-root-ca-staging"

# Staging intermediate
openssl genrsa -out staging-intermediate.key 4096
openssl req -new -key staging-intermediate.key -sha384 \
  -out staging-intermediate.csr \
  -subj "/C=US/O=Custom Firefox Distribution/OU=Staging Signing Service/CN=custom-cas-intermediate-amo-ca-staging"
openssl x509 -req -in staging-intermediate.csr -CA staging-root.pem -CAkey staging-root.key \
  -CAcreateserial -sha384 -days 10000 -out staging-intermediate.pem

echo "Keys generated in $KEYS_DIR"
ls -la *.pem *.key 2>/dev/null | grep -v csr
