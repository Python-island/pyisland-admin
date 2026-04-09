<template>
  <div class="docs-login-card">
    <label class="docs-login-label">用户名</label>
    <input
      v-model="username"
      class="docs-login-input"
      type="text"
      placeholder="请输入管理员用户名"
      autocomplete="username"
    />

    <label class="docs-login-label">密码</label>
    <input
      v-model="password"
      class="docs-login-input"
      type="password"
      placeholder="请输入管理员密码"
      autocomplete="current-password"
      @keydown.enter="submit"
    />

    <button class="docs-login-btn" :disabled="loading" @click="submit">
      {{ loading ? "登录中..." : "登录文档站" }}
    </button>

    <p v-if="error" class="docs-login-error">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

const API_BASE_URL = "https://server.pyisland.com/api";
const DOCS_TOKEN_KEY = "docs_admin_token";

const username = ref("");
const password = ref("");
const loading = ref(false);
const error = ref("");

function getRedirectTarget(): string {
  const query = new URLSearchParams(window.location.search);
  const redirect = query.get("redirect");
  return redirect && redirect.startsWith("/") ? redirect : "/";
}

async function submit() {
  if (!username.value || !password.value) {
    error.value = "用户名和密码不能为空";
    return;
  }

  loading.value = true;
  error.value = "";

  try {
    const res = await fetch(`${API_BASE_URL}/auth/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        username: username.value,
        password: password.value,
      }),
    });

    const json = (await res.json()) as {
      code?: number;
      message?: string;
      data?: { token?: string };
    };

    if (json.code !== 200 || !json.data?.token) {
      throw new Error(json.message || "登录失败");
    }

    localStorage.setItem(DOCS_TOKEN_KEY, json.data.token);
    window.location.href = getRedirectTarget();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "登录失败";
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.docs-login-card {
  width: 100%;
  max-width: 440px;
  margin: 24px auto 0;
  padding: 20px;
  border: 1px solid var(--vp-c-border);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.docs-login-label {
  font-size: 14px;
  font-weight: 600;
}

.docs-login-input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--vp-c-border);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
  background: var(--vp-c-bg);
  color: var(--vp-c-text);
}

.docs-login-btn {
  width: 100%;
  box-sizing: border-box;
  border: none;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  background: var(--vp-c-accent);
  color: #fff;
}

.docs-login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.docs-login-error {
  margin: 0;
  color: #e53935;
  font-size: 13px;
}
</style>
