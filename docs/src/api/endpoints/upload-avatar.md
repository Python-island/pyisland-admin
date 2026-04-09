# POST /api/v1/upload/avatar

## 基础信息

- Method: `POST`
- Path: `/api/v1/upload/avatar`
- Auth: 是（Bearer Token）
- Content-Type: `multipart/form-data`

## 表单字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | `file` | 是 | 图片文件 |

## 约束

- 文件不能为空
- 文件大小不超过 `5MB`
- 仅允许 `image/*` 类型

## 响应

```json
{
  "code": 200,
  "message": "上传成功",
  "data": "https://oss.example.com/avatars/uuid.png"
}
```

```json
{
  "code": 400,
  "message": "文件不能为空"
}
```

```json
{
  "code": 400,
  "message": "头像文件不能超过 5MB"
}
```

```json
{
  "code": 400,
  "message": "只能上传图片文件"
}
```

```json
{
  "code": 500,
  "message": "上传失败: <error_message>"
}
```
