# GET /api/v1/version/list

:::tip 用途
用于展示所有应用版本信息。
:::

:::warning 公开接口
该接口无需登录即可访问。
:::

:::info 对接建议
官网下载页可直接消费该接口中的 `downloadUrl` 字段。
:::

## 基础信息

- Method: `GET`
- Path: `/api/v1/version/list`
- Auth: 否

## 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "appName": "pyisland",
      "version": "26.0.0",
      "description": "...",
      "downloadUrl": "https://...",
      "updatedAt": "2026-04-09T12:00:00"
    }
  ]
}
```
