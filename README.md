<h1 align="center">
  <img src="docs/imgs/logo.jpg" alt="site-guard" width="64" height="64" />
  <br />
  site-guard
</h1>

<p align="center">
  <font size="5"><b><i>不让故障,悄悄发生</i></b></font><br />
</p>

<p align="center">
  对站点的可用性、证书有效期、关键路径进行持续巡检,<br />
  异常时自动推送钉钉 / 飞书 / 企业微信。
</p>

<p align="center">
  <a href="https://www.docker.com/">
    <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker" alt="Docker" />
  </a>
  <a href="https://www.java.com/">
    <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk" alt="Java 17" />
  </a>
  <a href="https://nuxt.com/">
    <img src="https://img.shields.io/badge/Nuxt-3-00DC82?style=flat-square&logo=nuxt" alt="Nuxt 3" />
  </a>
</p>

![站点管理](docs/imgs/sites.png)

![Open Dashboard](docs/imgs/open-dashboard.png)

## ✨ 主要功能

- **站点与分类管理** —— 自定义分类,资产一目了然。
- **可用性探活** —— HTTP 探针定时检测,自动统计总览 / 健康 / 异常 / 待检测 / 暂停。
- **SSL 证书到期监控** —— 解析证书剩余天数,提前发现即将过期的证书。
- **关键路径探针** —— 在主域名之外,对 `/healthz`、`/api/orders/recent` 等关键路径做二次校验,消除"主站 200 但业务挂了"的盲区。
- **告警通知** —— 站点异常或恢复时,按订阅规则推送钉钉、飞书、企业微信。
- **公开大屏** —— 只读视图,无需登录即可查看整体健康度与最近异常,适合内嵌大屏或分享给非管理员。

<p float="left">
  <img src="docs/imgs/im.png" width="400"  alt="IM Message"/>
</p>

## 🚀 快速部署(Docker Compose)

```bash
git clone https://github.com/sunmh207/site-guard.git
cd site-guard
cp .env.example .env
docker compose up -d
```

访问管理后台:http://localhost:1080  (默认账号 `admin` / `admin`,首次登录后请立即修改)

## 💻 本地开发

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

访问:http://localhost:3001