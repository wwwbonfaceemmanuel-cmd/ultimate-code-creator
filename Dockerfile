# Single-service image: builds the frontend, then serves it from the FastAPI backend.
FROM node:22-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM python:3.12-slim
WORKDIR /app/backend
COPY backend/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY backend/app ./app
COPY --from=frontend /app/frontend/dist /app/frontend/dist
# REQUIRE_AUTH: a deployed server stays locked until Supabase sign-in is configured.
ENV FRONTEND_DIST=/app/frontend/dist LOCAL_DATA_DIR=/data CORS_ORIGINS= REQUIRE_AUTH=true
EXPOSE 8000
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
