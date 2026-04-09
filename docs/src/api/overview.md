# 接口总览

:::tip 阅读建议
先阅读本页的响应约定与鉴权规则，再查看各个单接口页面。
:::

:::warning Token 失效处理
当返回 `401` 或 `4011` 时，建议前端统一清理登录态并跳转登录页。
:::

:::info 文档结构
当前文档采用“一个页面一个接口”的方式，便于快速定位与复制调用示例。
:::

## 基础信息

- 服务名称：`pyisland-admin/server`
- 全局前缀：`/api`
- 线上基址：`https://server.pyisland.com/api`
- 数据格式：`application/json`

## 响应约定

大多数接口遵循统一响应结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `number` | 业务状态码 |
| `message` | `string` | 响应消息 |
| `data` | `any` | 业务数据，部分接口不存在 |

## 认证与权限

除以下接口外，均需要携带 `Authorization: Bearer <token>` 请求头：

- `POST /api/auth/login`
- `GET /api/v1/version`
- `GET /api/v1/version/list`
- `GET /api/v1/service-status`
- `GET /api/v1/service-status/list`

### 鉴权失败示例

```json
{
  "code": 401,
  "message": "未登录或token已过期"
}
```

```json
{
  "code": 4011,
  "message": "账号已在其他设备登录"
}
```

## 接口分组

- [认证接口](/api/auth.html)
- [用户管理接口](/api/users.html)
- [版本管理接口](/api/version.html)
- [服务状态接口](/api/service-status.html)
- [上传接口](/api/upload.html)
