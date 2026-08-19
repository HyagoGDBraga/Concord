# syntax=docker/dockerfile:1
# =============================================================================
# Concord — frontend
# Estagios: base -> deps -> dev              (desenvolvimento)
#           base -> deps -> builder -> runtime  (producao)
# =============================================================================

FROM node:22-alpine AS base
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

FROM base AS deps
COPY package.json package-lock.json* ./
# Na primeira execucao ainda nao existe lockfile; depois dela, 'npm ci' garante
# instalacao reproduzivel.
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

# --- desenvolvimento ---------------------------------------------------------
FROM base AS dev
COPY --from=deps /app/node_modules ./node_modules
COPY . .
EXPOSE 3000
CMD ["npm", "run", "dev"]

# --- build de producao -------------------------------------------------------
FROM base AS builder
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# --- runtime de producao -----------------------------------------------------
FROM base AS runtime
ENV NODE_ENV=production
RUN addgroup -S nodejs && adduser -S nextjs -G nodejs
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
ENV PORT=3000 HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
