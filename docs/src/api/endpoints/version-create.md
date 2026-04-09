# POST /api/v1/version

## 基础信息

- Method: `POST`
- Path: `/api/v1/version`
- Auth: 是（Bearer Token）
- Content-Type: `application/json`

## 请求体

```json
{
  "appName": "pyisland",
  "version": "26.1.0",
  "description": "版本说明",
  "downloadUrl": "https://example.com/download.exe"
}
```

## 响应

```json
{
  "code": 200,
  "message": "应用创建成功",
  "data": {
    "appName": "pyisland",
    "version": "26.1.0",
    "description": "版本说明",
    "downloadUrl": "https://example.com/download.exe"
  }
}
```

```json
{
  "code": 400,
  "message": "应用名称不能为空"
}
```

```json
{
  "code": 400,
  "message": "版本号不能为空"
}
```

```json
{
  "code": 409,
  "message": "该应用已存在"
}
```
