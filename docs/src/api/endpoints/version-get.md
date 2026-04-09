# GET /api/v1/version

:::tip 用途
用于按 `appName` 查询单个应用版本。
:::

:::warning 公开接口
该接口无需登录即可访问。
:::

:::info 典型用法
适合客户端启动时检查某个分支的最新版本信息。
:::

## 基础信息

- Method: `GET`
- Path: `/api/v1/version?appName={appName}`
- Auth: 否

## Query 参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `appName` | `string` | 是 | 应用名称 |

## 响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "appName": "pyisland",
    "version": "26.0.0",
    "description": "...",
    "downloadUrl": "https://...",
    "updatedAt": "2026-04-09T12:00:00"
  }
}
```

```json
{
  "code": 404,
  "message": "版本信息不存在"
}
```
