# Dockerfile (project root)
# Docker base image - note that this is currently overwritten by azure pipelines
ARG BASE_IMAGE
FROM ${BASE_IMAGE:-eclipse-temurin:25-jdk}

WORKDIR /app

COPY build/libs/*.jar /app/

EXPOSE ${SERVER_PORT:-8082}

ENTRYPOINT ["sh","-c","exec java -jar $(ls /app/*.jar | grep -v 'plain' | head -n1)"]
