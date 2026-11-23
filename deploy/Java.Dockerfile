FROM maven:3.9.11-eclipse-temurin-17 AS build
ARG MODULE=backend
WORKDIR /build
COPY ${MODULE}/pom.xml .
COPY ${MODULE}/src src
RUN mvn -B -ntp package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --no-create-home campus
COPY --from=build --chown=campus:campus /build/target/*.jar /app/app.jar
USER campus
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/app.jar"]
