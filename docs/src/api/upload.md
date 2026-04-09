# 上传接口

> 路径前缀：`/api/v1/upload`

## 上传头像

- **Method**: `POST`
- **Path**: `/api/v1/upload/avatar`
- **Auth**: 是
- **Content-Type**: `multipart/form-data`

### 表单字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | `file` | 是 | 图片文件 |

### 约束

- 文件不能为空
- 文件大小不超过 `5MB`
- 仅允许 `image/*` 类型

### 成功响应

```json
{
  "code": 200,
  "message": "上传成功",
  "data": "https://oss.example.com/avatars/uuid.png"
}
```

### 失败响应

参数校验失败：

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

上传异常：

```json
{
  "code": 500,
  "message": "上传失败: <error_message>"
}
```

## 前端调用示例

### fetch

```ts
const baseUrl = "https://server.pyisland.com/api";

export async function uploadAvatar(file: File) {
  const token = localStorage.getItem("token") || "";
  const formData = new FormData();
  formData.append("file", file);

  const res = await fetch(`${baseUrl}/v1/upload/avatar`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: formData,
  });

  const json = await res.json();
  if (json.code !== 200) throw new Error(json.message);
  return json.data;
}
```

### axios

```ts
import axios from "axios";

const http = axios.create({
  baseURL: "https://server.pyisland.com/api",
});

export async function uploadAvatar(file: File) {
  const token = localStorage.getItem("token") || "";
  const formData = new FormData();
  formData.append("file", file);

  const { data } = await http.post("/v1/upload/avatar", formData, {
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "multipart/form-data",
    },
  });

  if (data.code !== 200) throw new Error(data.message);
  return data.data;
}
```
