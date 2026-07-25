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

Deploy the web client and API independently. Build the web client with
`NEXT_PUBLIC_API_BASE_URL` set to the public API origin. The API container
expects PostgreSQL, a persistent upload directory, and the environment
variables listed in `backend/.env.example`.
