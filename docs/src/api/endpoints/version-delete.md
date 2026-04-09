# DELETE /api/v1/version

:::tip 用途
用于删除指定应用的版本记录。
:::

:::warning 风险提示
删除后该应用版本信息将不可查询，建议先做操作确认。
:::

:::info 参数说明
通过 query 参数 `appName` 指定删除目标。
:::

## 基础信息

- Method: `DELETE`
- Path: `/api/v1/version?appName={appName}`
- Auth: 是（Bearer Token）

## 响应

```json
{
  "code": 200,
  "message": "版本信息删除成功"
}
```

```json
{
  "code": 404,
  "message": "版本信息不存在"
}
```
