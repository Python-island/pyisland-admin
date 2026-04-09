# 用户管理接口

> 路径前缀：`/api/v1/users`
> 
> 认证：本页全部接口均需要 `Bearer Token`

## 查询管理员列表

- **Method**: `GET`
- **Path**: `/api/v1/users`

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "username": "admin",
      "avatar": "https://...",
      "createdAt": "2026-04-09T12:00:00"
    }
  ]
}
```

## 查询管理员数量

- **Method**: `GET`
- **Path**: `/api/v1/users/count`

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": 1
}
```

## 新增管理员

- **Method**: `POST`
- **Path**: `/api/v1/users`
- **Content-Type**: `application/json`

### 请求体

```json
{
  "username": "new_admin",
  "password": "123456"
}
```

### 响应

成功：

```json
{
  "code": 200,
  "message": "添加成功"
}
```

参数缺失：

```json
{
  "code": 400,
  "message": "用户名和密码不能为空"
}
```

用户已存在：

```json
{
  "code": 409,
  "message": "用户名已存在"
}
```

## 删除管理员

- **Method**: `DELETE`
- **Path**: `/api/v1/users?username={username}`

### Query 参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `string` | 是 | 用户名 |

### 响应

成功：

```json
{
  "code": 200,
  "message": "删除成功"
}
```

用户不存在：

```json
{
  "code": 404,
  "message": "用户不存在"
}
```

## 前端调用示例

### fetch

```ts
const baseUrl = "https://server.pyisland.com/api";

function authHeaders() {
  const token = localStorage.getItem("token") || "";
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };
}

export async function getUsers() {
  const res = await fetch(`${baseUrl}/v1/users`, {
    method: "GET",
    headers: authHeaders(),
  });
  const json = await res.json();
  if (json.code !== 200) throw new Error(json.message);
  return json.data;
}

export async function createUser(username: string, password: string) {
  const res = await fetch(`${baseUrl}/v1/users`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ username, password }),
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

http.interceptors.request.use(config => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export async function getUsers() {
  const { data } = await http.get("/v1/users");
  if (data.code !== 200) throw new Error(data.message);
  return data.data;
}

export async function updateProfile(payload: {
  username: string;
  password?: string;
  avatar?: string;
}) {
  const { data } = await http.put("/v1/users/profile", payload);
  if (data.code !== 200) throw new Error(data.message);
  return data;
}
```

## 查询管理员资料

- **Method**: `GET`
- **Path**: `/api/v1/users/profile?username={username}`

### 响应

成功：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "username": "admin",
    "avatar": "https://...",
    "createdAt": "2026-04-09T12:00:00"
  }
}
```

用户不存在：

```json
{
  "code": 404,
  "message": "用户不存在"
}
```

## 更新管理员资料

- **Method**: `PUT`
- **Path**: `/api/v1/users/profile`
- **Content-Type**: `application/json`

### 请求体

```json
{
  "username": "admin",
  "password": "new_password",
  "avatar": "https://example.com/avatar.png"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `string` | 是 | 用户名 |
| `password` | `string` | 否 | 新密码；为空时仅更新头像 |
| `avatar` | `string` | 否 | 头像 URL |

### 响应

成功：

```json
{
  "code": 200,
  "message": "更新成功"
}
```

用户名为空：

```json
{
  "code": 400,
  "message": "用户名不能为空"
}
```

用户不存在：

```json
{
  "code": 404,
  "message": "用户不存在"
}
```
