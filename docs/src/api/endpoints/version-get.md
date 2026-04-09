# GET /api/v1/version

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
