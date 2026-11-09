# 1단계: 빌드 (JDK + Gradle wrapper)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# Windows 에서 체크아웃하면 gradlew 가 CRLF 일 수 있어 정리한다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# 2단계: 실행 (JRE 만 포함해 이미지 크기를 줄인다)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
