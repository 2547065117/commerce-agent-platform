FROM node:22-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /workspace/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -B -ntp package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system portal && useradd --system --gid portal portal && mkdir /app/data && chown portal:portal /app/data
COPY --from=backend /workspace/backend/target/enterprise-portal-1.0.0.jar /app/app.jar
USER portal
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
