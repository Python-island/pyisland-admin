# 版本管理接口

> 路径前缀：`/api/v1/version`

## 查询全部版本

- **Method**: `GET`
- **Path**: `/api/v1/version/list`
- **Auth**: 否

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "appName": "pyisland",
      "version": "26.0.0",
      "description": "...",
      "downloadUrl": "https://...",
      "updatedAt": "2026-04-09T12:00:00"
    }
  ]
}
```

## 查询单个应用版本

- **Method**: `GET`
- **Path**: `/api/v1/version?appName={appName}`
- **Auth**: 否

### Query 参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `appName` | `string` | 是 | 应用名称 |

### 响应

成功：

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

不存在：

```json
{
  "code": 404,
  "message": "版本信息不存在"
}
```

## 前端调用示例

### fetch

```ts
const baseUrl = "https://server.pyisland.com/api";

export async function getVersionList() {
  const res = await fetch(`${baseUrl}/v1/version/list`);
  const json = await res.json();
  if (json.code !== 200) throw new Error(json.message);
  return json.data;
}

export async function updateVersion(payload: {
  appName?: string;
  version: string;
  description?: string;
  downloadUrl?: string;
}) {
  const token = localStorage.getItem("token") || "";
  const res = await fetch(`${baseUrl}/v1/version`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  });
  return res.json();
}
```

### axios

```ts
import axios from "axios";

const http = axios.create({
  baseURL: "https://server.pyisland.com/api",
});

export async function getVersionList() {
  const { data } = await http.get("/v1/version/list");
  if (data.code !== 200) throw new Error(data.message);
  return data.data;
}

export async function createVersion(payload: {
  appName: string;
  version: string;
  description?: string;
  downloadUrl?: string;
}) {
  const token = localStorage.getItem("token") || "";
  const { data } = await http.post("/v1/version", payload, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (data.code !== 200) throw new Error(data.message);
  return data.data;
}
```

## 创建应用版本

- **Method**: `POST`
- **Path**: `/api/v1/version`
- **Auth**: 是
- **Content-Type**: `application/json`

### 请求体

```json
{
  "appName": "pyisland",
  "version": "26.1.0",
  "description": "版本说明",
  "downloadUrl": "https://example.com/download.exe"
}
```

### 响应

成功：

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

参数错误：

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

已存在：

```json
{
  "code": 409,
  "message": "该应用已存在"
}
```

## 更新应用版本

- **Method**: `PUT`
- **Path**: `/api/v1/version`
- **Auth**: 是
- **Content-Type**: `application/json`

### 请求体

```json
{
  "appName": "pyisland",
  "version": "26.1.1",
  "description": "新版说明",
  "downloadUrl": "https://example.com/new.exe"
}
```

> `appName` 为空时后端默认使用 `pyisland`。

### 响应

成功：

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

常见失败：

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

## 删除应用版本

- **Method**: `DELETE`
- **Path**: `/api/v1/version?appName={appName}`
- **Auth**: 是

### 响应

成功：

```json
{
  "code": 200,
  "message": "版本信息删除成功"
}
```

不存在：

```json
{
  "code": 404,
  "message": "版本信息不存在"
}
```
