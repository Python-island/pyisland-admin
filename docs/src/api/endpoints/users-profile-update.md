# PUT /api/v1/users/profile

## 基础信息

- Method: `PUT`
- Path: `/api/v1/users/profile`
- Auth: 是（Bearer Token）
- Content-Type: `application/json`

## 请求体

```json
{
  "username": "admin",
  "password": "new_password",
  "avatar": "https://example.com/avatar.png"
}
```

## 响应

```json
{
  "code": 200,
  "message": "更新成功"
}
```

```json
{
  "code": 400,
  "message": "用户名不能为空"
}
```

```json
{
  "code": 404,
  "message": "用户不存在"
}
```
