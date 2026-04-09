# POST /api/v1/users

## 基础信息

- Method: `POST`
- Path: `/api/v1/users`
- Auth: 是（Bearer Token）
- Content-Type: `application/json`

## 请求体

```json
{
  "username": "new_admin",
  "password": "123456"
}
```

## 响应

```json
{
  "code": 200,
  "message": "添加成功"
}
```

```json
{
  "code": 400,
  "message": "用户名和密码不能为空"
}
```

```json
{
  "code": 409,
  "message": "用户名已存在"
}
```
