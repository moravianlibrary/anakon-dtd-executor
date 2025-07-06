#!/bin/bash

DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$DIR/anakon-dtd-executor.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "No PID file found. Executor is not running?"
  exit 1
fi

PID=$(cat "$PID_FILE")
if kill -0 $PID 2>/dev/null; then
  echo "Stopping executor with PID $PID"
  kill $PID
  rm -f "$PID_FILE"
else
  echo "No process with PID $PID. Cleaning up."
  rm -f "$PID_FILE"
fi
