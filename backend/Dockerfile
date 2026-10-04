FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x ./mvnw

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
RUN addgroup -S --gid 10001 app \
    && adduser -S --uid 10001 --ingroup app app

COPY --from=build --chown=app:app /workspace/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
