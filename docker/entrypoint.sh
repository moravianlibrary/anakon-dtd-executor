#!/bin/sh
set -e

# ===== Generování config.properties =====
CONFIG_FILE=/app/config.properties
echo "Generating $CONFIG_FILE from environment variables..."

cat > "$CONFIG_FILE" <<EOL
# Database configuration
db.host=${APP_DB_HOST:-localhost}
db.port=${APP_DB_PORT:-5432}
db.database=${APP_DB_NAME:-anakon_db}
db.user=${APP_DB_USERNAME:-anakon_user}
db.password=${APP_DB_PASSWORD:-anakon_password}

# Dynamic paths
dynamic.config.file=${APP_DYNAMIC_CONFIG_FILE:-/path/to/dynamic/config/file.yaml}
processes.definition.dir=${APP_PROCESSES_DEFINITION_DIR:-/path/to/process/definitions/folder}
process.execution.dir=${APP_PROCESS_EXECUTION_DIR:-/path/to/jobs/data/folder}
EOL

echo "File $CONFIG_FILE created"
#echo "$CONFIG_FILE created:"
#cat "$CONFIG_FILE"

# ===== Spuštění aplikace =====
exec java -jar /app/anakon-dtd-executor.jar -c "$CONFIG_FILE"
