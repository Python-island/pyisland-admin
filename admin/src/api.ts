const BASE = "/api";

function getToken(): string | null {
  return localStorage.getItem("token");
}

export function setToken(token: string) {
  localStorage.setItem("token", token);
}

export function clearToken() {
  localStorage.removeItem("token");
  localStorage.removeItem("username");
}

export function isLoggedIn(): boolean {
  return !!getToken();
}

export function getUsername(): string {
  return localStorage.getItem("username") || "";
}

export function setUsername(username: string) {
  localStorage.setItem("username", username);
}

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${BASE}${path}`, { ...options, headers });

  if (res.status === 401) {
    clearToken();
    window.location.href = "/login";
    throw new Error("未登录或token已过期");
  }

  return res.json();
}

export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data?: T;
}

export interface AppVersion {
  id: number;
  appName: string;
  version: string;
  description: string;
  updatedAt: string;
}

export interface LoginData {
  token: string;
  username: string;
}

export const auth = {
  login(username: string, password: string) {
    return request<ApiResponse<LoginData>>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  },
  register(username: string, password: string) {
    return request<ApiResponse>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  },
};

export const version = {
  get(appName: string) {
    return request<ApiResponse<AppVersion>>(
      `/v1/version?appName=${encodeURIComponent(appName)}`
    );
  },
  create(appName: string, ver: string, description: string) {
    return request<ApiResponse<AppVersion>>("/v1/version", {
      method: "POST",
      body: JSON.stringify({ appName, version: ver, description }),
    });
  },
  update(appName: string, ver: string, description: string) {
    return request<ApiResponse<AppVersion>>("/v1/version", {
      method: "PUT",
      body: JSON.stringify({ appName, version: ver, description }),
    });
  },
  delete(appName: string) {
    return request<ApiResponse>(
      `/v1/version?appName=${encodeURIComponent(appName)}`,
      { method: "DELETE" }
    );
  },
};
