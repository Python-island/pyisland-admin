# POST /api/auth/login

## 基础信息

- Method: `POST`
- Path: `/api/auth/login`
- Auth: 否
- Content-Type: `application/json`

## 请求体

```json
{
  "username": "admin",
  "password": "123456"
}
```

## 成功响应

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "<jwt_token>",
    "username": "admin"
  }
}
```

## 失败响应

```json
{
  "code": 400,
  "message": "用户名和密码不能为空"
}
```

```json
{
  "code": 401,
  "message": "用户名或密码错误"
}
```
