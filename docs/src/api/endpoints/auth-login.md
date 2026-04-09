# POST /api/auth/login

:::tip 用途
用于后台管理员登录并获取 JWT token。
:::

:::warning 登录安全
建议前端限制登录重试次数，并避免在日志中输出明文密码。
:::

:::info 调试建议
拿到 token 后先调用一个受保护接口，快速验证登录态是否生效。
:::

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
