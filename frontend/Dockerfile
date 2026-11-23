# Next.js frontend. Interim image for the App Router migration (ADR 0001): it runs
# `next start` so Docker Compose keeps working. #86 replaces it with a standalone,
# non-root image with a health check.

FROM node:24-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

COPY package*.json ./
RUN npm ci

# NEXT_PUBLIC_* values are inlined into the browser bundle at build time: never secrets.
ARG NEXT_PUBLIC_BACKEND_URL=/api
ARG NEXT_PUBLIC_FRONTEND_URL=http://localhost:3000
ARG NEXT_PUBLIC_BACKEND_WS_URL=
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID=
ARG NEXT_PUBLIC_MSAL_CLIENT_ID=
ARG NEXT_PUBLIC_MSAL_AUTHORITY=
ARG NEXT_PUBLIC_RECAPTCHA_SITE_KEY=
# The /api rewrite target is resolved at build time, so it is a build argument too.
ARG API_INTERNAL_URL=http://backend:8090
# Statically prerendered pages read the default marketplace at build time.
ARG DEFAULT_MARKETPLACE_ID=

ENV NEXT_PUBLIC_BACKEND_URL=$NEXT_PUBLIC_BACKEND_URL \
    NEXT_PUBLIC_FRONTEND_URL=$NEXT_PUBLIC_FRONTEND_URL \
    NEXT_PUBLIC_BACKEND_WS_URL=$NEXT_PUBLIC_BACKEND_WS_URL \
    NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID \
    NEXT_PUBLIC_MSAL_CLIENT_ID=$NEXT_PUBLIC_MSAL_CLIENT_ID \
    NEXT_PUBLIC_MSAL_AUTHORITY=$NEXT_PUBLIC_MSAL_AUTHORITY \
    NEXT_PUBLIC_RECAPTCHA_SITE_KEY=$NEXT_PUBLIC_RECAPTCHA_SITE_KEY \
    API_INTERNAL_URL=$API_INTERNAL_URL \
    DEFAULT_MARKETPLACE_ID=$DEFAULT_MARKETPLACE_ID

COPY . .
RUN npm run build && npm prune --omit=dev

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

COPY --from=build /app/package.json /app/package-lock.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY --from=build /app/next.config.ts ./next.config.ts

EXPOSE 3000
CMD ["node", "node_modules/next/dist/bin/next", "start", "-p", "3000"]
