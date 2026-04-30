# AI Data Analyst Dashboard

A beginner-friendly full-stack dashboard for uploading CSV files, previewing data, asking natural language questions, and receiving chart-ready results with an AI-generated summary.

## Tech Stack

- Backend: Java 17, Spring Boot, Spring Web, Spring Data JPA
- Frontend: React, Tailwind CSS, Axios
- Charts: Recharts
- CSV parsing: Apache Commons CSV
- Database: PostgreSQL
- AI: OpenAI API through a REST call

## Project Structure

```text
backend/
frontend/
README.md
```

## Backend Setup

1. Create a PostgreSQL database:

```sql
CREATE DATABASE ai_data_analyst;
```

2. Update `backend/src/main/resources/application.properties`:

```properties
spring.datasource.username=postgres
spring.datasource.password=YOUR_DATABASE_PASSWORD
openai.api.key=YOUR_OPENAI_API_KEY
```

3. Start the backend:

```bash
cd backend
mvn spring-boot:run
```

The API runs at `http://localhost:8080`.

## Frontend Setup

1. Install dependencies:

```bash
cd frontend
npm install
```

2. Start the frontend:

```bash
npm run dev
```

The app runs at `http://localhost:5173`.

## API Endpoints

- `POST /api/upload` uploads a CSV file as multipart form data with field name `file`.
- `GET /api/data` returns the latest uploaded dataset preview.
- `POST /api/query` accepts a question:

```json
{
  "question": "show sales by month"
}
```

- `GET /api/history` returns recent query history.

## Query Response

```json
{
  "chartType": "bar",
  "labels": ["January", "February"],
  "values": [1200, 1800],
  "summary": "The sum of sales grouped by month is highest for February at 1800.00."
}
```

## Notes

- If `openai.api.key` is still set to `YOUR_OPENAI_API_KEY`, the backend uses a simple local fallback to infer a query from the question and CSV headers.
- `spring.jpa.hibernate.ddl-auto=update` creates tables automatically for local development.
- The dashboard always analyzes the most recently uploaded CSV.
