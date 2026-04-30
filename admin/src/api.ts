/**
 * @file api.ts
 * @description 前端统一 API 请求封装与业务接口定义。
 * @description 提供鉴权、版本管理、用户管理、接口状态管理及头像上传能力。
 * @author 鸡哥
 */

import { showKickedModal } from "./modal";

const DEV_API_BASE = "https://test.server.pyisland.com/api";
const BASE = import.meta.env.VITE_API_BASE || (import.meta.env.DEV ? DEV_API_BASE : "/api");

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
  role: "admin" | "pro" | "user";
}

export interface AdminUserInfo {
  id: number;
  username: string;
  avatar: string | null;
  createdAt: string;
}

export interface DailyActivePoint {
  date: string;
  count: number;
}

export interface DailyActiveStats {
  today: number;
  days: number;
  series: DailyActivePoint[];
}

export type Gender = "male" | "female" | "custom" | "undisclosed";

export interface AppUserInfo {
  id: number;
  username: string;
  email: string;
  role?: "admin" | "pro" | "user";
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
  balanceFen?: number;
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

export interface WeatherQuotaStatus {
  provider: string;
  month: string;
  limit: number;
  used: number;
  remaining: number;
  fused: boolean;
}

export interface WallpaperAdminItem {
  id: number;
  ownerUsername: string;
  title: string;
  description: string;
  type: string;
  status: string;
  tagsText: string;
  copyrightInfo?: string;
  originalUrl: string;
  thumb320Url?: string;
  thumb720Url?: string;
  thumb1280Url?: string;
  durationMs?: number;
  frameRate?: number;
  ratingAvg?: number;
  ratingCount?: number;
  downloadCount?: number;
  applyCount?: number;
  createdAt?: string;
  updatedAt?: string;
  publishedAt?: string | null;
}

export interface WallpaperReportItem {
  id: number;
  wallpaperId: number;
  reporterUsername: string;
  reasonType: string;
  reasonDetail: string;
  status: string;
  resolutionNote?: string;
  resolvedBy?: string;
  createdAt?: string;
  resolvedAt?: string;
}

export interface WallpaperRatingItem {
  id: number;
  wallpaperId: number;
  username: string;
  score: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface PaymentTestOrderData {
  outTradeNo: string;
  productCode: string;
  amountFen: number;
  currency: string;
  status: string;
  channel: string;
  qrCodeUrl?: string;
  payUrl?: string;
  expireAt?: string;
  paidAt?: string;
}

export interface PaymentDlqAdminItem {
  id: number;
  notifyId?: string;
  outTradeNo?: string;
  tradeState?: string;
  retryCount?: number;
  errorMessage?: string;
  rawBody?: string;
  createdAt?: string;
}

export interface EmailDlqAdminItem {
  id: number;
  traceId?: string;
  email?: string;
  scene?: string;
  retryCount?: number;
  errorMessage?: string;
  createdAt?: string;
}

export interface IssueFeedbackAdminItem {
  id: number;
  username: string;
  feedbackType: string;
  title: string;
  content: string;
  contact?: string;
  feedbackLogUrl?: string;
  clientVersion?: string;
  status: string;
  adminReply?: string;
  createdAt?: string;
  updatedAt?: string;
  resolvedAt?: string;
}

export interface IssueFeedbackAdminListResponse {
  items: IssueFeedbackAdminItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface PaymentConfigData {
  enabled: boolean;
  mchId: string;
  appId: string;
  serialNo: string;
  notifyUrl: string;
  publicKeyId: string;
  publicKeyPath: string;
  platformCertPath: string;
  privateKeyPath: string;
  apiV3KeyMasked: string;
  alipayEnabled: boolean;
  alipayGatewayUrl: string;
  alipayAppId: string;
  alipayNotifyUrl: string;
  alipayPrivateKeyPath: string;
  alipayPublicKeyPath: string;
  alipaySignType: string;
  alipayCharset: string;
  alipayQueryPendingBatchSize: number;
  proMonthAmountFen: number;
  freeDesc: string;
  freeFeatures: string[];
  proDesc: string;
  proFeatures: string[];
  orderExpireMinutes: number;
  queryPendingBatchSize: number;
}

export interface AnnouncementConfigData {
  id: number;
  title: string;
  content: string;
  enabled: boolean;
  startAt?: string | null;
  endAt?: string | null;
  updatedBy?: string;
  updatedAt?: string | null;
}

export interface AnnouncementConfigUpdatePayload {
  title: string;
  content: string;
  enabled: boolean;
  startAt?: string | null;
  endAt?: string | null;
}

export interface PaymentOrderAdminItem {
  id: number;
  outTradeNo: string;
  username: string;
  productCode: string;
  amountFen: number;
  currency: string;
  status: string;
  channel?: "WECHAT" | "ALIPAY";
  wxPrepayId?: string;
  wxCodeUrl?: string;
  wxTransactionId?: string;
  expireAt?: string;
  paidAt?: string;
  closedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PaymentConfigUpdatePayload {
  enabled?: boolean;
  mchId?: string;
  appId?: string;
  apiV3Key?: string;
  privateKeyPath?: string;
  serialNo?: string;
  notifyUrl?: string;
  publicKeyId?: string;
  publicKeyPath?: string;
  platformCertPath?: string;
  alipayEnabled?: boolean;
  alipayGatewayUrl?: string;
  alipayAppId?: string;
  alipayNotifyUrl?: string;
  alipayPrivateKeyPath?: string;
  alipayPublicKeyPath?: string;
  alipaySignType?: string;
  alipayCharset?: string;
  alipayQueryPendingBatchSize?: number;
  proMonthAmountFen?: number;
  freeDesc?: string;
  freeFeatures?: string[];
  proDesc?: string;
  proFeatures?: string[];
  orderExpireMinutes?: number;
  queryPendingBatchSize?: number;
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
  rotateTotpSeed(username: string) {
    return request<ApiResponse>(
      `/v1/admin-users/totp-seed/rotate?username=${encodeURIComponent(username)}`,
      { method: "POST" }
    );
  },
};

export const wallpaperAdmin = {
  list(params?: {
    keyword?: string;
    type?: string;
    status?: string;
    page?: number;
    pageSize?: number;
  }) {
    const query = new URLSearchParams();
    if (params?.keyword) query.set("keyword", params.keyword);
    if (params?.type) query.set("type", params.type);
    if (params?.status) query.set("status", params.status);
    if (params?.page) query.set("page", String(params.page));
    if (params?.pageSize) query.set("pageSize", String(params.pageSize));
    const qs = query.toString();
    return request<ApiResponse<WallpaperAdminItem[]>>(`/v1/admin/wallpapers/list${qs ? `?${qs}` : ""}`);
  },
  updateMetadata(payload: {
    id: number;
    title: string;
    description: string;
    type: string;
    tags: string;
    copyrightInfo?: string;
    status: string;
  }) {
    return request<ApiResponse>("/v1/admin/wallpapers/metadata", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
  review(payload: { id: number; action: "approve" | "reject" | "delist" | "relist"; reason: string }) {
    return request<ApiResponse>("/v1/admin/wallpapers/review", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
  reports(params?: { status?: string; page?: number; pageSize?: number }) {
    const query = new URLSearchParams();
    if (params?.status) query.set("status", params.status);
    if (params?.page) query.set("page", String(params.page));
    if (params?.pageSize) query.set("pageSize", String(params.pageSize));
    const qs = query.toString();
    return request<ApiResponse<WallpaperReportItem[]>>(`/v1/admin/wallpapers/reports${qs ? `?${qs}` : ""}`);
  },
  resolveReport(payload: { id: number; status: string; resolutionNote: string }) {
    return request<ApiResponse>("/v1/admin/wallpapers/reports/resolve", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
  ratings(wallpaperId: number, page = 1, pageSize = 20) {
    const query = new URLSearchParams();
    query.set("wallpaperId", String(wallpaperId));
    query.set("page", String(page));
    query.set("pageSize", String(pageSize));
    return request<ApiResponse<WallpaperRatingItem[]>>(`/v1/admin/wallpapers/ratings?${query.toString()}`);
  },
  deleteRating(id: number, wallpaperId: number) {
    return request<ApiResponse>(
      `/v1/admin/wallpapers/ratings?id=${encodeURIComponent(String(id))}&wallpaperId=${encodeURIComponent(String(wallpaperId))}`,
      { method: "DELETE" }
    );
  },
  delete(id: number) {
    return request<ApiResponse>(
      `/v1/admin/wallpapers/delete?id=${encodeURIComponent(String(id))}`,
      { method: "DELETE" }
    );
  },
};

export interface WallpaperTagAdminItem {
  id: number;
  name: string;
  slug: string;
  creatorUsername?: string;
  enabled: number | boolean;
  usageCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface WallpaperTagAdminListResponse {
  items: WallpaperTagAdminItem[];
  total: number;
}

export const wallpaperTagAdmin = {
  list(params?: { keyword?: string; enabled?: number; page?: number; pageSize?: number }) {
    const query = new URLSearchParams();
    if (params?.keyword) query.set("keyword", params.keyword);
    if (params?.enabled !== undefined) query.set("enabled", String(params.enabled));
    if (params?.page) query.set("page", String(params.page));
    if (params?.pageSize) query.set("pageSize", String(params.pageSize));
    const qs = query.toString();
    return request<ApiResponse<WallpaperTagAdminListResponse>>(`/v1/admin/tags/list${qs ? `?${qs}` : ""}`);
  },
  updateName(id: number, name: string) {
    return request<ApiResponse>("/v1/admin/tags/update", {
      method: "PUT",
      body: JSON.stringify({ id, name }),
    });
  },
  setEnabled(id: number, enabled: boolean) {
    return request<ApiResponse>("/v1/admin/tags/enable", {
      method: "PUT",
      body: JSON.stringify({ id, enabled }),
    });
  },
  deleteTag(id: number) {
    return request<ApiResponse>(`/v1/admin/tags/delete?id=${encodeURIComponent(String(id))}`, {
      method: "DELETE",
    });
  },
};

export const issueFeedbackAdmin = {
  list(params?: {
    status?: string;
    keyword?: string;
    page?: number;
    pageSize?: number;
  }) {
    const query = new URLSearchParams();
    if (params?.status) query.set("status", params.status);
    if (params?.keyword) query.set("keyword", params.keyword);
    if (params?.page) query.set("page", String(params.page));
    if (params?.pageSize) query.set("pageSize", String(params.pageSize));
    const qs = query.toString();
    return request<ApiResponse<IssueFeedbackAdminListResponse>>(
      `/v1/admin/feedback${qs ? `?${qs}` : ""}`
    );
  },
  resolve(payload: { id: number; status: string; adminReply?: string }) {
    return request<ApiResponse>("/v1/admin/feedback/resolve", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
};

export const announcementAdmin = {
  getConfig() {
    return request<ApiResponse<AnnouncementConfigData>>("/v1/admin/announcement");
  },
  updateConfig(payload: AnnouncementConfigUpdatePayload) {
    return request<ApiResponse<AnnouncementConfigData>>("/v1/admin/announcement", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
};

export const paymentAdmin = {
  getConfig() {
    return request<ApiResponse<PaymentConfigData>>("/v1/admin/payment/config");
  },
  updateConfig(payload: PaymentConfigUpdatePayload) {
    return request<ApiResponse>("/v1/admin/payment/config", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
  listOrders(params?: { username?: string; status?: string; channel?: "WECHAT" | "ALIPAY"; limit?: number }) {
    const query = new URLSearchParams();
    if (params?.username) query.set("username", params.username);
    if (params?.status) query.set("status", params.status);
    if (params?.channel) query.set("channel", params.channel);
    if (params?.limit) query.set("limit", String(params.limit));
    const qs = query.toString();
    return request<ApiResponse<PaymentOrderAdminItem[]>>(
      `/v1/admin/payment/orders${qs ? `?${qs}` : ""}`
    );
  },
  refreshOrder(outTradeNo: string) {
    return request<ApiResponse<PaymentOrderAdminItem>>("/v1/admin/payment/orders/refresh", {
      method: "PUT",
      body: JSON.stringify({ outTradeNo }),
    });
  },
  closeOrder(outTradeNo: string) {
    return request<ApiResponse>("/v1/admin/payment/orders/close", {
      method: "PUT",
      body: JSON.stringify({ outTradeNo }),
    });
  },
  createTestOrder(payload: { channel: "WECHAT" | "ALIPAY"; amountFen: number; subject?: string }) {
    return request<ApiResponse<PaymentTestOrderData>>("/v1/admin/payment/orders/test", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
  listDlq(params?: { notifyId?: string; outTradeNo?: string; limit?: number }) {
    const query = new URLSearchParams();
    if (params?.notifyId) query.set("notifyId", params.notifyId);
    if (params?.outTradeNo) query.set("outTradeNo", params.outTradeNo);
    if (params?.limit) query.set("limit", String(params.limit));
    const qs = query.toString();
    return request<ApiResponse<PaymentDlqAdminItem[]>>(
      `/v1/admin/payment/notify-dlq${qs ? `?${qs}` : ""}`
    );
  },
};

export const emailAdmin = {
  listDlq(params?: { traceId?: string; email?: string; limit?: number }) {
    const query = new URLSearchParams();
    if (params?.traceId) query.set("traceId", params.traceId);
    if (params?.email) query.set("email", params.email);
    if (params?.limit) query.set("limit", String(params.limit));
    const qs = query.toString();
    return request<ApiResponse<EmailDlqAdminItem[]>>(
      `/v1/admin/email/notify-dlq${qs ? `?${qs}` : ""}`
    );
  },
};

export const weatherAdmin = {
  quota() {
    return request<ApiResponse<WeatherQuotaStatus>>("/v1/admin/weather/quota");
  },
};

export const appUsers = {
  list() {
    return request<ApiResponse<AppUserInfo[]>>("/v1/app-users");
  },
  count() {
    return request<ApiResponse<number>>("/v1/app-users/count");
  },
  dailyActive(days = 7) {
    return request<ApiResponse<DailyActiveStats>>(
      `/v1/app-users/daily-active?days=${encodeURIComponent(String(days))}`
    );
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

  updateBalance(username: string, balanceFen: number) {
    return request<ApiResponse>("/v1/app-users/balance", {
      method: "PUT",
      body: JSON.stringify({ username, balanceFen }),
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

/**
 * 统一用户管理视图的数据结构（合表后）。
 */
export interface UserAccountItem {
  id: number;
  username: string;
  email: string;
  role: "admin" | "pro" | "user";
  avatar: string | null;
  gender?: Gender;
  genderCustom?: string | null;
  birthday?: string | null;
  enabled: boolean;
  banned?: boolean;
  createdAt: string;
}

/**
 * 合表后的统一用户管理接口（`/v1/admin/users`）。
 * 旧 `adminUsers` / `appUsers` 仍保留，便于按角色分区展示。
 */
export const userAccounts = {
  /**
   * 列表查询，可按 role 过滤（空表示全部）。
   * @param role - 可选角色过滤。
   */
  list(role?: "admin" | "pro" | "user" | "") {
    const qs = role ? `?role=${encodeURIComponent(role)}` : "";
    return request<ApiResponse<UserAccountItem[]>>(`/v1/admin/users${qs}`);
  },
  /**
   * 更新角色（admin/pro/user 切换，切换后对方会被强制重新登录）。
   */
  updateRole(username: string, role: "admin" | "pro" | "user") {
    return request<ApiResponse>("/v1/admin/users/role", {
      method: "PUT",
      body: JSON.stringify({ username, role }),
    });
  },
  /**
   * 启用或禁用账号。禁用会同时清空 session_token。
   */
  updateEnabled(username: string, enabled: boolean) {
    return request<ApiResponse>("/v1/admin/users/enabled", {
      method: "PUT",
      body: JSON.stringify({ username, enabled }),
    });
  },
  /**
   * 封禁或解封账号。
   */
  updateBan(username: string, banned: boolean) {
    return request<ApiResponse>("/v1/admin/users/ban", {
      method: "PUT",
      body: JSON.stringify({ username, banned }),
    });
  },
};

export interface AgentModelPricingItem {
  id: number;
  modelName: string;
  inputPriceFenPerMillion: number;
  outputPriceFenPerMillion: number;
  enabled: boolean;
  updatedAt: string;
}

export const agentAdmin = {
  listModelPricing() {
    return request<ApiResponse<AgentModelPricingItem[]>>("/v1/admin/agent/model-pricing");
  },
  upsertModelPricing(payload: {
    modelName: string;
    inputPriceFenPerMillion: number;
    outputPriceFenPerMillion: number;
    enabled: boolean;
  }) {
    return request<ApiResponse>("/v1/admin/agent/model-pricing", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
  deleteModelPricing(modelName: string) {
    return request<ApiResponse>(
      `/v1/admin/agent/model-pricing?modelName=${encodeURIComponent(modelName)}`,
      { method: "DELETE" }
    );
  },
  getServiceEnabled() {
    return request<ApiResponse<{ enabled: boolean; statusMessage: string }>>(
      "/v1/admin/agent/service-enabled"
    );
  },
  setServiceEnabled(enabled: boolean, message?: string) {
    return request<ApiResponse>("/v1/admin/agent/service-enabled", {
      method: "PUT",
      body: JSON.stringify({ enabled, message: message ?? "" }),
    });
  },
};
