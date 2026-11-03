# Jar is built outside (./mvnw package or Tilt); here only layering for cache-friendly images.
FROM eclipse-temurin:25-jre AS extract
WORKDIR /build
COPY target/catalog-service-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER 10001
# Heap size is set by the chart next to the memory limit (jvmOptions, ADR-0039); compose has no limit and uses the JVM default.
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
