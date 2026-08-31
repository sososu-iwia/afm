#!/usr/bin/env bash
# Разворачивает «Кең дала 2» на сервере: собирает образы и поднимает стек.
# Перед первым запуском заполните .env.production (см. .env.production.example).
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.production}"
COMPOSE="docker compose --env-file $ENV_FILE -f docker-compose.prod.yml -p kendala"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Нет файла $ENV_FILE."
  echo "Скопируйте шаблон и заполните значения:"
  echo "  cp .env.production.example $ENV_FILE"
  exit 1
fi

# Проверяем, что заданы обязательные секреты — иначе бэкенд не стартует.
missing=()
for key in POSTGRES_PASSWORD APP_JWT_SECRET AI_INTERNAL_API_KEY APP_CORS_ALLOWED_ORIGINS; do
  value=$(grep -E "^$key=" "$ENV_FILE" | cut -d= -f2- || true)
  [[ -z "$value" || "$value" == "ЗАПОЛНИТЕ" ]] && missing+=("$key")
done
if ((${#missing[@]})); then
  echo "Не заполнены обязательные переменные в $ENV_FILE:"
  printf '  %s\n' "${missing[@]}"
  exit 1
fi

# Номер администратора не обязателен, но с номером-заглушкой из шаблона
# в систему нельзя будет войти: SMS с кодом уйдёт в никуда.
admin_phone=$(grep -E "^APP_BOOTSTRAP_ADMIN_PHONE=" "$ENV_FILE" | cut -d= -f2- || true)
if [[ "$admin_phone" == "+77000000000" ]]; then
  echo "ВНИМАНИЕ: APP_BOOTSTRAP_ADMIN_PHONE оставлен как в шаблоне (+77000000000)."
  echo "Это несуществующий номер — код для входа получить будет некому."
  echo "Укажите рабочий номер администратора и запустите скрипт заново."
  exit 1
fi
if [[ -z "$admin_phone" ]]; then
  echo "Внимание: APP_BOOTSTRAP_ADMIN_PHONE не задан — администратор создан не будет."
  echo "Это нормально, если администратор в системе уже есть."
fi

echo "==> Сборка образов"
$COMPOSE build

echo "==> Запуск"
$COMPOSE up -d

echo "==> Ожидание готовности"
for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "Бэкенд готов."
    break
  fi
  sleep 5
done

$COMPOSE ps
echo
echo "Готово. Интерфейс: http://<адрес-сервера>:${FRONTEND_PORT:-80}"
echo "Логи:     $COMPOSE logs -f"
echo "Остановка: $COMPOSE down"
