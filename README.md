<div align="center">
  <h1><img src="assets/eisland.svg" alt="eIsland Logo" height="32" style="vertical-align: middle;" />&nbsp;eisland-server</h1>
  <p><strong>eIsland 服务端与管理后台 Monorepo</strong></p>
  <p>包含后端 API 服务、管理后台前端与接口文档站</p>

  [![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
  [![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)](https://react.dev/)
  [![VuePress](https://img.shields.io/badge/VuePress-2.0.0--rc.28-3EAF7C?logo=vuedotjs&logoColor=white)](https://v2.vuepress.vuejs.org/)
</div>

---

## 项目简介

`eisland-server` 是 eIsland 项目的服务端仓库，提供以下核心能力：

- 账号体系与鉴权（登录、注册、验证码、JWT）
- 用户资料与密码管理
- 版本信息与服务状态接口
- 文件上传能力（头像、资源上传）
- 管理后台前端与 API 文档站

后端接口默认基路径为 `/api`（见 `server/server-app/src/main/resources/application.yaml`）。

## 仓库结构

```text
eisland-server/
  server/   # Spring Boot 聚合工程（多模块）
  admin/    # 管理后台前端（React + TypeScript + Vite）
  docs/     # API 文档站（VuePress 2）
```

`server/` 下包含的主要模块：

- `server-app`：应用入口与打包模块
- `server-auth`：登录注册与鉴权相关接口
- `server-user`：用户资料、密码与账户相关接口
- `server-version`：版本相关接口
- `server-service-status`：服务状态接口
- `server-upload`：上传相关接口
- `server-common`：公共配置与基础能力

## 技术栈

- **后端**：Spring Boot 4、Spring Security、MyBatis、Redis、RabbitMQ
- **管理后台**：React 19、TypeScript、Vite、Tailwind CSS
- **文档站**：VuePress 2、vuepress-theme-hope

## 快速开始

### 1) 启动后端 `server`

环境要求：

- JDK 25
- Maven 3.9+
- MySQL 8+
- Redis
- RabbitMQ

在仓库根目录执行：

```bash
cd server
mvn spring-boot:run -pl server-app
```

或打包：

```bash
cd server
mvn clean package
```

### 2) 启动管理后台 `admin`

```bash
cd admin
npm install
npm run dev
```

### 3) 启动文档站 `docs`

```bash
cd docs
npm install
npm run docs:dev
```

## 后端配置说明

后端配置文件位于：

- `server/server-app/src/main/resources/application.yaml`
- `server/server-app/src/main/resources/application.dev.yaml`

生产/开发环境主要通过环境变量注入，例如：

- 数据库：`DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD`
- Redis：`REDIS_HOST` `REDIS_PORT` `REDIS_DATABASE`
- JWT：`JWT_SECRET` `JWT_EXPIRATION`
- 对象存储：`OSS_*`、`R2_*`、`WALLPAPER_R2_*`
- 邮件与校验：`RESEND_*`、`TURNSTILE_SECRET_KEY`

请在本地或部署环境中正确配置后再启动服务。

## API 文档

文档站源码位于 `docs/`，可通过以下方式构建：

```bash
cd docs
npm run docs:build
```

## 许可证 (License)

本项目采用 **GNU General Public License v3.0 (GPLv3)** 或更高版本（GPL-3.0-or-later）发布。

详细条款请查看仓库根目录的 `LICENSE` 文件。
