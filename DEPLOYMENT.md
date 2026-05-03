# Deployment

## Frontend

Deploy `frontend` to Vercel.

Environment variable:

```txt
VITE_API_URL=https://your-backend-url/api
```

## Backend

Deploy `backend` to Render as a Docker web service.

Runtime and health check:

```txt
Docker
/api/health
```

Required environment variables:

```txt
SPRING_PROFILES_ACTIVE=local
APP_CORS_ALLOWED_ORIGINS=https://your-frontend-url
```

Optional:

```txt
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-4o-mini
```

Notes:

- If `OPENAI_API_KEY` is omitted, the app uses its local fallback query inference.
- Vercel preview URLs are allowed automatically by backend CORS.
- `SPRING_PROFILES_ACTIVE=local` uses the existing in-memory H2 profile, so you do not need Postgres just to deploy a working demo.
