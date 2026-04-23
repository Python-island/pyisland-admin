/**
 * @file Layout.tsx
 * @description 后台通用布局组件。
 * @description 提供侧边导航、用户信息区与主内容路由出口。
 * @author 鸡哥
 */

import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { clearToken, getUsername, adminUsers, sanitizeUrl } from "../api";
import { useState, useEffect } from "react";

const navItems = [
  { label: "总览", path: "/" },
  {
    label: "版本管理",
    children: [
      { label: "更新版本", path: "/version/update" },
      { label: "创建版本", path: "/version/create" },
      { label: "删除版本", path: "/version/delete" },
    ],
  },
  {
    label: "管理员管理",
    children: [
      { label: "管理员列表", path: "/admin-users/list" },
    ],
  },
  {
    label: "用户管理",
    children: [
      { label: "用户列表", path: "/app-users/list" },
      { label: "添加用户", path: "/app-users/add" },
      { label: "修改用户", path: "/app-users/edit" },
    ],
  },
  {
    label: "接口管理",
    children: [
      { label: "接口状态", path: "/api-status" },
      { label: "状态管理", path: "/api-message" },
      { label: "调试接口", path: "/api-debug" },
    ],
  },
  {
    label: "支付管理",
    children: [
      { label: "支付配置", path: "/payment/config" },
      { label: "订单管理", path: "/payment/orders" },
      { label: "支付DLQ", path: "/payment/dlq" },
      { label: "邮件DLQ", path: "/email/dlq" },
    ],
  },
  {
    label: "内容管理",
    children: [
      { label: "壁纸审核", path: "/wallpapers/review" },
      { label: "举报处理", path: "/wallpapers/reports" },
      { label: "评分管理", path: "/wallpapers/ratings" },
      { label: "标签管理", path: "/tags" },
      { label: "用户反馈", path: "/feedbacks" },
      { label: "公告管理", path: "/announcement" },
    ],
  },
];

const linkBase: React.CSSProperties = {
  display: "block",
  padding: "8px 20px",
  borderRadius: 8,
  fontSize: 14,
  lineHeight: 1.43,
  letterSpacing: "-0.224px",
  color: "rgba(255,255,255,0.64)",
  textDecoration: "none",
  transition: "all 0.15s",
};

const linkActive: React.CSSProperties = {
  ...linkBase,
  backgroundColor: "var(--apple-blue)",
  color: "#ffffff",
};

const subLinkBase: React.CSSProperties = {
  ...linkBase,
  paddingLeft: 36,
  fontSize: 13,
};

const subLinkActive: React.CSSProperties = {
  ...subLinkBase,
  color: "var(--apple-link-dark)",
};

/**
 * 后台布局组件。
 * @returns 渲染侧边导航与主内容容器。
 */
export default function Layout() {
  const navigate = useNavigate();
  const [expandedSections, setExpandedSections] = useState<Set<string>>(
    new Set(["版本管理", "管理员管理", "用户管理", "接口管理", "支付管理", "内容管理"])
  );
  const [avatar, setAvatar] = useState<string | null>(null);

  useEffect(() => {
    const username = getUsername();
    if (username) {
      adminUsers.getProfile(username).then((res) => {
        if (res.code === 200 && res.data?.avatar) {
          setAvatar(res.data.avatar);
        }
      }).catch(() => {});
    }
    const onAvatarUpdated = (e: Event) => {
      setAvatar((e as CustomEvent).detail);
    };
    window.addEventListener("avatar-updated", onAvatarUpdated);
    return () => window.removeEventListener("avatar-updated", onAvatarUpdated);
  }, []);

  const toggleSection = (label: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(label)) next.delete(label);
      else next.add(label);
      return next;
    });
  };

  const handleLogout = () => {
    clearToken();
    navigate("/login");
  };

  return (
    <div className="flex" style={{ backgroundColor: "var(--apple-black)", height: "100vh", overflow: "hidden" }}>
      {/* Sidebar */}
      <aside
        className="admin-sidebar flex flex-col"
        style={{
          width: 280,
          flexShrink: 0,
          height: "100vh",
          position: "sticky",
          top: 0,
          backgroundColor: "var(--apple-surface-1)",
          borderRight: "1px solid rgba(255,255,255,0.06)",
          overflowY: "auto",
        }}
      >
        {/* Logo */}
        <div
          className="flex items-center"
          style={{
            padding: "18px 20px 18px",
            borderBottom: "1px solid rgba(255,255,255,0.06)",
            gap: 10,
          }}
        >
          <img
            src="/eisland.svg"
            alt="PyIsland"
            style={{ width: 28, height: 28 }}
          />
          <span
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 21,
              fontWeight: 600,
              lineHeight: 1.19,
              letterSpacing: "0.231px",
              color: "#ffffff",
            }}
          >
            PyIsland 管理面板
          </span>
        </div>

        {/* Nav */}
        <nav className="flex-1" style={{ padding: "12px 12px" }}>
          {navItems.map((item) =>
            item.children ? (
              <div key={item.label} style={{ marginBottom: 4 }}>
                <button
                  onClick={() => toggleSection(item.label)}
                  className="w-full text-left cursor-pointer border-none bg-transparent"
                  style={{
                    ...linkBase,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                  }}
                >
                  {item.label}
                  <span
                    style={{
                      fontSize: 12,
                      transform: expandedSections.has(item.label)
                        ? "rotate(90deg)"
                        : "rotate(0deg)",
                      transition: "transform 0.15s",
                    }}
                  >
                    ›
                  </span>
                </button>
                {expandedSections.has(item.label) && (
                  <div>
                    {item.children.map((child) => (
                      <NavLink
                        key={child.path}
                        to={child.path}
                        style={({ isActive }) =>
                          isActive ? subLinkActive : subLinkBase
                        }
                      >
                        {child.label}
                      </NavLink>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <NavLink
                key={item.path}
                to={item.path}
                end={item.path === "/"}
                style={({ isActive }) => (isActive ? linkActive : linkBase)}
              >
                {item.label}
              </NavLink>
            )
          )}
        </nav>

        {/* User / Logout */}
        <div
          style={{
            padding: "16px 20px",
            borderTop: "1px solid rgba(255,255,255,0.06)",
          }}
        >
          <NavLink
            to="/profile"
            className="flex items-center"
            style={{
              gap: 10,
              fontSize: 12,
              color: "rgba(255,255,255,0.48)",
              marginBottom: 8,
              textDecoration: "none",
              cursor: "pointer",
            }}
          >
            <div
              style={{
                width: 28,
                height: 28,
                borderRadius: "50%",
                backgroundColor: "var(--apple-surface-2)",
                backgroundImage: sanitizeUrl(avatar) ? `url(${sanitizeUrl(avatar)})` : "none",
                backgroundSize: "cover",
                backgroundPosition: "center",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 12,
                fontWeight: 600,
                color: "rgba(255,255,255,0.4)",
                flexShrink: 0,
              }}
            >
              {!avatar && getUsername().charAt(0).toUpperCase()}
            </div>
            {getUsername()}
          </NavLink>
          <button
            onClick={handleLogout}
            className="cursor-pointer border-none bg-transparent"
            style={{
              fontSize: 12,
              color: "var(--apple-link-dark)",
              padding: 0,
            }}
          >
            退出登录
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1" style={{ overflow: "auto" }}>
        <Outlet />
      </main>
    </div>
  );
}
