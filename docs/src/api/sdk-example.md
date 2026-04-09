# 前端 SDK 示例

本页提供一个可直接复用的前端 API SDK 基础实现，用于统一处理：

- `baseURL`
- `Bearer Token`
- 统一业务错误处理（`code !== 200`）

## fetch 版本

```ts
const API_BASE_URL = "https://server.pyisland.com/api";

type ApiResponse<T = unknown> = {
  code: number;
  message: string;
  data?: T;
};

function getToken(): string {
  return localStorage.getItem("token") || "";
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  needAuth = false
): Promise<T> {
  const headers: Record<string, string> = {
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(options.headers as Record<string, string>),
  };

  if (needAuth) {
    headers.Authorization = `Bearer ${getToken()}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  const json = (await response.json()) as ApiResponse<T>;

  if (json.code !== 200) {
    throw new Error(json.message || "请求失败");
  }

  return json.data as T;
}

export const apiFetch = {
  login: (username: string, password: string) =>
    request<{ token: string; username: string }>(
      "/auth/login",
      {
        method: "POST",
        body: JSON.stringify({ username, password }),
      },
      false
    ),

  getVersionList: () => request<any[]>("/v1/version/list"),

  getUsers: () => request<any[]>("/v1/users", { method: "GET" }, true),

  uploadAvatar: async (file: File) => {
    const formData = new FormData();
    formData.append("file", file);

    const response = await fetch(`${API_BASE_URL}/v1/upload/avatar`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${getToken()}`,
      },
      body: formData,
    });

    const json = (await response.json()) as ApiResponse<string>;
    if (json.code !== 200) {
      throw new Error(json.message || "上传失败");
    }

    return json.data as string;
  },
};
```

## axios 版本

```ts
import axios from "axios";

type ApiResponse<T = unknown> = {
  code: number;
  message: string;
  data?: T;
};

const http = axios.create({
  baseURL: "https://server.pyisland.com/api",
  timeout: 15000,
});

http.interceptors.request.use(config => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

function unwrap<T>(res: { data: ApiResponse<T> }): T {
  if (res.data.code !== 200) {
    throw new Error(res.data.message || "请求失败");
  }
  return res.data.data as T;
}

export const apiAxios = {
  async login(username: string, password: string) {
    const res = await http.post<ApiResponse<{ token: string; username: string }>>(
      "/auth/login",
      { username, password }
    );
    return unwrap<{ token: string; username: string }>({ data: res.data });
  },

  async getVersionList() {
    const res = await http.get<ApiResponse<any[]>>("/v1/version/list");
    return unwrap<any[]>({ data: res.data });
  },

  async getUsers() {
    const res = await http.get<ApiResponse<any[]>>("/v1/users");
    return unwrap<any[]>({ data: res.data });
  },
};
```

## 使用建议

- 生产环境建议将 `API_BASE_URL` 迁移到环境变量。
- `401/4011` 可在拦截器中统一跳转登录页。
- 文件上传建议增加客户端大小与类型校验。
