#!/bin/sh
set -e

# Vytvoření config.properties ze zadaných environment proměnných
cat > config.properties <<EOF
db.host=${APP_DB_HOST:-localhost}
db.port=${APP_DB_PORT:-5432}
db.database=${APP_DB_NAME:-anakon_db}
db.user=${APP_DB_USERNAME:-anakon_user}
db.password=${APP_DB_PASSWORD:-anakon_password}
EOF

echo "Generated config.properties:"
cat config.properties

# Spuštění JARu s configem
exec java -jar anakon-dtd-executor.jar -c config.properties
