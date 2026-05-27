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
