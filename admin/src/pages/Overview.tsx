import { useState, useEffect } from "react";
import { version, users, apiStatus, type AppVersion } from "../api";

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
  const [apiAvailable, setApiAvailable] = useState(0);
  const [apiUnavailable, setApiUnavailable] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [vRes, cRes, sRes] = await Promise.all([
          version.list(),
          users.count(),
          apiStatus.list(),
        ]);
        if (vRes.code === 200 && vRes.data) setVersions(vRes.data);
        if (cRes.code === 200 && cRes.data !== undefined) setAdminCount(cRes.data);
        if (sRes.code === 200 && sRes.data) {
          setApiAvailable(sRes.data.filter((x) => x.status).length);
          setApiUnavailable(sRes.data.filter((x) => !x.status).length);
        }
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
    <div style={{ padding: "48px 48px" }}>
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
        <ApiStatusCard available={apiAvailable} unavailable={apiUnavailable} />
        <StatCard label="应用版本数" value={versions.length} />
      </div>

      {/* Version list */}
      <h2 style={headingStyle}>版本一览</h2>
      {versions.length === 0 ? (
        <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无版本数据</p>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
            gap: 16,
          }}
        >
          {versions.map((v) => (
            <div
              key={v.id}
              style={{
                backgroundColor: "var(--apple-surface-1)",
                borderRadius: 12,
                padding: "20px 24px",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "flex-start",
                  marginBottom: 10,
                }}
              >
                <div
                  style={{
                    fontSize: 17,
                    fontWeight: 600,
                    color: "#ffffff",
                  }}
                >
                  {v.appName}
                </div>
                <div
                  style={{
                    padding: "2px 10px",
                    borderRadius: 980,
                    backgroundColor: "var(--apple-blue)",
                    fontSize: 12,
                    fontWeight: 600,
                    color: "#ffffff",
                    whiteSpace: "nowrap",
                    marginLeft: 12,
                  }}
                >
                  v{v.version}
                </div>
              </div>
              <div
                style={{
                  fontSize: 14,
                  color: "rgba(255,255,255,0.56)",
                  lineHeight: 1.43,
                  marginBottom: 8,
                }}
              >
                {v.description || "无描述"}
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: "rgba(255,255,255,0.32)",
                }}
              >
                更新于 {v.updatedAt}
              </div>
            </div>
          ))}
        </div>
      )}
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

function ApiStatusCard({ available, unavailable }: { available: number; unavailable: number }) {
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
          marginBottom: 16,
        }}
      >
        接口状态
      </div>
      <div style={{ display: "flex", gap: 24, alignItems: "center" }}>
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "#30d158",
            }}
          >
            {available}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4 }}>可用</div>
        </div>
        <div style={{ width: 1, height: 48, backgroundColor: "rgba(255,255,255,0.08)" }} />
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: unavailable > 0 ? "#ff453a" : "rgba(255,255,255,0.32)",
            }}
          >
            {unavailable}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4 }}>不可用</div>
        </div>
      </div>
    </div>
  );
}
