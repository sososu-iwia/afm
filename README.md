# Кең дала 2 — AFM Backend

Система онлайн-подачи заявок АО «Аграрная кредитная корпорация».

## Стек

- Java 21, Spring Boot 3.3.5
- PostgreSQL + Flyway (17 миграций)
- Spring Security + JWT (access + refresh rotation)
- SMS OTP (локальный файл для dev, SMSC для prod)
- Spring Data JPA + JpaSpecificationExecutor
- Testcontainers (интеграционные тесты)
- Local / Supabase Storage

## Запуск PostgreSQL

```bash
docker-compose up -d postgres
```

Или вручную: создайте БД `kendala`, пользователя `kendala`.

## Быстрый запуск демо

После запуска PostgreSQL из корня проекта выполните:

```bash
./start-demo.sh
```

Скрипт поднимает Python AI-сервис, Java backend и frontend, дожидается готовности системы и выводит ссылки. Основной экран: `http://127.0.0.1:5173/login`.

Тестовые роли:

| Роль | Номер |
|---|---|
| Заявитель | `+77000000001` |
| Председатель комиссии | `+77000000002` |
| Член комиссии | `+77000000003` |
| Секретарь | `+77000000004` |

В dev-режиме тестовый OTP показывается прямо на экране ввода кода. База автоматически получает пять демонстрационных заявок с разными статусами.

Скоринг выполняется локальной обученной XGBoost-моделью, а факторы формируются SHAP. Для текстового заключения задайте `OPENAI_API_KEY` в локальном окружении. Ключи нельзя добавлять в Git или вставлять в исходный код.

## Dev-профиль

```bash
cp .env.example .env
# задайте SPRING_PROFILES_ACTIVE=dev
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd backend-java
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Dev-профиль включает:
- `APP_JWT_SECRET` — необязателен (dev-only дефолт)
- `APP_SMS_PROVIDER=local-file` (OTP сохраняется локально, реальные SMS не отправляются)
- `APP_STORAGE_PROVIDER=local` (локальная папка `./dev-uploads`)
- Swagger UI доступен на `/swagger-ui.html`

## Prod-профиль

Обязательные env-переменные:

| Переменная | Описание |
|---|---|
| `APP_JWT_SECRET` | Base64-encoded секрет (≥32 байт) |
| `APP_SMS_PROVIDER` | `smsc` |
| `SMSC_LOGIN` / `SMSC_PASSWORD` | Credentials SMSC.ru |
| `APP_STORAGE_PROVIDER` | `local` или `supabase` |
| `SUPABASE_URL` / `SUPABASE_STORAGE_BUCKET` / `SUPABASE_SERVICE_KEY` | Если `supabase` |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL |

Swagger UI **отключён** в prod-профиле.

## Maven-команды

```bash
./mvnw clean verify           # сборка + все тесты
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Тестам нужен Docker (Testcontainers). При отсутствии Docker контейнерные тесты пропускаются (`@Testcontainers(disabledWithoutDocker = true)`).

## Auth flow

1. `POST /api/auth/register` — регистрация (создаёт пользователя с ролью APPLICANT, отправляет OTP)
2. `POST /api/auth/login` — всегда возвращает 202 с одинаковым ответом (без enumeration); SMS отправляется только зарегистрированному номеру
3. `POST /api/auth/verify` — подтверждение OTP → `{ accessToken, refreshToken, user }`
4. `POST /api/auth/refresh` — ротация refresh token
5. `POST /api/auth/logout` — отзыв refresh token
6. `GET /api/auth/me` — текущий пользователь

JWT хранит `sub=userId`, claim `role`. Access TTL: 15 мин. Refresh TTL: 30 дней.

## Storage

- `APP_STORAGE_PROVIDER=local` — файлы в `APP_STORAGE_LOCAL_PATH`
- `APP_STORAGE_PROVIDER=supabase` — Supabase Storage (bucket задаётся через `SUPABASE_STORAGE_BUCKET`)
- Неизвестный провайдер — приложение не запустится (нет NoSuchBeanDefinition fallback)

## Роли

| Роль | Возможности |
|---|---|
| `APPLICANT` | Свои заявки: CRUD, upload/delete doc, submit/withdraw |
| `COMMISSION_MEMBER` | Просмотр заявок комиссии, документов, истории |
| `CHAIRMAN` | COMMISSION_MEMBER + approve, reject, request-documents |
| `SECRETARY` | Просмотр заявок, документов, истории |
| `ADMIN` | Commission операции |
| `MANAGER` | Зарезервирована, прав комиссии не имеет |
