# ==========================================
# ЭТАП 1: Сборка фронтенда (Builder Stage)
# ==========================================
FROM node:24-alpine AS builder
WORKDIR /app

# Кэширование зависимостей npm
COPY package.json package-lock.json ./
RUN npm ci

# Исходный код приложения
COPY . .

# Сборка статических файлов (typecheck + vite build)
RUN npm run build

# ==========================================
# ЭТАП 2: Раздача через Nginx (Runtime Stage)
# ==========================================
FROM nginx:alpine AS runtime

# Копирование скомпилированного SPA
COPY --from=builder /app/dist /usr/share/nginx/html

# Конфигурация Nginx с SPA-роутингом и проксированием /api/ к бэкенду
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
