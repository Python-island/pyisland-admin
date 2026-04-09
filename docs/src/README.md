---
title: PyIsland API 文档站
navbar: true
sidebar: true
---

# PyIsland API 文档站

:::tip 文档说明
本页用于快速了解 API 全貌，具体接口请从左侧侧边栏按分组进入。
:::

:::warning 鉴权提醒
除公开接口外，调用时都需要携带 `Authorization: Bearer <token>`。
:::

:::info 线上环境
推荐基址：`https://server.pyisland.com/api`
:::

## 项目说明

本项目为 `pyisland-admin/server` 的接口文档站，覆盖当前后端控制器中的全部公开 API。

- 服务基路径：`/api`
- 线上示例地址：`https://server.pyisland.com/api`
- 接口风格：JSON 统一返回结构

## 统一返回结构

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

- `code`: 业务状态码
- `message`: 结果描述
- `data`: 业务数据（部分接口可为空）

## 鉴权规则

除以下接口外，其余接口都需要在请求头携带 JWT：

- `GET /api/v1/version/**`
- `GET /api/v1/service-status/**`
- `POST /api/auth/login`

鉴权请求头：

```text
Authorization: Bearer <token>
```

## 文档目录

- [接口总览](/api/overview.html)
- [前端 SDK 示例](/api/sdk-example.html)

### 单接口页面示例

- [POST /api/auth/login](/api/endpoints/auth-login.html)
- [GET /api/v1/users](/api/endpoints/users-list.html)
- [GET /api/v1/version/list](/api/endpoints/version-list.html)
- [GET /api/v1/service-status/list](/api/endpoints/service-status-list.html)
- [POST /api/v1/upload/avatar](/api/endpoints/upload-avatar.html)

其余接口可通过左侧 `sidebar` 的 `children-item` 逐个进入。
