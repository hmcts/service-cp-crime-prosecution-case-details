# Dockerfile (project root)
ARG BASE_IMAGE
FROM ${BASE_IMAGE:-eclipse-temurin:25-jre}

# install curl for debugging
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# run as non-root
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app

# copy startup script and app jar file
COPY docker/* /app/
COPY build/libs/*.jar /app/
COPY lib/applicationinsights.json /app/

# Not sure this does anything useful we can drop once we sort certificates
RUN test -n "$JAVA_HOME" \
 && test -f "$JAVA_HOME/lib/security/cacerts" \
 && chmod 777 "$JAVA_HOME/lib/security/cacerts"

USER app
ENTRYPOINT ["/bin/sh","./startup.sh"]