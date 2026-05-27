# Test Client

`test-client` is a lightweight Spring Boot proxy for quickly testing gateway endpoints.
It is intended as a starter module you can later evolve into a UI-facing backend.

## Run

```bash
mvn -pl test-client spring-boot:run
```

Default URL: `http://localhost:8084`

## Available Endpoints

- `GET /test/health`
- `POST /test/auth/register`
- `POST /test/auth/login`
- `POST /test/loans`
- `GET /test/loans`
- `GET /test/loans/{id}`
- `PATCH /test/loans/{id}/status`
- `POST /test/loans/{id}/assessment`
- `POST /test/loans/{id}/decision`
- `POST /test/loans/{id}/documents`
- `PATCH /test/loans/{id}/disburse`

For loan endpoints, pass `Authorization: Bearer <token>` from login.

## Smoke / lifecycle scripts

From repo root (with `./start-all.sh` running):

| Script | What it does |
|--------|----------------|
| `./test-client-smoke.sh` | Quick: register, login, create/list/get loan via test-client |
| `./test-client-lifecycle.sh` | **Full EP7-T4 lifecycle** via test-client → gateway |
| `./test-gateway.sh` | Quick smoke direct on gateway (`:8080`) |
| `./test-gateway-lifecycle.sh` | **Full EP7-T4 lifecycle** direct on gateway (`:8080`) |

Lifecycle scripts also verify `audit_logs`, `loan_status_events`, and `notification_log` when Docker DBs are running.
