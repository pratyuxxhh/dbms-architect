# Docker Compose setup

The Compose stack runs the Spring Boot API, MongoDB, and Redis together. The
application still needs `OPENAI_API_KEY`, `REACT_APP_URL`, and `STRING_TEST` in
the local `.env` file.

## Start the stack

```powershell
docker compose up --build
```

The API is available at `http://localhost:8080` by default. Set `PORT` in
`.env` to expose it on a different host port.

## Stop the stack

```powershell
docker compose down
```

To also delete the MongoDB and Redis data volumes:

```powershell
docker compose down -v
```

Do not commit `.env`; it contains secrets. Rotate any credentials that have
been exposed outside your local machine.