FROM node:20-alpine AS web-build
WORKDIR /web
COPY lumina-web/package.json lumina-web/pnpm-lock.yaml ./
RUN npm install -g pnpm && pnpm install
COPY lumina-web/ ./
RUN pnpm build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY --from=web-build /web/dist ./src/main/resources/static
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
LABEL version="0.1.0"
LABEL description="Lumina - High-performance LLM API Gateway"
WORKDIR /app
RUN apk add --no-cache redis dos2unix
COPY --from=build /app/target/*.jar app.jar
COPY startup.sh ./
RUN dos2unix startup.sh && chmod +x startup.sh
EXPOSE 8080
ENTRYPOINT ["./startup.sh"]
