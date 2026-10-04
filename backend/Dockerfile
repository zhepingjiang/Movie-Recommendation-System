# FIX: build context must be the repo root (docker build -f backend/Dockerfile -t <tag> .), not
# backend/ -- build.gradle's sourceSets.main.proto.srcDir '../proto' expects a proto/ directory
# as a sibling of this module's root (same shared-contract setup as recommendation/Dockerfile),
# which only exists if the build can see the repo root. This Dockerfile was never actually
# build-tested before the k8s migration: docker-compose.yml runs the backend on the host, not in
# a container, so nothing exercised this path until now.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY backend/gradlew .
COPY backend/gradle gradle
COPY backend/build.gradle backend/settings.gradle ./
COPY backend/src src
COPY proto ../proto
# copyOtelJavaagent puts the OpenTelemetry Java agent at build/otel/opentelemetry-javaagent.jar.
RUN ./gradlew bootJar copyOtelJavaagent --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# Shipped in the image but not attached here: tracing stays off unless the deployment adds
# -javaagent through JAVA_TOOL_OPTIONS (see k8s/backend.yaml).
COPY --from=build /app/build/otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-jar", "app.jar"]
