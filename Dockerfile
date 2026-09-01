FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring spring

COPY --from=build --chown=spring:spring /workspace/build/libs/app.jar /app/app.jar

ENV FILE_UPLOAD_DIR=/tmp/uploads
# 컨테이너 기본 시간대(UTC)로 작성 시각이 9시간 이르게 저장되지 않도록 한국 시간대로 맞춘다.
# (애플리케이션 코드에서도 한 번 더 고정한다 — SightseeingProjectApplication 참고)
ENV TZ=Asia/Seoul

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
