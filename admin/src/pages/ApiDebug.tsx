import { useState } from "react";

const METHODS = ["GET", "POST", "PUT", "DELETE"] as const;
type Method = (typeof METHODS)[number];

const PRESET_APIS = [
  { label: "获取所有版本", method: "GET" as Method, path: "/v1/version/list", body: "" },
  { label: "获取指定版本", method: "GET" as Method, path: "/v1/version?appName=pyisland", body: "" },
  { label: "管理员列表", method: "GET" as Method, path: "/v1/users", body: "" },
  { label: "管理员数量", method: "GET" as Method, path: "/v1/users/count", body: "" },
  { label: "接口状态列表", method: "GET" as Method, path: "/v1/service-status/list", body: "" },
];

const cardStyle: React.CSSProperties = {
  backgroundColor: "var(--apple-surface-1)",
  borderRadius: 12,
  padding: 32,
};

const inputStyle: React.CSSProperties = {
  padding: "10px 14px",
  backgroundColor: "var(--apple-surface-2)",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 14,
  lineHeight: 1.43,
  letterSpacing: "-0.224px",
  outline: "none",
  width: "100%",
};

export default function ApiDebug() {
  const [method, setMethod] = useState<Method>("GET");
  const [path, setPath] = useState("/v1/version/list");
  const [body, setBody] = useState("");
  const [response, setResponse] = useState<string | null>(null);
  const [statusCode, setStatusCode] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [elapsed, setElapsed] = useState<number | null>(null);

  const token = localStorage.getItem("token");
  const base = (import.meta as { env: Record<string, string> }).env.VITE_API_BASE || "/api";

  const send = async () => {
    setLoading(true);
    setResponse(null);
    setStatusCode(null);
    setElapsed(null);
    const start = Date.now();
    try {
      const headers: Record<string, string> = {
        "Content-Type": "application/json",
      };
      if (token) headers["Authorization"] = `Bearer ${token}`;
      const opts: RequestInit = { method, headers };
      if (method !== "GET" && method !== "DELETE" && body.trim()) {
        opts.body = body;
      }
      const res = await fetch(`${base}${path}`, opts);
      const ms = Date.now() - start;
      setStatusCode(res.status);
      setElapsed(ms);
      const text = await res.text();
      try {
        setResponse(JSON.stringify(JSON.parse(text), null, 2));
      } catch {
        setResponse(text);
      }
    } catch (e) {
      setElapsed(Date.now() - start);
      setStatusCode(0);
      setResponse(String(e));
    } finally {
      setLoading(false);
    }
  };

  const applyPreset = (p: (typeof PRESET_APIS)[number]) => {
    setMethod(p.method);
    setPath(p.path);
    setBody(p.body);
    setResponse(null);
    setStatusCode(null);
    setElapsed(null);
  };

  const statusColor =
    statusCode === null
      ? "rgba(255,255,255,0.32)"
      : statusCode >= 200 && statusCode < 300
      ? "#30d158"
      : statusCode >= 400
      ? "#ff453a"
      : "#ffd60a";

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
        调试接口
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
        向 API 接口发送测试请求并查看响应
      </p>

      <div style={{ display: "flex", gap: 24, alignItems: "flex-start" }}>
        {/* Left: request panel */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={cardStyle}>
            {/* Preset list */}
            <div style={{ marginBottom: 24 }}>
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: "rgba(255,255,255,0.48)",
                  textTransform: "uppercase",
                  letterSpacing: "-0.12px",
                  marginBottom: 10,
                }}
              >
                快捷接口
              </div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                {PRESET_APIS.map((p) => (
                  <button
                    key={p.label}
                    onClick={() => applyPreset(p)}
                    className="cursor-pointer"
                    style={{
                      padding: "4px 12px",
                      backgroundColor: "var(--apple-surface-2)",
                      color: "rgba(255,255,255,0.72)",
                      borderRadius: 980,
                      border: "none",
                      fontSize: 12,
                      lineHeight: 1.43,
                    }}
                  >
                    {p.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Method + Path */}
            <div style={{ display: "flex", gap: 10, marginBottom: 16 }}>
              <select
                value={method}
                onChange={(e) => setMethod(e.target.value as Method)}
                style={{
                  ...inputStyle,
                  width: 100,
                  flexShrink: 0,
                  cursor: "pointer",
                }}
              >
                {METHODS.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
              <input
                value={path}
                onChange={(e) => setPath(e.target.value)}
                placeholder="/v1/version/list"
                style={inputStyle}
              />
            </div>

            {/* Body */}
            {(method === "POST" || method === "PUT") && (
              <div style={{ marginBottom: 16 }}>
                <div
                  style={{
                    fontSize: 12,
                    fontWeight: 600,
                    color: "rgba(255,255,255,0.48)",
                    textTransform: "uppercase",
                    letterSpacing: "-0.12px",
                    marginBottom: 8,
                  }}
                >
                  请求体 (JSON)
                </div>
                <textarea
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  placeholder={'{\n  "key": "value"\n}'}
                  rows={6}
                  style={{
                    ...inputStyle,
                    resize: "vertical",
                    fontFamily: "monospace",
                    fontSize: 13,
                  }}
                />
              </div>
            )}

            <button
              onClick={send}
              disabled={loading}
              className="cursor-pointer"
              style={{
                padding: "10px 28px",
                backgroundColor: "var(--apple-blue)",
                color: "#ffffff",
                borderRadius: 980,
                border: "none",
                fontSize: 15,
                fontWeight: 500,
                opacity: loading ? 0.6 : 1,
                cursor: loading ? "not-allowed" : "pointer",
              }}
            >
              {loading ? "发送中..." : "发送请求"}
            </button>
          </div>
        </div>

        {/* Right: response panel */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={cardStyle}>
            <div
              className="flex items-center justify-between"
              style={{ marginBottom: 16 }}
            >
              <div
                style={{
                  fontFamily: "var(--font-display)",
                  fontSize: 17,
                  fontWeight: 600,
                  color: "#ffffff",
                }}
              >
                响应
              </div>
              <div style={{ display: "flex", gap: 16, alignItems: "center" }}>
                {statusCode !== null && (
                  <span style={{ fontSize: 13, fontWeight: 600, color: statusColor }}>
                    {statusCode === 0 ? "网络错误" : `HTTP ${statusCode}`}
                  </span>
                )}
                {elapsed !== null && (
                  <span style={{ fontSize: 12, color: "rgba(255,255,255,0.40)" }}>
                    {elapsed} ms
                  </span>
                )}
              </div>
            </div>

            {response === null ? (
              <div
                style={{
                  minHeight: 240,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  color: "rgba(255,255,255,0.24)",
                  fontSize: 14,
                }}
              >
                发送请求后在这里查看响应
              </div>
            ) : (
              <pre
                style={{
                  margin: 0,
                  padding: "16px",
                  backgroundColor: "var(--apple-surface-2)",
                  borderRadius: 8,
                  fontSize: 13,
                  lineHeight: 1.6,
                  color: "rgba(255,255,255,0.84)",
                  overflowX: "auto",
                  overflowY: "auto",
                  maxHeight: 480,
                  fontFamily: "monospace",
                  whiteSpace: "pre-wrap",
                  wordBreak: "break-all",
                }}
              >
                {response}
              </pre>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
