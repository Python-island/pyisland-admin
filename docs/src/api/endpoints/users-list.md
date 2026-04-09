# GET /api/v1/users

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
