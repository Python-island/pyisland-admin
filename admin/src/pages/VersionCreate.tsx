import { useState } from "react";
import { version } from "../api";

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

export default function VersionCreate() {
  const [appName, setAppName] = useState("");
  const [ver, setVer] = useState("");
  const [desc, setDesc] = useState("");
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
    setTimeout(() => setMsg(""), 3000);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await version.create(appName, ver, desc);
      if (res.code === 200) {
        showMsg("应用创建成功");
        setAppName("");
        setVer("");
        setDesc("");
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("创建失败", "err");
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
        创建应用
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
        注册新的应用版本
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

      <div
        style={{
          backgroundColor: "var(--apple-surface-1)",
          borderRadius: 12,
          padding: 32,
        }}
      >
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 16 }}>
            <label style={labelStyle}>应用名称</label>
            <input
              type="text"
              value={appName}
              onChange={(e) => setAppName(e.target.value)}
              placeholder="如 pyisland"
              required
              style={inputStyle}
            />
          </div>
          <div style={{ marginBottom: 16 }}>
            <label style={labelStyle}>版本号</label>
            <input
              type="text"
              value={ver}
              onChange={(e) => setVer(e.target.value)}
              placeholder="如 1.0.0"
              required
              style={inputStyle}
            />
          </div>
          <div style={{ marginBottom: 24 }}>
            <label style={labelStyle}>描述</label>
            <input
              type="text"
              value={desc}
              onChange={(e) => setDesc(e.target.value)}
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
            创建
          </button>
        </form>
      </div>
    </div>
  );
}
