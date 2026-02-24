# ---- Base image (default fallback) ----
ARG BASE_IMAGE
FROM ${BASE_IMAGE:-eclipse-temurin:21-jdk}

WORKDIR /app

# ---- Dependencies ----
RUN apt-get update \
    && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/*

# ---- Application files ----
COPY docker/* /app/
COPY build/libs/*.jar /app/
COPY lib/applicationinsights.json /app/
# Temp fix we need to work out the actual app user
RUN test -n "$JAVA_HOME" \
 && test -f "$JAVA_HOME/lib/security/cacerts" \
 && chmod 777 "$JAVA_HOME/lib/security/cacerts"

# ---- Runtime ----
EXPOSE 4550

ENTRYPOINT ["/bin/sh","./startup.sh"]