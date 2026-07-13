#!/bin/bash
set -euo pipefail

# The default POSTGRES_DB (postgres) is created automatically by the image.
# Create one database per microservice so each has its own isolated schema.
for DB in orderdb paymentdb frauddb notificationdb; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        SELECT 'CREATE DATABASE $DB' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB')\gexec
EOSQL
    echo "Ensured database $DB exists"
done
