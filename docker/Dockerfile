FROM eclipse-temurin:25-jre-noble
WORKDIR /opt/mystem4j
COPY mystem4j-http-server/build/install/mystem4j-http-server/lib/ ./lib/
ENV MYSTEM_HOST=0.0.0.0 MYSTEM_PORT=8080 MYSTEM_EXECUTABLE=/opt/mystem/mystem
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "/opt/mystem4j/lib/*", "io.github.ulviar.mystem4j.server.MystemServerMain"]
