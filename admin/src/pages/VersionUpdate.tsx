import { useState } from "react";
import { version, type AppVersion } from "../api";

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

export default function VersionUpdate() {
  const [searchName, setSearchName] = useState("");
  const [current, setCurrent] = useState<AppVersion | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [formVersion, setFormVersion] = useState("");
  const [formDesc, setFormDesc] = useState("");
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const [loading, setLoading] = useState(false);

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
    setTimeout(() => setMsg(""), 3000);
  };

  const handleSearch = async () => {
    if (!searchName.trim()) return;
    setLoading(true);
    try {
      const res = await version.get(searchName.trim());
      if (res.code === 200 && res.data) {
        setCurrent(res.data);
        setFormVersion(res.data.version);
        setFormDesc(res.data.description || "");
        setNotFound(false);
      } else {
        setCurrent(null);
        setNotFound(true);
      }
    } catch {
      showMsg("查询失败", "err");
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!current) return;
    try {
      const res = await version.update(current.appName, formVersion, formDesc);
      if (res.code === 200) {
        showMsg("版本更新成功");
        setCurrent(res.data!);
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("更新失败", "err");
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
        更新版本
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
        搜索应用并更新版本号
      </p>

      {/* Toast */}
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

      {/* Search */}
      <div
        style={{
          backgroundColor: "var(--apple-surface-1)",
          borderRadius: 12,
          padding: 32,
          marginBottom: 24,
        }}
      >
        <div className="flex" style={{ gap: 10 }}>
          <input
            type="text"
            value={searchName}
            onChange={(e) => setSearchName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            placeholder="输入应用名称"
            style={{ ...inputStyle, flex: 1 }}
          />
          <button
            onClick={handleSearch}
            disabled={loading}
            className="cursor-pointer"
            style={{
              padding: "8px 20px",
              backgroundColor: "var(--apple-blue)",
              color: "#ffffff",
              borderRadius: 980,
              border: "none",
              fontSize: 14,
              opacity: loading ? 0.5 : 1,
              whiteSpace: "nowrap",
            }}
          >
            {loading ? "查询中..." : "查询"}
          </button>
        </div>
      </div>

      {notFound && !current && (
        <div
          className="text-center"
          style={{
            padding: "24px",
            borderRadius: 12,
            backgroundColor: "var(--apple-surface-1)",
            color: "rgba(255,255,255,0.56)",
            fontSize: 14,
            marginBottom: 24,
          }}
        >
          未找到该应用的版本信息
        </div>
      )}

      {current && (
        <div
          style={{
            backgroundColor: "var(--apple-surface-1)",
            borderRadius: 12,
            padding: 32,
          }}
        >
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: "rgba(255,255,255,0.48)",
              textTransform: "uppercase",
              letterSpacing: "-0.12px",
              marginBottom: 4,
            }}
          >
            当前版本
          </div>
          <div style={{ fontSize: 17, color: "#fff", marginBottom: 24 }}>
            {current.appName} — {current.version}
          </div>

          <form onSubmit={handleUpdate}>
            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>新版本号</label>
              <input
                type="text"
                value={formVersion}
                onChange={(e) => setFormVersion(e.target.value)}
                placeholder="如 2.0.0"
                required
                style={inputStyle}
              />
            </div>
            <div style={{ marginBottom: 24 }}>
              <label style={labelStyle}>描述</label>
              <input
                type="text"
                value={formDesc}
                onChange={(e) => setFormDesc(e.target.value)}
                placeholder="版本描述（可选）"
                style={inputStyle}
              />
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
              更新
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
