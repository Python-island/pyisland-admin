# GET /api/v1/service-status

## 基础信息

- Method: `GET`
- Path: `/api/v1/service-status?apiName={apiName}`
- Auth: 否

## 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "apiName": "version",
    "status": true,
    "message": "运行正常",
    "remark": "",
    "updatedAt": "2026-04-09T12:00:00"
  }
}
```

## 失败响应

```json
{
  "code": 404,
  "message": "接口不存在"
}
```
