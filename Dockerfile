# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY travel-common travel-common
COPY travel-transaction travel-transaction
COPY travel-invoice travel-invoice
COPY travel-app travel-app

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests \
    && cp travel-app/target/travel-app-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
