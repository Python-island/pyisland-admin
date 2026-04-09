import { useState, useEffect } from "react";
import { users, getUsername, type AdminUserInfo } from "../api";

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

export default function UserList() {
  const [list, setList] = useState<AdminUserInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const currentUser = getUsername();

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
    setTimeout(() => setMsg(""), 3000);
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

  const handleDelete = async (username: string) => {
    if (username === currentUser) {
      showMsg("不能删除自己的账号", "err");
      return;
    }
    if (!confirm(`确定删除管理员 ${username} 吗？`)) return;
    try {
      const res = await users.delete(username);
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

      {msg && (
        <div
          className="fixed z-50"
          style={{
            top: 24,
            right: 24,
            padding: "10px 20px",
            borderRadius: 8,
            fontSize: 14,
            color: "#ffffff",
            backgroundColor: msgType === "ok" ? "var(--apple-blue)" : "#ff453a",
          }}
        >
          {msg}
        </div>
      )}

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
                        <button
                          onClick={() => handleDelete(u.username)}
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
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
