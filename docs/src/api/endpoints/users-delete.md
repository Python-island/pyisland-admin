# DELETE /api/v1/users

:::tip 用途
用于删除指定管理员账号。
:::

:::warning 风险提示
删除后不可恢复，建议在前端增加二次确认弹窗。
:::

:::info 参数说明
通过 query 参数 `username` 指定要删除的用户。
:::

## 基础信息

- Method: `DELETE`
- Path: `/api/v1/users?username={username}`
- Auth: 是（Bearer Token）

## Query 参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `string` | 是 | 用户名 |

## 响应

```json
{
  "code": 200,
  "message": "删除成功"
}
```

```json
{
  "code": 404,
  "message": "用户不存在"
}
```
