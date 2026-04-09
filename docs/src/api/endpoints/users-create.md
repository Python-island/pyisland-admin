# POST /api/v1/users

:::tip 用途
用于新增后台管理员账号。
:::

:::warning 参数校验
`username` 与 `password` 为空会返回 `400`。
:::

:::danger 安全要求
该接口属于管理员后台能力，禁止作为公开接口对外提供；仅允许在完成鉴权的后台系统中调用。
:::

:::info 建议
创建成功后建议立即刷新管理员列表，保持界面数据一致。
:::

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
