# Stage 1: build and test
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src
COPY schemas ./schemas
COPY samples ./samples

RUN mvn -B clean package

# Stage 2: runtime
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN mkdir -p /app/schema /app/samples

COPY --from=build /workspace/target/dfdl-parser-1.0.0.jar /app/app.jar
COPY --from=build /workspace/schemas/ /app/schema/
COPY --from=build /workspace/samples/ /app/samples/

ENV DAFFODIL_SCHEMA=/app/schema/CYO_SMPREQ.xsd
ENV DAFFODIL_SAMPLES_DIR=/app/samples

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
