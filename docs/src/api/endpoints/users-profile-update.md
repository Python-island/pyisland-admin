# PUT /api/v1/users/profile

:::tip 用途
用于更新管理员头像与密码。
:::

:::warning 字段行为
当 `password` 为空时，后端仅更新头像字段。
:::

:::danger 安全要求
该接口属于管理员后台能力，禁止作为公开接口对外提供；仅允许在完成鉴权的后台系统中调用。
:::

:::info 建议
更新成功后可重新拉取资料接口，确保前端状态同步。
:::

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
