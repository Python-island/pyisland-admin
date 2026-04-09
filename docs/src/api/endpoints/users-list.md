# GET /api/v1/users

:::tip 用途
用于后台用户管理页面加载管理员列表。
:::

:::warning 鉴权
该接口需要 `Bearer Token`，未登录会返回 `401`。
:::

:::info 建议
列表渲染前可先判断 `data` 是否为数组，降低空数据报错风险。
:::

## 基础信息

- Method: `GET`
- Path: `/api/v1/users`
- Auth: 是（Bearer Token）

## 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "username": "admin",
      "avatar": "https://...",
      "createdAt": "2026-04-09T12:00:00"
    }
  ]
}
```
