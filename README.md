# MySend

MySend is a temporary workspace for sharing text and files. Create a room,
send its five-character code, and close it when the transfer is done. Guests
can use the core flow without creating an account.

## Product rules

| Capability | Guest | Free account | Premium |
| --- | ---: | ---: | ---: |
| Monthly price | No login | $0 | $9.99 |
| Active rooms | 1 | 2 | 5 |
| Maximum room lifetime | 15 minutes | 60 minutes | 180 minutes |
| Clipboard per room | 2,000 characters | 10,000 characters | 100,000 characters |
| Total files per room | 250 MB | 1 GB | 5 GB |
| Single file | 50 MB | 250 MB | 1 GB |
| Successful guest entries | 20 | 100 | 1,000 |

Guests can create and join rooms without an account. Free accounts add the
My ShareRooms list and higher limits. Premium raises the room, clipboard, file,
and entry limits.

## Stack

- React, TypeScript, Vinext, and Vite
- Java 21, Maven, Spring Boot, and Flyway
- PostgreSQL in production, H2 for local development
- Docker Compose for the local API and database

## Local development

The web client requires Node.js 22 or newer:

```bash
npm ci
npm run dev
```

The API requires Java 21:

```bash
cd backend
mvn spring-boot:run
```

To run the API with PostgreSQL instead:

```bash
docker compose up --build
```

## Deployment

Deploy the web client and API independently. The web build needs both public
origins:

```text
NEXT_PUBLIC_API_BASE_URL=https://api.your-domain.com
NEXT_PUBLIC_SITE_URL=https://your-domain.com
```

Build and run the API from `backend/Dockerfile`. Mount durable storage at the
same absolute path supplied as `UPLOAD_DIRECTORY`, use PostgreSQL, and set:

```text
SPRING_PROFILES_ACTIVE=production
DATABASE_URL=jdbc:postgresql://database-host:5432/mysend
DATABASE_USERNAME=mysend
DATABASE_PASSWORD=<secret>
WEB_ORIGINS=https://your-domain.com
APP_BASE_URL=https://your-domain.com
COOKIE_SECURE=true
UPLOAD_DIRECTORY=/app/uploads
STORAGE_PERSISTENT=true
MAIL_FROM=MySend <send@your-domain.com>
MAIL_DELIVERY_ENABLED=true
DEVELOPMENT_CODE_ENABLED=false
SPRING_MAIL_HOST=<smtp-host>
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<smtp-user>
SPRING_MAIL_PASSWORD=<secret>
STRIPE_SECRET_KEY=<secret>
STRIPE_WEBHOOK_SECRET=<secret>
STRIPE_PREMIUM_PRICE_ID=<price-id>
```

Configure Stripe to send subscription events to
`https://api.your-domain.com/api/billing/webhook`. The production profile
refuses to start when PostgreSQL, HTTPS cookies and origins, persistent file
storage, verified email delivery, or Stripe settings are missing. After the API
starts, use `/actuator/health` as the readiness endpoint.

Pull requests run the same checks used locally:

```bash
npm ci
npm run check
cd backend
mvn --batch-mode --no-transfer-progress verify
docker build --tag mysend-api:local .
```
