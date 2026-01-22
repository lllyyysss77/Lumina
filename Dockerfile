FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache redis
COPY --from=build /app/target/*.jar app.jar
COPY startup.sh ./
RUN chmod +x startup.sh
EXPOSE 8080
ENTRYPOINT ["./startup.sh"]
