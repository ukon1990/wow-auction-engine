FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN useradd --create-home --shell /bin/bash appuser

COPY app.war /app/app.war

ENV SPRING_PROFILES_ACTIVE=production
ENV JVM_XMS=96m
ENV JVM_XMX=256m
ENV JVM_MAX_METASPACE=192m
ENV JVM_MAX_DIRECT_MEMORY=32m
ENV JVM_RESERVED_CODE_CACHE=64m
ENV JVM_DEFAULT_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED -Xlog:gc*:stdout:time,level,tags"

EXPOSE 8080

USER appuser

ENTRYPOINT ["sh", "-c", "java -Xms${JVM_XMS:-96m} -Xmx${JVM_XMX:-256m} -XX:MaxMetaspaceSize=${JVM_MAX_METASPACE:-192m} -XX:MaxDirectMemorySize=${JVM_MAX_DIRECT_MEMORY:-32m} -XX:ReservedCodeCacheSize=${JVM_RESERVED_CODE_CACHE:-64m} -XX:+ExitOnOutOfMemoryError ${JVM_DEFAULT_TOOL_OPTIONS:-} ${JAVA_TOOL_OPTIONS:-} -jar /app/app.war"]
