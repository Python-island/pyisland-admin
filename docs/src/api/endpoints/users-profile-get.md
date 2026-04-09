# GET /api/v1/users/profile

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
