# Build stage
FROM node:26-alpine AS build
WORKDIR /app

RUN npm install --global npm@12.1.0

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production --output-path=dist/frontend

# Serving stage
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80