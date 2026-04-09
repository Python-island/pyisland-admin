import { defineClientConfig } from "@vuepress/client";

const API_BASE_URL = "https://server.pyisland.com/api";
const DOCS_TOKEN_KEY = "docs_admin_token";

let pendingVerify: Promise<boolean> | null = null;

async function verifyAdminToken(token: string): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE_URL}/v1/users/count`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    const json = (await res.json()) as { code?: number };
    return json.code === 200;
  } catch {
    return false;
  }
}

function getLoginRedirectUrl(target: string): string {
  return `/login.html?redirect=${encodeURIComponent(target)}`;
}

export default defineClientConfig({
  enhance({ router }) {
    router.beforeEach(async to => {
      if (typeof window === "undefined") {
        return true;
      }

      const path = to.path || "/";
      const isPublicRoute = path.startsWith("/login") || path.startsWith("/404");

      if (isPublicRoute) {
        return true;
      }

      const token = localStorage.getItem(DOCS_TOKEN_KEY);
      if (!token) {
        return getLoginRedirectUrl(to.fullPath || path);
      }

      if (!pendingVerify) {
        pendingVerify = verifyAdminToken(token).finally(() => {
          pendingVerify = null;
        });
      }

      const isValid = await pendingVerify;
      if (!isValid) {
        localStorage.removeItem(DOCS_TOKEN_KEY);
        return getLoginRedirectUrl(to.fullPath || path);
      }

      return true;
    });
  },
});
