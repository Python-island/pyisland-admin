# 认证接口

> 路径前缀：`/api/auth`

## 管理员登录

- **Method**: `POST`
- **Path**: `/api/auth/login`
- **Auth**: 否
- **Content-Type**: `application/json`

### 请求体

```json
{
  "username": "admin",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `string` | 是 | 管理员用户名 |
| `password` | `string` | 是 | 管理员密码 |

### 成功响应

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "<jwt_token>",
    "username": "admin"
  }
}
```

### 失败响应

用户名或密码为空：

```json
{
  "code": 400,
  "message": "用户名和密码不能为空"
}
```

用户名或密码错误：

```json
{
  "code": 401,
  "message": "用户名或密码错误"
}
```

## 前端调用示例

### fetch

```ts
const baseUrl = "https://server.pyisland.com/api";

export async function login(username: string, password: string) {
  const res = await fetch(`${baseUrl}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });

  const json = await res.json();
  if (json.code !== 200) throw new Error(json.message);

  localStorage.setItem("token", json.data.token);
  return json.data;
}
```

### axios

```ts
import axios from "axios";

const http = axios.create({
  baseURL: "https://server.pyisland.com/api",
  headers: { "Content-Type": "application/json" },
});

export async function login(username: string, password: string) {
  const { data } = await http.post("/auth/login", { username, password });
  if (data.code !== 200) throw new Error(data.message);

  localStorage.setItem("token", data.data.token);
  return data.data;
}
```
