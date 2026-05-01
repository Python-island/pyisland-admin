import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { appUsers, userAccounts, identityAdmin, type UserAccountItem, type Gender, type IdentityUserInfo, sanitizeUrl } from "../api";
import ConfirmDialog from "../components/ConfirmDialog";
import MessageDialog from "../components/MessageDialog";

function genderLabel(gender: Gender | undefined, custom: string | null | undefined): string {
  if (gender === "male") return "男";
  if (gender === "female") return "女";
  if (gender === "custom") {
    const trimmed = (custom || "").trim();
    return trimmed ? `自定义 (${trimmed})` : "自定义";
  }
  return "不愿透露";
}

function roleLabel(role: "admin" | "pro" | "user" | undefined): string {
  if (role === "admin") return "管理员";
  if (role === "pro") return "Pro 用户";
  return "普通用户";
}

const cardStyle: React.CSSProperties = {
  backgroundColor: "var(--apple-surface-1)",
  borderRadius: 12,
  padding: 32,
};

const headingStyle: React.CSSProperties = {
  fontFamily: "var(--font-display)",
  fontSize: 21,
  fontWeight: 600,
  lineHeight: 1.19,
  letterSpacing: "0.231px",
  color: "#ffffff",
  marginBottom: 24,
};

const tdStyle: React.CSSProperties = {
  padding: "10px 12px",
  fontSize: 14,
  lineHeight: 1.43,
  letterSpacing: "-0.224px",
  color: "#ffffff",
  borderBottom: "1px solid rgba(255,255,255,0.04)",
};

export default function AppUserList() {
  const navigate = useNavigate();
  const [list, setList] = useState<UserAccountItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState("");
  const [roleVisible, setRoleVisible] = useState(false);
  const [roleTarget, setRoleTarget] = useState("");
  const [targetRole, setTargetRole] = useState<"admin" | "pro" | "user">("pro");
  const [identityVisible, setIdentityVisible] = useState(false);
  const [identityTarget, setIdentityTarget] = useState("");
  const [identityInfo, setIdentityInfo] = useState<IdentityUserInfo | null>(null);
  const [identityLoading, setIdentityLoading] = useState(false);

  const total = list.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const safePage = Math.min(page, totalPages);
  const startIndex = (safePage - 1) * pageSize;
  const endIndex = startIndex + pageSize;
  const pageList = list.slice(startIndex, endIndex);

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchUsers = async () => {
    try {
      const res = await userAccounts.list();
      if (res.code === 200 && res.data) {
        setList(res.data.filter((u) => u.role !== "admin"));
      }
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  };

  const handleToggleBan = async (username: string, banned: boolean) => {
    try {
      const res = await userAccounts.updateBan(username, !banned);
      if (res.code === 200) {
        showMsg(!banned ? `已封禁 ${username}` : `已解除封禁 ${username}`);
        fetchUsers();
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg(!banned ? "封禁失败" : "解除封禁失败", "err");
    }
  };

  const handleQueryIdentity = async (username: string) => {
    setIdentityTarget(username);
    setIdentityInfo(null);
    setIdentityVisible(true);
    setIdentityLoading(true);
    try {
      const res = await identityAdmin.getUserInfo(username);
      if (res.code === 200 && res.data) {
        setIdentityInfo(res.data);
      } else {
        showMsg(res.message || "查询实名信息失败", "err");
        setIdentityVisible(false);
      }
    } catch {
      showMsg("查询实名信息失败", "err");
      setIdentityVisible(false);
    } finally {
      setIdentityLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  useEffect(() => {
    if (page > totalPages) {
      setPage(totalPages);
    }
  }, [page, totalPages]);

  const requestDelete = (username: string) => {
    setDeleteTarget(username);
    setConfirmVisible(true);
  };

  const handleDelete = async () => {
    setConfirmVisible(false);
    if (!deleteTarget) return;
    try {
      const res = await appUsers.delete(deleteTarget);
      if (res.code === 200) {
        showMsg("删除成功");
        fetchUsers();
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("删除失败", "err");
    }
  };

  const requestUpdateRole = (username: string, role: "admin" | "pro" | "user") => {
    setRoleTarget(username);
    setTargetRole(role);
    setRoleVisible(true);
  };

  /**
   * 执行角色变更。成功后刷新列表。
   */
  const handleUpdateRole = async () => {
    setRoleVisible(false);
    if (!roleTarget) return;
    try {
      const res = await userAccounts.updateRole(roleTarget, targetRole);
      if (res.code === 200) {
        const roleText = targetRole === "admin" ? "管理员" : targetRole === "pro" ? "Pro 用户" : "普通用户";
        showMsg(`已将 ${roleTarget} 设为${roleText}`);
        fetchUsers();
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("操作失败", "err");
    }
  };

  return (
    <div style={{ padding: "48px 48px" }}>
      <h1
        style={{
          fontFamily: "var(--font-display)",
          fontSize: 40,
          fontWeight: 600,
          lineHeight: 1.1,
          color: "#ffffff",
          margin: "0 0 8px",
        }}
      >
        用户列表
      </h1>
      <p
        style={{
          fontSize: 21,
          fontWeight: 400,
          lineHeight: 1.19,
          letterSpacing: "0.231px",
          color: "rgba(255,255,255,0.56)",
          marginBottom: 40,
        }}
      >
        查看和管理普通用户与 Pro 用户
      </p>

      <MessageDialog
        visible={!!msg}
        type={msgType}
        message={msg}
        onClose={() => setMsg("")}
      />

      <div style={cardStyle}>
        <h2 style={headingStyle}>
          全部用户
          <span
            style={{
              fontSize: 14,
              fontWeight: 400,
              color: "rgba(255,255,255,0.48)",
              marginLeft: 12,
            }}
          >
            共 {total} 人
          </span>
        </h2>

        {loading ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>加载中...</p>
        ) : total === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无用户</p>
        ) : (
          <div>
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr>
                    {["头像", "用户名", "角色", "邮箱", "性别", "生日", "创建时间", "操作"].map((h) => (
                      <th
                        key={h}
                        style={{
                          textAlign: "left",
                          padding: "8px 12px",
                          fontSize: 12,
                          fontWeight: 600,
                          lineHeight: 1.33,
                          letterSpacing: "-0.12px",
                          color: "rgba(255,255,255,0.48)",
                          textTransform: "uppercase",
                          borderBottom: "1px solid rgba(255,255,255,0.08)",
                        }}
                      >
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {pageList.map((u) => (
                    <tr key={u.id}>
                      <td style={tdStyle}>
                        <div
                          style={{
                            width: 32,
                            height: 32,
                            borderRadius: "50%",
                            backgroundColor: "var(--apple-surface-2)",
                            backgroundImage: sanitizeUrl(u.avatar) ? `url(${sanitizeUrl(u.avatar)})` : "none",
                            backgroundSize: "cover",
                            backgroundPosition: "center",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            fontSize: 13,
                            fontWeight: 600,
                            color: "rgba(255,255,255,0.4)",
                          }}
                        >
                          {!u.avatar && u.username.charAt(0).toUpperCase()}
                        </div>
                      </td>
                      <td style={tdStyle}>{u.username}</td>
                      <td style={tdStyle}>{roleLabel(u.role)}</td>
                      <td style={tdStyle}>{u.email}</td>
                      <td style={tdStyle}>{genderLabel(u.gender, u.genderCustom)}</td>
                      <td style={tdStyle}>{u.birthday || "—"}</td>
                      <td style={tdStyle}>{u.createdAt}</td>
                      <td style={tdStyle}>
                        <div style={{ display: "flex", gap: 8 }}>
                          <button
                            onClick={() => navigate(`/app-users/edit?username=${encodeURIComponent(u.username)}`)}
                            className="cursor-pointer"
                            style={{
                              padding: "2px 12px",
                              backgroundColor: "transparent",
                              color: "var(--apple-link-dark)",
                              borderRadius: 980,
                              border: "1px solid var(--apple-link-dark)",
                              fontSize: 12,
                              lineHeight: 1.43,
                            }}
                          >
                            编辑
                          </button>
                          {u.role !== "pro" && (
                            <button
                              onClick={() => requestUpdateRole(u.username, "pro")}
                              className="cursor-pointer"
                              style={{
                                padding: "2px 12px",
                                backgroundColor: "transparent",
                                color: "#64d2ff",
                                borderRadius: 980,
                                border: "1px solid #64d2ff",
                                fontSize: 12,
                                lineHeight: 1.43,
                              }}
                            >
                              设为 Pro
                            </button>
                          )}
                          {u.role !== "admin" && (
                            <button
                              onClick={() => requestUpdateRole(u.username, "admin")}
                              className="cursor-pointer"
                              style={{
                                padding: "2px 12px",
                                backgroundColor: "transparent",
                                color: "#30d158",
                                borderRadius: 980,
                                border: "1px solid #30d158",
                                fontSize: 12,
                                lineHeight: 1.43,
                              }}
                            >
                              设为管理员
                            </button>
                          )}
                          {u.role === "pro" && (
                            <button
                              onClick={() => requestUpdateRole(u.username, "user")}
                              className="cursor-pointer"
                              style={{
                                padding: "2px 12px",
                                backgroundColor: "transparent",
                                color: "#ff9f0a",
                                borderRadius: 980,
                                border: "1px solid #ff9f0a",
                                fontSize: 12,
                                lineHeight: 1.43,
                              }}
                            >
                              降为普通用户
                            </button>
                          )}
                          <button
                            onClick={() => void handleToggleBan(u.username, Boolean(u.banned))}
                            className="cursor-pointer"
                            style={{
                              padding: "2px 12px",
                              backgroundColor: "transparent",
                              color: Boolean(u.banned) ? "#34c759" : "#ff375f",
                              borderRadius: 980,
                              border: `1px solid ${Boolean(u.banned) ? "#34c759" : "#ff375f"}`,
                              fontSize: 12,
                              lineHeight: 1.43,
                            }}
                          >
                            {Boolean(u.banned) ? "解除封禁" : "封禁"}
                          </button>
                          <button
                            onClick={() => handleQueryIdentity(u.username)}
                            className="cursor-pointer"
                            style={{
                              padding: "2px 12px",
                              backgroundColor: "transparent",
                              color: "#bf5af2",
                              borderRadius: 980,
                              border: "1px solid #bf5af2",
                              fontSize: 12,
                              lineHeight: 1.43,
                            }}
                          >
                            实名信息
                          </button>
                          <button
                            onClick={() => requestDelete(u.username)}
                            className="cursor-pointer"
                            style={{
                              padding: "2px 12px",
                              backgroundColor: "transparent",
                              color: "#ff453a",
                              borderRadius: 980,
                              border: "1px solid #ff453a",
                              fontSize: 12,
                              lineHeight: 1.43,
                            }}
                          >
                            删除
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div
              style={{
                marginTop: 16,
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: 12,
                flexWrap: "wrap",
              }}
            >
              <div style={{ fontSize: 13, color: "rgba(255,255,255,0.56)" }}>
                第 {safePage} / {totalPages} 页 · 显示 {startIndex + 1}-{Math.min(endIndex, total)} 条
              </div>

              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <label style={{ fontSize: 13, color: "rgba(255,255,255,0.56)" }}>每页</label>
                <select
                  value={pageSize}
                  onChange={(e) => {
                    const nextSize = Number(e.target.value);
                    setPageSize(nextSize);
                    setPage(1);
                  }}
                  style={{
                    backgroundColor: "var(--apple-surface-2)",
                    color: "#fff",
                    border: "1px solid rgba(255,255,255,0.12)",
                    borderRadius: 8,
                    padding: "4px 8px",
                  }}
                >
                  {[10, 20, 50].map((size) => (
                    <option key={size} value={size}>
                      {size}
                    </option>
                  ))}
                </select>

                <button
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={safePage <= 1}
                  style={{
                    padding: "4px 10px",
                    borderRadius: 8,
                    border: "1px solid rgba(255,255,255,0.12)",
                    background: "transparent",
                    color: safePage <= 1 ? "rgba(255,255,255,0.28)" : "#fff",
                  }}
                >
                  上一页
                </button>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={safePage >= totalPages}
                  style={{
                    padding: "4px 10px",
                    borderRadius: 8,
                    border: "1px solid rgba(255,255,255,0.12)",
                    background: "transparent",
                    color: safePage >= totalPages ? "rgba(255,255,255,0.28)" : "#fff",
                  }}
                >
                  下一页
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      <ConfirmDialog
        visible={confirmVisible}
        title="删除确认"
        message={`确定要删除用户 ${deleteTarget} 吗？此操作不可撤销。`}
        confirmText="删除"
        danger
        onConfirm={handleDelete}
        onCancel={() => setConfirmVisible(false)}
      />

      <ConfirmDialog
        visible={roleVisible}
        title="变更用户角色"
        message={`确定要将 ${roleTarget} 设为${targetRole === "admin" ? "管理员" : targetRole === "pro" ? "Pro 用户" : "普通用户"}吗？角色变更后其当前登录态会被清除，需重新登录。`}
        confirmText="确认变更"
        onConfirm={handleUpdateRole}
        onCancel={() => setRoleVisible(false)}
      />

      {identityVisible && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 1000,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            backgroundColor: "rgba(0,0,0,0.55)",
          }}
          onClick={() => setIdentityVisible(false)}
        >
          <div
            style={{
              backgroundColor: "var(--apple-surface-1)",
              borderRadius: 16,
              padding: 32,
              minWidth: 360,
              maxWidth: 480,
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ color: "#fff", fontSize: 20, fontWeight: 600, marginTop: 0, marginBottom: 20 }}>
              {identityTarget} 的实名信息
            </h3>
            {identityLoading ? (
              <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>查询中...</p>
            ) : identityInfo && !identityInfo.verified ? (
              <p style={{ color: "rgba(255,255,255,0.56)", fontSize: 14 }}>该用户尚未完成实名认证</p>
            ) : identityInfo ? (
              <div style={{ display: "grid", gap: 14, color: "rgba(255,255,255,0.84)", fontSize: 14 }}>
                <div><strong style={{ color: "rgba(255,255,255,0.56)" }}>姓名：</strong>{identityInfo.certName || "—"}</div>
                <div><strong style={{ color: "rgba(255,255,255,0.56)" }}>身份证号：</strong><span style={{ fontFamily: "monospace" }}>{identityInfo.maskedCertNo || "—"}</span></div>
                <div><strong style={{ color: "rgba(255,255,255,0.56)" }}>状态：</strong><span style={{ color: identityInfo.status === "PASSED" ? "#34c759" : "#ff9f0a", fontWeight: 600 }}>{identityInfo.status}</span></div>
                <div><strong style={{ color: "rgba(255,255,255,0.56)" }}>认证时间：</strong>{identityInfo.verifiedAt || "—"}</div>
              </div>
            ) : null}
            <div style={{ marginTop: 24, textAlign: "right" }}>
              <button
                onClick={() => setIdentityVisible(false)}
                className="cursor-pointer"
                style={{
                  padding: "8px 24px",
                  backgroundColor: "var(--apple-blue)",
                  color: "#fff",
                  borderRadius: 980,
                  border: "none",
                  fontSize: 14,
                  fontWeight: 500,
                }}
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
