# ShopWave frontend: Next.js standalone server (ADR 0001).
#
# Build arguments are baked into the build:
#   - NEXT_PUBLIC_* values are inlined into the browser bundle (public; never secrets).
#   - API_INTERNAL_URL is the /api rewrite target, resolved when `next build` runs.
#   - DEFAULT_MARKETPLACE_ID is read by pages prerendered during the build.
# API_INTERNAL_URL and DEFAULT_MARKETPLACE_ID are also read at runtime by server
# rendering, so set them on the container as well (docker-compose.yml does).

FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

ARG NEXT_PUBLIC_BACKEND_URL=/api
ARG NEXT_PUBLIC_FRONTEND_URL=http://localhost:3000
ARG NEXT_PUBLIC_BACKEND_WS_URL=
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID=
ARG NEXT_PUBLIC_MSAL_CLIENT_ID=
ARG NEXT_PUBLIC_MSAL_AUTHORITY=
ARG NEXT_PUBLIC_RECAPTCHA_SITE_KEY=
ARG API_INTERNAL_URL=http://backend:8090
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

COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:24-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

RUN addgroup -S -g 1001 nodejs && adduser -S -u 1001 -G nodejs nextjs

# The standalone output contains server.js and only the node_modules it needs.
# Static assets and public/ are served by server.js but are not copied into the
# standalone folder by `next build`. The ISR cache under .next/ must be writable.
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=build --chown=nextjs:nodejs /app/public ./public

USER nextjs
EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:3000/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.js"]
