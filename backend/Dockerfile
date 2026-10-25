# ==========================================
# ЭТАП 1: Сборка приложения (Builder Stage)
# ==========================================
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /workspace

# 1. Кэширование зависимостей Gradle
COPY backend/gradle/ gradle/
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 2. Копирование спецификации OpenAPI и исходного кода
COPY docs/ docs/
COPY backend/src/ src/

# 3. Генерация контракта и сборка исполняемого JAR
RUN ./gradlew bootJar -x test --no-daemon

# ==========================================
# ЭТАП 2: Финальный легковесный образ (Runtime)
# ==========================================
FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app

# Создание безопасного непривилегированного пользователя
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Копирование только скомпилированного JAR-файла (без исходников и компилятора)
COPY --from=builder --chown=spring:spring /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "--enable-preview", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]