import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { apiStatus, type ApiStatus } from "../api";

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

const thStyle: React.CSSProperties = {
  textAlign: "left",
  padding: "8px 12px",
  fontSize: 12,
  fontWeight: 600,
  lineHeight: 1.33,
  letterSpacing: "-0.12px",
  color: "rgba(255,255,255,0.48)",
  textTransform: "uppercase",
  borderBottom: "1px solid rgba(255,255,255,0.08)",
};

const tdStyle: React.CSSProperties = {
  padding: "10px 12px",
  fontSize: 14,
  lineHeight: 1.43,
  letterSpacing: "-0.224px",
  color: "#ffffff",
  borderBottom: "1px solid rgba(255,255,255,0.04)",
  verticalAlign: "top",
};

export default function ApiStatusPage() {
  const [list, setList] = useState<ApiStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await apiStatus.list();
      if (res.code === 200 && res.data) setList(res.data);
      else showMsg(res.message || "加载失败", "err");
    } catch {
      showMsg("加载失败", "err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
  }, []);


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
        接口状态
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
        查询和修改各个 API 接口运行状态
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        <div className="flex items-center justify-between" style={{ marginBottom: 10 }}>
          <h2 style={headingStyle}>
            全部接口
            <span
              style={{
                fontSize: 14,
                fontWeight: 400,
                color: "rgba(255,255,255,0.48)",
                marginLeft: 12,
              }}
            >
              共 {list.length} 项
            </span>
          </h2>
          <button
            onClick={fetchList}
            className="cursor-pointer"
            style={{
              padding: "8px 16px",
              backgroundColor: "var(--apple-surface-2)",
              color: "rgba(255,255,255,0.8)",
              borderRadius: 980,
              border: "none",
              fontSize: 14,
            }}
          >
            刷新
          </button>
        </div>

        {loading ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>加载中...</p>
        ) : list.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无数据（请先在数据库插入接口配置）</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={thStyle}>API</th>
                  <th style={thStyle}>状态</th>
                  <th style={thStyle}>提示信息</th>
                  <th style={thStyle}>最后更新</th>
                </tr>
              </thead>
              <tbody>
                {list.map((row) => (
                  <tr key={row.apiName}>
                    <td style={tdStyle}>{row.apiName}</td>
                    <td style={tdStyle}>
                      <span
                        style={{
                          padding: "2px 12px",
                          borderRadius: 980,
                          border: "1px solid rgba(255,255,255,0.16)",
                          backgroundColor: row.status ? "rgba(48, 209, 88, 0.16)" : "rgba(255, 69, 58, 0.16)",
                          color: row.status ? "#30d158" : "#ff453a",
                          fontSize: 12,
                          lineHeight: 1.43,
                          display: "inline-block",
                        }}
                      >
                        {row.status ? "启用" : "禁用"}
                      </span>
                    </td>
                    <td style={{ ...tdStyle, color: row.message ? "rgba(255,255,255,0.72)" : "rgba(255,255,255,0.24)" }}>
                      {row.message || "—"}
                    </td>
                    <td style={{ ...tdStyle, color: "rgba(255,255,255,0.40)", fontSize: 12 }}>
                      {row.updatedAt || "—"}
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
