# POST /api/v1/version

:::tip 用途
用于新增一个应用分支的版本记录。
:::

:::warning 鉴权与幂等
该接口需要登录；同名应用重复创建会返回 `409`。
:::

:::danger 安全要求
该接口属于管理员后台能力，禁止作为公开接口对外提供；仅允许在完成鉴权的后台系统中调用。
:::

:::info 建议
创建前可先调用版本查询接口，避免重复提交。
:::

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
