# PyIsland API 文档站

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
- [认证接口](/api/auth.html)
- [用户管理接口](/api/users.html)
- [版本管理接口](/api/version.html)
- [服务状态接口](/api/service-status.html)
- [上传接口](/api/upload.html)
