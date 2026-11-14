# 2026-11-14 - the public site on Render (free plan). One image = the children's pages + the server, on one
# address. Built by Render from this (public) repo; the pages come from the public learn-client repo.
# Nothing secret is in here: the database, join code and admin name are Render environment variables.

# ---- 1. the pages (learn-client) ----
FROM node:22.12-alpine AS client
RUN apk add --no-cache git
ARG CLIENT_REPO=https://github.com/shirperetz/brainblocks-learn-client.git
ARG CLIENT_BRANCH=main
# The site's public address, for the WhatsApp share preview (Render sets RENDER_EXTERNAL_URL).
ARG PUBLIC_ORIGIN
ARG RENDER_EXTERNAL_URL
# A new commit on the pages' repo changes this file, so the copy below is never an old one from cache.
ADD https://api.github.com/repos/shirperetz/brainblocks-learn-client/commits/main /tmp/client-commit.json
RUN git clone --depth 1 --branch "$CLIENT_BRANCH" "$CLIENT_REPO" /client
WORKDIR /client
RUN npm ci --no-audit --no-fund
RUN PUBLIC_ORIGIN="${PUBLIC_ORIGIN:-$RENDER_EXTERNAL_URL}" npm run build

# ---- 2. the server ----
FROM maven:3.9.9-eclipse-temurin-17 AS server
WORKDIR /server
COPY pom.xml .
RUN mvn -q -B dependency:go-offline -Dmaven.test.skip=true || true
COPY src ./src
RUN mvn -q -B package -Dmaven.test.skip=true

# ---- 3. what runs ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=server /server/target/brainblocks-0.0.1-SNAPSHOT.jar /app/brainblocks.jar
COPY --from=client /client/dist /app/dist
ENV SPRING_PROFILES_ACTIVE=cloud \
    CLIENT_DIST=/app/dist/ \
    TZ=Asia/Jerusalem \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -Duser.timezone=Asia/Jerusalem"
# Render sends traffic to $PORT (the app reads it); 8080 when run elsewhere.
EXPOSE 8080
USER 1000
CMD ["java", "-jar", "/app/brainblocks.jar"]
