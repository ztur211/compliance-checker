# --- Stage 1: build the frontend ---
FROM node:22-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Stage 2: build the backend jar (frontend dist baked into static) ---
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /src
COPY pom.xml ./
COPY engine/pom.xml engine/pom.xml
COPY app/pom.xml app/pom.xml
COPY engine engine
COPY app app
COPY --from=frontend /fe/dist app/src/main/resources/static
RUN mvn -q -B -pl app -am package -DskipTests

# --- Stage 3: runtime ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /run
COPY --from=backend /src/app/target/app-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
