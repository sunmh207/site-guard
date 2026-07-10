# site-guard

Spring Boot 4.0 + Nuxt 4 站点监控告警系统。

## 技术栈

- **前端**: Nuxt 4 (Vue 3, TypeScript) + @nuxt/ui + Pinia
- **后端**: Spring Boot 4.0 (Java 25) + Spring Data JPA + Spring Security + Flyway
- **数据库**: MySQL 8
- **构建工具**: Gradle 9.4.1 + pnpm 10

## 开发模式

### 后端

```bash
cd server
./gradlew bootRun
```

### 前端

```bash
cd web
pnpm install
pnpm dev
```

访问：<http://localhost:3001>

## Docker 部署

详见 [docs/docker/build_deploy.md](docs/docker/build_deploy.md)。

快速启动：

```bash
# 一键启动（Gradle 由 Wrapper 在容器内自动下载）
make up
```

访问：<http://localhost:1080>