#!/bin/bash

DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/anakon-dtd-executor-1.8.0.jar"
CONFIG_FILE="$DIR/config.properties"
LOG="$DIR/anakon-dtd-executor.log"
PID_FILE="$DIR/anakon-dtd-executor.pid"

# Jestli už běží, tak nebudeme spouštět znovu
if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
  echo "Executor is already running with PID $(cat "$PID_FILE")"
  exit 1
fi

nohup nice -n 10 java -jar "$JAR" --config_file "$CONFIG_FILE" \
  >> "$LOG" 2>&1 &

echo $! > "$PID_FILE"
echo "Executor started with PID $!"
