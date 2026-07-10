# ================================
# 多阶段构建 Dockerfile
# Nuxt (Static) + Spring Boot 4.0
# Node 24 + JDK 25 + Gradle 9.4.1
# ================================

# -------------------- 阶段 1: 构建前端 --------------------
FROM node:24-alpine AS frontend-builder

WORKDIR /frontend

# 复制依赖描述文件，提高缓存效率
COPY web/package.json web/pnpm-lock.yaml ./

ARG NPM_REGISTRY=https://registry.npmmirror.com
RUN npm config set registry ${NPM_REGISTRY} \
    && npm install -g pnpm@10.26.0 \
    && pnpm install --frozen-lockfile

# 复制前端源码
COPY web/ ./

# 生成纯静态文件（ssr: false）
RUN pnpm run generate


# -------------------- 阶段 2: 构建后端 --------------------
# 使用 Gradle Wrapper 在线下载指定版本（无需预置 gradle-cache，方便 CI 干净环境构建）
FROM gradle:jdk25-alpine AS backend-builder

WORKDIR /backend

# 复制 Gradle Wrapper 与构建脚本（利用缓存：依赖描述变更时才重跑此层）
COPY server/gradle ./gradle
COPY server/gradlew .
COPY server/gradlew.bat .
COPY server/build.gradle.kts .
COPY server/settings.gradle.kts .

# 让 Wrapper 把 Gradle 发行版与依赖缓存到固定目录，便于层缓存复用
ENV GRADLE_USER_HOME=/gradle

# 预解析依赖，加速后续构建
RUN ./gradlew dependencies --no-daemon || true

# 复制后端源码
COPY server/ ./

# 构建 Spring Boot 可执行 JAR
RUN ./gradlew bootJar --no-daemon


# -------------------- 阶段 3: 运行时镜像 --------------------
FROM eclipse-temurin:25-jdk-noble AS runtime

ENV TZ=Asia/Shanghai

# 安装运行依赖
RUN apt-get update \
 && apt-get install -y nginx supervisor wget tzdata \
 && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
 && echo $TZ > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 复制后端 JAR
COPY --from=backend-builder /backend/build/libs/*.jar /app/app.jar

# 复制前端静态资源
COPY --from=frontend-builder /frontend/.output/public /usr/share/nginx/html

# 创建必要目录
RUN mkdir -p /app/data /app/logs \
    && rm -f /etc/nginx/sites-enabled/default \
    && rm -f /etc/nginx/conf.d/default.conf

# 复制 Nginx & Supervisor 配置
COPY docker/nginx.conf /etc/nginx/conf.d/app.conf
COPY docker/supervisord.conf /etc/supervisord.conf

EXPOSE 80 8080

# 应用参数（JVM 参数由 docker-compose.yml 注入，不在此处硬编码）
ENV JWT_SECRET=ChangeThisSecretKeyInProduction

# 健康检查：通过 nginx 探测，nginx 故障或 Spring Boot 故障都能被识别
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q -O - http://localhost:80/actuator/health || exit 1

# 启动 Supervisor 管理 Nginx + Spring Boot
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisord.conf"]