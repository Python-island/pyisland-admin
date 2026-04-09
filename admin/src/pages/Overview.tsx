import { useState, useEffect } from "react";
import { version, users, type AppVersion } from "../api";

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

export default function Overview() {
  const [versions, setVersions] = useState<AppVersion[]>([]);
  const [adminCount, setAdminCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [vRes, cRes] = await Promise.all([
          version.list(),
          users.count(),
        ]);
        if (vRes.code === 200 && vRes.data) setVersions(vRes.data);
        if (cRes.code === 200 && cRes.data !== undefined) setAdminCount(cRes.data);
      } catch {
        /* ignore */
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center" style={{ minHeight: "60vh" }}>
        <span style={{ color: "rgba(255,255,255,0.48)", fontSize: 17 }}>加载中...</span>
      </div>
    );
  }

  return (
    <div style={{ padding: "48px 48px", maxWidth: 980 }}>
      {/* Hero */}
      <h1
        style={{
          fontFamily: "var(--font-display)",
          fontSize: 56,
          fontWeight: 600,
          lineHeight: 1.07,
          letterSpacing: "-0.28px",
          color: "#ffffff",
          margin: "0 0 8px",
        }}
      >
        总览
      </h1>
      <p
        style={{
          fontFamily: "var(--font-display)",
          fontSize: 21,
          fontWeight: 400,
          lineHeight: 1.19,
          letterSpacing: "0.231px",
          color: "rgba(255,255,255,0.56)",
          marginBottom: 48,
        }}
      >
        系统运行概况
      </p>

      {/* Stats */}
      <div className="flex" style={{ gap: 20, marginBottom: 32 }}>
        <StatCard label="管理员数量" value={adminCount} />
        <StatCard label="应用版本数" value={versions.length} />
      </div>

      {/* Version list */}
      <div style={cardStyle}>
        <h2 style={headingStyle}>所有版本一览</h2>
        {versions.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无版本数据</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  {["应用名称", "版本号", "描述", "更新时间"].map((h) => (
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
                {versions.map((v) => (
                  <tr key={v.id}>
                    {[v.appName, v.version, v.description || "-", v.updatedAt].map(
                      (val, i) => (
                        <td
                          key={i}
                          style={{
                            padding: "10px 12px",
                            fontSize: 14,
                            lineHeight: 1.43,
                            letterSpacing: "-0.224px",
                            color: "#ffffff",
                            borderBottom: "1px solid rgba(255,255,255,0.04)",
                          }}
                        >
                          {val}
                        </td>
                      )
                    )}
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

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "var(--apple-surface-1)",
        borderRadius: 12,
        padding: "24px 28px",
      }}
    >
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.33,
          letterSpacing: "-0.12px",
          color: "rgba(255,255,255,0.48)",
          textTransform: "uppercase",
          marginBottom: 8,
        }}
      >
        {label}
      </div>
      <div
        style={{
          fontFamily: "var(--font-display)",
          fontSize: 40,
          fontWeight: 600,
          lineHeight: 1.1,
          color: "#ffffff",
        }}
      >
        {value}
      </div>
    </div>
  );
}
