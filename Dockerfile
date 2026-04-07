FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN useradd --create-home --shell /bin/bash appuser

COPY --from=build /workspace/target/*.war /app/app.war

ENV SPRING_PROFILES_ACTIVE=production
ENV JVM_MAX_RAM_PERCENTAGE=65.0

EXPOSE 8080

USER appuser

ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=${JVM_MAX_RAM_PERCENTAGE:-65.0} -XX:+ExitOnOutOfMemoryError ${JAVA_TOOL_OPTIONS:-} -jar /app/app.war"]
