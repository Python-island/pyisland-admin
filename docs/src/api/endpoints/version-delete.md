# DELETE /api/v1/version

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
