#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_LOG="/tmp/kendala-backend.log"
FRONTEND_LOG="/tmp/kendala-frontend.log"
AI_LOG="/tmp/kendala-ai.log"
PIDS=()

export AI_INTERNAL_API_KEY="${AI_INTERNAL_API_KEY:-kendala-local-ai-key-change-before-production}"
export MPLCONFIGDIR="${MPLCONFIGDIR:-/tmp/kendala-matplotlib}"
export XDG_CACHE_HOME="${XDG_CACHE_HOME:-/tmp/kendala-cache}"

cleanup() {
  if ((${#PIDS[@]})); then
    kill "${PIDS[@]}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

if [[ -x /opt/homebrew/opt/openjdk@21/bin/java ]]; then
  export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
  export PATH="$JAVA_HOME/bin:$PATH"
elif [[ -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java 21 is not installed. Install it before starting the demo."
  exit 1
fi

if command -v pg_isready >/dev/null 2>&1 && ! pg_isready -h 127.0.0.1 -p 5432 >/dev/null 2>&1; then
  echo "PostgreSQL is not available on port 5432. Start PostgreSQL and retry."
  exit 1
fi

if ! curl -fsS http://127.0.0.1:8001/health >/dev/null 2>&1; then
  if ! python3 -c "import fastapi, uvicorn, xgboost, shap" >/dev/null 2>&1; then
    echo "Python AI dependencies are missing. Run: python3 -m pip install -r ai-service/requirements.txt"
    exit 1
  fi
  (
    cd "$ROOT_DIR/ai-service"
    python3 -m uvicorn api.main:app --host 127.0.0.1 --port 8001
  ) >"$AI_LOG" 2>&1 &
  PIDS+=("$!")
fi

if ! curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  (
    cd "$ROOT_DIR/backend-java"
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
  ) >"$BACKEND_LOG" 2>&1 &
  PIDS+=("$!")
fi

if ! curl -fsS http://127.0.0.1:5173/login >/dev/null 2>&1; then
  (
    cd "$ROOT_DIR/frontend"
    npm run dev -- --host 127.0.0.1
  ) >"$FRONTEND_LOG" 2>&1 &
  PIDS+=("$!")
fi

for _ in {1..60}; do
  if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' \
    && curl -fsS http://127.0.0.1:5173/login >/dev/null \
    && curl -fsS http://127.0.0.1:8001/health >/dev/null; then
    break
  fi
  sleep 1
done

if ! curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
  echo "Backend did not start. See $BACKEND_LOG"
  exit 1
fi

if ! curl -fsS http://127.0.0.1:8001/health >/dev/null; then
  echo "AI service did not start. See $AI_LOG"
  exit 1
fi

echo
echo "Ken Dala 2 is ready: http://127.0.0.1:5173/login"
echo "Public registry:       http://127.0.0.1:5173/public/registry"
echo "AI service:            http://127.0.0.1:8001/health"
echo
echo "Demo phones:"
echo "  Applicant:            +77000000001"
echo "  Chairman:            +77000000002"
echo "  Commission member:   +77000000003"
echo "  Secretary:           +77000000004"
echo
echo "Keep this window open. Press Ctrl+C to stop services started by this script."

while true; do sleep 3600; done
