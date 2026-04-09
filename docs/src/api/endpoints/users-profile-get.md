# GET /api/v1/users/profile

:::tip 用途
用于获取单个管理员的资料信息。
:::

:::warning 鉴权
该接口需要 `Bearer Token`。
:::

:::info 典型场景
常用于“个人中心”或“编辑资料”页面初始化。
:::

## 基础信息

- Method: `GET`
- Path: `/api/v1/users/profile?username={username}`
- Auth: 是（Bearer Token）

## 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "username": "admin",
    "avatar": "https://...",
    "createdAt": "2026-04-09T12:00:00"
  }
}
```

## 失败响应

```json
{
  "code": 404,
  "message": "用户不存在"
}
```
