# GET /api/v1/service-status/list

## 基础信息

- Method: `GET`
- Path: `/api/v1/service-status/list`
- Auth: 否

## 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "apiName": "version",
      "status": true,
      "message": "运行正常",
      "remark": "",
      "updatedAt": "2026-04-09T12:00:00"
    }
  ]
}
```
