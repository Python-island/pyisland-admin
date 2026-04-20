/**
 * @file UserList.tsx
 * @description 管理员列表页面。
 * @description 提供管理员查看与删除能力。
 * @author 鸡哥
 */

import { useState, useEffect } from "react";
import { adminUsers, userAccounts, getUsername, sanitizeUrl, type AdminUserInfo } from "../api";
import ConfirmDialog from "../components/ConfirmDialog";
import MessageDialog from "../components/MessageDialog";

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

/**
 * 管理员列表组件。
 * @returns 渲染管理员列表与删除交互。
 */
export default function UserList() {
  const [list, setList] = useState<AdminUserInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const currentUser = getUsername();
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState("");
  const [demoteVisible, setDemoteVisible] = useState(false);
  const [demoteTarget, setDemoteTarget] = useState("");

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
      const res = await adminUsers.list();
      if (res.code === 200 && res.data) setList(res.data);
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  };

  /**
   * 轮换目标管理员的 TOTP 种子。
   * @param username - 目标管理员用户名。
   */
  const handleRotateTotpSeed = async (username: string) => {
    try {
      const res = await adminUsers.rotateTotpSeed(username);
      if (res.code === 200) {
        showMsg(`已为 ${username} 轮换 TOTP 种子`);
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("轮换失败", "err");
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
    if (username === currentUser) {
      showMsg("不能删除自己的账号", "err");
      return;
    }
    setDeleteTarget(username);
    setConfirmVisible(true);
  };

  const handleDelete = async () => {
    setConfirmVisible(false);
    if (!deleteTarget) return;
    try {
      const res = await adminUsers.delete(deleteTarget);
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

  /**
   * 发起"降为普通用户"二次确认，自身禁止降级。
   * @param username - 目标管理员用户名。
   */
  const requestDemote = (username: string) => {
    if (username === currentUser) {
      showMsg("不能将当前登录管理员降为普通用户", "err");
      return;
    }
    setDemoteTarget(username);
    setDemoteVisible(true);
  };

  /**
   * 执行角色降级。成功后该用户从管理员列表消失。
   */
  const handleDemote = async () => {
    setDemoteVisible(false);
    if (!demoteTarget) return;
    try {
      const res = await userAccounts.updateRole(demoteTarget, "user");
      if (res.code === 200) {
        showMsg(`已将 ${demoteTarget} 降为普通用户`);
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
        管理员列表
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
        查看和管理系统管理员
      </p>

      <MessageDialog
        visible={!!msg}
        type={msgType}
        message={msg}
        onClose={() => setMsg("")}
      />

      <div style={cardStyle}>
        <h2 style={headingStyle}>
          全部管理员
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
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无管理员</p>
        ) : (
          <div>
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr>
                    {["头像", "用户名", "创建时间", "操作"].map((h) => (
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
                      <td style={tdStyle}>
                        {u.username}
                        {u.username === currentUser && (
                          <span
                            style={{
                              marginLeft: 8,
                              fontSize: 11,
                              color: "var(--apple-link-dark)",
                            }}
                          >
                            (当前)
                          </span>
                        )}
                      </td>
                      <td style={tdStyle}>{u.createdAt}</td>
                      <td style={tdStyle}>
                        {u.username === currentUser ? (
                          <span style={{ fontSize: 12, color: "rgba(255,255,255,0.24)" }}>
                            —
                          </span>
                        ) : (
                          <div style={{ display: "flex", gap: 8 }}>
                            <button
                              onClick={() => void handleRotateTotpSeed(u.username)}
                              className="cursor-pointer"
                              style={{
                                padding: "2px 12px",
                                backgroundColor: "transparent",
                                color: "#5ac8fa",
                                borderRadius: 980,
                                border: "1px solid #5ac8fa",
                                fontSize: 12,
                                lineHeight: 1.43,
                              }}
                            >
                              轮换 TOTP
                            </button>
                            <button
                              onClick={() => requestDemote(u.username)}
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
                        )}
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
        message={`确定要删除管理员 ${deleteTarget} 吗？此操作不可撤销。`}
        confirmText="删除"
        danger
        onConfirm={handleDelete}
        onCancel={() => setConfirmVisible(false)}
      />

      <ConfirmDialog
        visible={demoteVisible}
        title="撤销管理员权限"
        message={`确定要将 ${demoteTarget} 降为普通用户吗？其当前登录态会被清除，需重新登录。`}
        confirmText="降为普通用户"
        onConfirm={handleDemote}
        onCancel={() => setDemoteVisible(false)}
      />
    </div>
  );
}
