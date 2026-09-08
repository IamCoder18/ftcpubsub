# syntax=docker/dockerfile:1.7

# ---- Build stage ---------------------------------------------------------
# The build context is the repo root (see website/docker-bake.hcl) so the
# website/ source tree and the repo-root CHANGELOG.md are both available.
FROM node:lts-alpine AS build
WORKDIR /app

RUN mkdir -p website
COPY website/package.json website/package-lock.json* ./website/
RUN cd website && npm ci --no-audit --no-fund || npm install --no-audit --no-fund
COPY website/ ./website/
COPY CHANGELOG.md ./CHANGELOG.md

RUN cd website && npm run build

# ---- Runtime stage -------------------------------------------------------
FROM nginx:alpine AS runtime
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=build /app/website/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/website/dist /usr/share/nginx/html

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
