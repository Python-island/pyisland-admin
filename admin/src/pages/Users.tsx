import { useState, useEffect } from "react";
import { users, type AdminUserInfo } from "../api";
import ConfirmDialog from "../components/ConfirmDialog";
import MessageDialog from "../components/MessageDialog";

const inputStyle: React.CSSProperties = {
  padding: "10px 14px",
  backgroundColor: "var(--apple-surface-2)",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 17,
  lineHeight: 1.47,
  letterSpacing: "-0.374px",
  width: "100%",
  outline: "none",
};

const labelStyle: React.CSSProperties = {
  display: "block",
  marginBottom: 8,
  fontSize: 14,
  fontWeight: 600,
  lineHeight: 1.29,
  letterSpacing: "-0.224px",
  color: "rgba(255,255,255,0.64)",
};

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

export default function UsersPage() {
  const [list, setList] = useState<AdminUserInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [newUsername, setNewUsername] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState("");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchUsers = async () => {
    try {
      const res = await users.list();
      if (res.code === 200 && res.data) setList(res.data);
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await users.add(newUsername, newPassword);
      if (res.code === 200) {
        showMsg("添加管理员成功");
        setNewUsername("");
        setNewPassword("");
        fetchUsers();
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("添加失败", "err");
    }
  };

  const requestDelete = (username: string) => {
    setDeleteTarget(username);
    setConfirmVisible(true);
  };

  const handleDelete = async () => {
    setConfirmVisible(false);
    if (!deleteTarget) return;
    try {
      const res = await users.delete(deleteTarget);
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
        人员管理
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
        管理系统管理员账号
      </p>

      <MessageDialog
        visible={!!msg}
        type={msgType}
        message={msg}
        onClose={() => setMsg("")}
      />

      {/* Add admin form */}
      <div style={{ ...cardStyle, marginBottom: 24 }}>
        <h2 style={headingStyle}>添加管理员</h2>
        <form onSubmit={handleAdd}>
          <div className="flex" style={{ gap: 16, marginBottom: 16 }}>
            <div style={{ flex: 1 }}>
              <label style={labelStyle}>用户名</label>
              <input
                type="text"
                value={newUsername}
                onChange={(e) => setNewUsername(e.target.value)}
                placeholder="新管理员用户名"
                required
                style={inputStyle}
              />
            </div>
            <div style={{ flex: 1 }}>
              <label style={labelStyle}>密码</label>
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="设置密码"
                required
                style={inputStyle}
              />
            </div>
          </div>
          <button
            type="submit"
            className="cursor-pointer"
            style={{
              padding: "8px 20px",
              backgroundColor: "var(--apple-blue)",
              color: "#ffffff",
              borderRadius: 980,
              border: "none",
              fontSize: 17,
              fontWeight: 400,
            }}
          >
            添加
          </button>
        </form>
      </div>

      {/* User list */}
      <div style={cardStyle}>
        <h2 style={headingStyle}>
          管理员列表
          <span
            style={{
              fontSize: 14,
              fontWeight: 400,
              color: "rgba(255,255,255,0.48)",
              marginLeft: 12,
            }}
          >
            共 {list.length} 人
          </span>
        </h2>

        {loading ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>加载中...</p>
        ) : list.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无管理员</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  {["ID", "用户名", "创建时间", "操作"].map((h) => (
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
                {list.map((u) => (
                  <tr key={u.id}>
                    <td style={tdStyle}>{u.id}</td>
                    <td style={tdStyle}>{u.username}</td>
                    <td style={tdStyle}>{u.createdAt}</td>
                    <td style={tdStyle}>
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
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
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
    </div>
  );
}

const tdStyle: React.CSSProperties = {
  padding: "10px 12px",
  fontSize: 14,
  lineHeight: 1.43,
  letterSpacing: "-0.224px",
  color: "#ffffff",
  borderBottom: "1px solid rgba(255,255,255,0.04)",
};
