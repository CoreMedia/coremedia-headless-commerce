ARG JAVA_APPLICATION_BASE_IMAGE_TAG
ARG JAVA_APPLICATION_BASE_IMAGE_REPO
FROM ${JAVA_APPLICATION_BASE_IMAGE_REPO}:${JAVA_APPLICATION_BASE_IMAGE_TAG}
ENV JAVA_HEAP=2048m
ENV JAVA_PARALLEL_GC=true

EXPOSE 8080 8081 8199
VOLUME /coremedia/cache
HEALTHCHECK --start-period=30s --interval=30s --timeout=3s CMD curl -Lf http://localhost:8081/actuator/health || exit 1

COPY --chown=coremedia:coremedia src/docker /coremedia
COPY --chown=coremedia:coremedia target/headless-server-commerce-app.jar /coremedia/application.jar

CMD ["application"]
