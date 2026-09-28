# Job image for the Flink Kubernetes Operator (k8s/flink.yaml): the Flink runtime plus the
# nearline job's fat jar, so the FlinkDeployment can start it in application mode from
# local:///opt/flink/usrlib/streaming.jar. Replaces docker-compose.yml's host-mounted
# ./streaming/build/libs, which minikube's pods can't see. docker-compose is unaffected -- it
# still uses the stock flink image + that mount.
#
# Build context is streaming/: docker build -t movie-rec-streaming:local streaming
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
# shadowJar directly, not build -- `./gradlew build` is broken by a pre-existing shadow plugin /
# Gradle 9 incompatibility in the distribution tasks (unrelated to the jar itself).
RUN ./gradlew shadowJar --no-daemon

FROM flink:2.2.1-java17
# The image ships the S3 filesystem under opt/ but doesn't enable it. Baked into plugins/ here
# rather than relying on the entrypoint's ENABLE_BUILT_IN_PLUGINS (docker-compose's approach), so
# it's present however the operator launches the jobmanager/taskmanager. Presto rather than
# Hadoop S3, matching docker-compose (Flink's docs recommend Presto for checkpoint storage).
RUN mkdir -p /opt/flink/plugins/s3-fs-presto && \
    cp /opt/flink/opt/flink-s3-fs-presto-2.2.1.jar /opt/flink/plugins/s3-fs-presto/
COPY --from=build /app/build/libs/streaming-0.0.1-SNAPSHOT.jar /opt/flink/usrlib/streaming.jar
