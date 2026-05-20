FROM oven/bun:1.3.3-alpine

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=4000

COPY package.json bun.lock ./
COPY frontend/package.json frontend/
RUN bun install --frozen-lockfile --production

COPY frontend/app-dist.tar.gz /tmp/app-dist.tar.gz
RUN mkdir -p dist && tar -xzf /tmp/app-dist.tar.gz -C dist && rm /tmp/app-dist.tar.gz

EXPOSE 4000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD ["bun", "-e", "fetch('http://127.0.0.1:4000/').then((r)=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]

CMD ["bun", "dist/The-Ethereal-Exchange/server/server.mjs"]
