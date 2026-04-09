# PUT /api/v1/service-status

## 基础信息

- Method: `PUT`
- Path: `/api/v1/service-status`
- Auth: 是（Bearer Token）
- Content-Type: `application/json`

## 请求体

```json
{
  "apiName": "version",
  "status": true,
  "message": "运行正常",
  "remark": "可用"
}
```

## 响应

```json
{
  "code": 200,
  "message": "接口状态更新成功",
  "data": {
    "apiName": "version",
    "status": true,
    "message": "运行正常",
    "remark": "可用",
    "updatedAt": "2026-04-09T12:00:00"
  }
}
```

```json
{
  "code": 400,
  "message": "接口名称不能为空"
}
```

```json
{
  "code": 400,
  "message": "接口状态不能为空"
}
```
