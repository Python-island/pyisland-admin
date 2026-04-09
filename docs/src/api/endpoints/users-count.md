# GET /api/v1/users/count

:::tip 用途
用于展示管理员总数统计信息。
:::

:::warning 鉴权
该接口需要 `Bearer Token`。
:::

:::danger 安全要求
该接口属于管理员后台能力，禁止作为公开接口对外提供；仅允许在完成鉴权的后台系统中调用。
:::

:::info 建议
可与列表接口并行请求，缩短页面加载时间。
:::

## 基础信息

- Method: `GET`
- Path: `/api/v1/users/count`
- Auth: 是（Bearer Token）

## 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": 1
}
```
