# The POM compiles source/target 14; JDK 17 is the supported Spring Boot 2.7 LTS runtime.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Cache Maven dependencies independently from application source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Windows checkouts do not preserve the executable bit and can retain CRLF line endings.
RUN sed -i 's/\r$//' mvnw && chmod 0755 mvnw
RUN ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
# curl is used only by Docker's local liveness probe and is installed explicitly.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --create-home spring
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/springboot-init-0.0.1-SNAPSHOT.jar /app/app.jar
USER spring:spring
EXPOSE 8121
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
# The image runs with the prod profile unless an operator explicitly overrides this command.
CMD ["--spring.profiles.active=prod"]
