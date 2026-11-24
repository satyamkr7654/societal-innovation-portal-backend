# One container: the React app is built here and served by Spring Boot.
FROM node:24-alpine AS web
RUN apk add --no-cache git
ARG FRONTEND_REPO=https://github.com/satyamkr7654/societal-innovation-portal-frontend.git
ARG FRONTEND_REF=main
RUN git clone --depth 1 --branch "$FRONTEND_REF" "$FRONTEND_REPO" /web
WORKDIR /web
RUN npm ci && npm run build

FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /api
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home app && mkdir uploads && chown app uploads
COPY --from=api /api/target/portal-backend-0.1.0.jar app.jar
COPY --from=web /web/dist frontend
USER app
ENV FRONTEND_DIR=/app/frontend SERVER_ADDRESS=0.0.0.0 COOKIE_SECURE=true SERVER_FORWARD_HEADERS_STRATEGY=native
# Render and similar hosts choose the port through PORT
CMD ["sh", "-c", "SERVER_PORT=${PORT:-8080} exec java -XX:MaxRAMPercentage=55 -XX:+UseSerialGC -jar app.jar"]
