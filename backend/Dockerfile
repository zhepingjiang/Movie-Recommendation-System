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
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-jar", "app.jar"]
