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

export default function VersionDelete() {
  const [searchName, setSearchName] = useState("");
  const [current, setCurrent] = useState<AppVersion | null>(null);
  const [notFound, setNotFound] = useState(false);
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

  const handleDelete = async () => {
    if (!current) return;
    if (!confirm(`确定删除 ${current.appName} 吗？`)) return;
    try {
      const res = await version.delete(current.appName);
      if (res.code === 200) {
        showMsg("删除成功");
        setCurrent(null);
        setSearchName("");
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("删除失败", "err");
    }
  };

  return (
    <div style={{ padding: "48px 48px", maxWidth: 720 }}>
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
        删除版本
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
        搜索并删除应用版本
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
              display: "grid",
              gridTemplateColumns: "1fr 1fr",
              gap: 20,
              marginBottom: 24,
            }}
          >
            <InfoItem label="应用名称" value={current.appName} />
            <InfoItem label="版本号" value={current.version} />
            <InfoItem label="描述" value={current.description || "-"} />
            <InfoItem label="更新时间" value={current.updatedAt} />
          </div>
          <button
            onClick={handleDelete}
            className="cursor-pointer"
            style={{
              padding: "8px 20px",
              backgroundColor: "transparent",
              color: "#ff453a",
              borderRadius: 980,
              border: "1px solid #ff453a",
              fontSize: 14,
              lineHeight: 1.43,
              letterSpacing: "-0.224px",
            }}
          >
            确认删除
          </button>
        </div>
      )}
    </div>
  );
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span
        style={{
          display: "block",
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.33,
          letterSpacing: "-0.12px",
          color: "rgba(255,255,255,0.48)",
          textTransform: "uppercase",
        }}
      >
        {label}
      </span>
      <p style={{ marginTop: 4, fontSize: 17, color: "#ffffff" }}>{value}</p>
    </div>
  );
}
