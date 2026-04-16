/**
 * @file api.ts
 * @description 前端统一 API 请求封装与业务接口定义。
 * @description 提供鉴权、版本管理、用户管理、接口状态管理及头像上传能力。
 * @author 鸡哥
 */

import { showKickedModal } from "./modal";

const BASE = import.meta.env.VITE_API_BASE || "/api";

/**
 * 过滤并校验可用于展示的头像 URL。
 * @param url - 原始 URL 字符串。
 * @returns 合法 URL 或 null。
 */
export function sanitizeUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    if (parsed.protocol === "https:" || parsed.protocol === "http:") return url;
  } catch { /* invalid URL */ }
  if (url.startsWith("data:image/")) return url;
  return null;
}

function getToken(): string | null {
  return localStorage.getItem("token");
}

/**
 * 持久化登录 token。
 * @param token - 登录成功返回的 JWT token。
 */
export function setToken(token: string) {
  localStorage.setItem("token", token);
}

/**
 * 清理当前登录态。
 */
export function clearToken() {
  localStorage.removeItem("token");
  localStorage.removeItem("username");
}

/**
 * 判断当前是否已登录。
 * @returns 是否存在可用 token。
 */
export function isLoggedIn(): boolean {
  return !!getToken();
}

/**
 * 获取当前登录用户名。
 * @returns 用户名；未登录时返回空字符串。
 */
export function getUsername(): string {
  return localStorage.getItem("username") || "";
}

/**
 * 持久化当前登录用户名。
 * @param username - 当前用户名称。
 */
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
    const body = await res.json().catch(() => null);
    clearToken();
    if (body?.code === 4011) {
      showKickedModal(() => { window.location.href = "/login"; });
      throw new Error(body?.message || "账号已在其他设备登录");
    }
    window.location.href = "/login";
    throw new Error(body?.message || "未登录或token已过期");
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
  downloadUrl: string;
  updateCount: number;
  updatedAt: string;
}

export interface LoginData {
  token: string;
  username: string;
  role: "admin" | "user";
}

export interface AdminUserInfo {
  id: number;
  username: string;
  avatar: string | null;
  createdAt: string;
}

export type Gender = "male" | "female" | "custom" | "undisclosed";

export interface AppUserInfo {
  id: number;
  username: string;
  email: string;
  avatar: string | null;
  gender?: Gender;
  genderCustom?: string | null;
  birthday?: string | null;
  createdAt: string;
}

export interface AppUserProfileData {
  username: string;
  email: string;
  avatar: string | null;
  gender?: Gender;
  genderCustom?: string | null;
  birthday?: string | null;
  createdAt: string;
}

export interface ProfileData {
  username: string;
  email?: string;
  avatar: string | null;
  createdAt: string;
}

export interface ApiStatus {
  id: number;
  apiName: string;
  status: boolean;
  message: string;
  remark: string;
  updatedAt: string;
}

export const auth = {
  adminLogin(username: string, password: string) {
    return request<ApiResponse<LoginData>>("/auth/admin/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  },
  userLogin(username: string, password: string) {
    return request<ApiResponse<LoginData>>("/auth/user/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  },
  adminRegister(username: string, password: string) {
    return request<ApiResponse>("/auth/admin/register", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  },
  userRegister(
    username: string,
    email: string,
    password: string,
    extras?: { gender?: Gender; genderCustom?: string | null; birthday?: string | null }
  ) {
    return request<ApiResponse>("/auth/user/register", {
      method: "POST",
      body: JSON.stringify({
        username,
        email,
        password,
        gender: extras?.gender,
        genderCustom: extras?.genderCustom,
        birthday: extras?.birthday,
      }),
    });
  },
  login(username: string, password: string) {
    return this.adminLogin(username, password);
  },
};

export const version = {
  list() {
    return request<ApiResponse<AppVersion[]>>("/v1/version/list");
  },
  get(appName: string) {
    return request<ApiResponse<AppVersion>>(
      `/v1/version?appName=${encodeURIComponent(appName)}`
    );
  },
  create(appName: string, ver: string, description: string, downloadUrl: string) {
    return request<ApiResponse<AppVersion>>("/v1/version", {
      method: "POST",
      body: JSON.stringify({ appName, version: ver, description, downloadUrl }),
    });
  },
  update(appName: string, ver: string, description: string, downloadUrl: string) {
    return request<ApiResponse<AppVersion>>("/v1/version", {
      method: "PUT",
      body: JSON.stringify({ appName, version: ver, description, downloadUrl }),
    });
  },
  delete(appName: string) {
    return request<ApiResponse>(
      `/v1/version?appName=${encodeURIComponent(appName)}`,
      { method: "DELETE" }
    );
  },
};

/**
 * 上传管理员头像文件（OSS）。
 * @param file - 待上传的头像文件。
 * @returns 上传接口响应，成功时 data 为头像 URL。
 */
export async function uploadAdminAvatar(file: File): Promise<ApiResponse<string>> {
  const token = getToken();
  const formData = new FormData();
  formData.append("file", file);
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(`${BASE}/v1/upload/admin-avatar`, {
    method: "POST",
    headers,
    body: formData,
  });
  if (res.status === 401) {
    const body = await res.json().catch(() => null);
    clearToken();
    if (body?.code === 4011) {
      showKickedModal(() => { window.location.href = "/login"; });
      throw new Error(body?.message || "账号已在其他设备登录");
    }
    window.location.href = "/login";
    throw new Error(body?.message || "未登录或token已过期");
  }
  return res.json();
}

/**
 * 上传普通用户头像文件（R2）。
 * @param file - 待上传的头像文件。
 * @returns 上传接口响应，成功时 data 为头像 URL。
 */
export async function uploadUserAvatar(file: File): Promise<ApiResponse<string>> {
  const token = getToken();
  const formData = new FormData();
  formData.append("file", file);
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(`${BASE}/v1/upload/user-avatar`, {
    method: "POST",
    headers,
    body: formData,
  });
  if (res.status === 401) {
    const body = await res.json().catch(() => null);
    clearToken();
    if (body?.code === 4011) {
      showKickedModal(() => { window.location.href = "/login"; });
      throw new Error(body?.message || "账号已在其他设备登录");
    }
    window.location.href = "/login";
    throw new Error(body?.message || "未登录或token已过期");
  }
  return res.json();
}

export const adminUsers = {
  list() {
    return request<ApiResponse<AdminUserInfo[]>>("/v1/admin-users");
  },
  count() {
    return request<ApiResponse<number>>("/v1/admin-users/count");
  },
  add(username: string, password: string) {
    return request<ApiResponse>("/v1/admin-users", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  },
  delete(username: string) {
    return request<ApiResponse>(
      `/v1/admin-users?username=${encodeURIComponent(username)}`,
      { method: "DELETE" }
    );
  },
  getProfile(username: string) {
    return request<ApiResponse<ProfileData>>(
      `/v1/admin-users/profile?username=${encodeURIComponent(username)}`
    );
  },
  updateProfile(username: string, password: string | null, avatar: string | null) {
    return request<ApiResponse>("/v1/admin-users/profile", {
      method: "PUT",
      body: JSON.stringify({ username, password, avatar }),
    });
  },
};

export const appUsers = {
  list() {
    return request<ApiResponse<AppUserInfo[]>>("/v1/app-users");
  },
  count() {
    return request<ApiResponse<number>>("/v1/app-users/count");
  },
  add(
    username: string,
    email: string,
    password: string,
    extras?: { gender?: Gender; genderCustom?: string | null; birthday?: string | null }
  ) {
    return request<ApiResponse>("/v1/app-users", {
      method: "POST",
      body: JSON.stringify({
        username,
        email,
        password,
        gender: extras?.gender,
        genderCustom: extras?.genderCustom,
        birthday: extras?.birthday,
      }),
    });
  },
  delete(username: string) {
    return request<ApiResponse>(
      `/v1/app-users?username=${encodeURIComponent(username)}`,
      { method: "DELETE" }
    );
  },
  getProfile(username: string) {
    return request<ApiResponse<AppUserProfileData>>(
      `/v1/app-users/profile?username=${encodeURIComponent(username)}`
    );
  },
  updateProfile(
    username: string,
    password: string | null,
    avatar: string | null,
    extras?: { gender?: Gender; genderCustom?: string | null; birthday?: string | null }
  ) {
    return request<ApiResponse>("/v1/app-users/profile", {
      method: "PUT",
      body: JSON.stringify({
        username,
        password,
        avatar,
        gender: extras?.gender,
        genderCustom: extras?.genderCustom,
        birthday: extras?.birthday,
      }),
    });
  },
};

export const apiStatus = {
  list() {
    return request<ApiResponse<ApiStatus[]>>("/v1/service-status/list");
  },
  update(apiName: string, status: boolean, message: string, remark: string) {
    return request<ApiResponse>("/v1/service-status", {
      method: "PUT",
      body: JSON.stringify({ apiName, status, message, remark }),
    });
  },
};
