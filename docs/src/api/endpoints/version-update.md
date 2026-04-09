# PUT /api/v1/version

:::tip 用途
用于更新应用版本号、描述和下载链接。
:::

:::warning 特殊规则
当 `appName` 为空时，后端默认按 `pyisland` 更新。
:::

:::info 常见报错
若字段完全未变化，会返回 `code: 400` 且提示“没有修改内容，无需更新”。
:::

## 基础信息

- Method: `PUT`
- Path: `/api/v1/version`
- Auth: 是（Bearer Token）
- Content-Type: `application/json`

## 请求体

```json
{
  "appName": "pyisland",
  "version": "26.1.1",
  "description": "新版说明",
  "downloadUrl": "https://example.com/new.exe"
}
```

> `appName` 为空时后端默认使用 `pyisland`。

## 响应

```json
{
  "code": 200,
  "message": "版本号更新成功",
  "data": {
    "appName": "pyisland",
    "version": "26.1.1",
    "description": "新版说明",
    "downloadUrl": "https://example.com/new.exe"
  }
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
  "code": 400,
  "message": "没有修改内容，无需更新"
}
```

```json
{
  "code": 404,
  "message": "版本信息不存在"
}
```
