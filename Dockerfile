FROM eclipse-temurin:17-jdk-alpine

RUN apk add --no-cache curl

COPY target/*.jar /app/hw.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/hw.jar"]