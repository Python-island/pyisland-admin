# DELETE /api/v1/users

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
