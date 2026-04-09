import { useState, useEffect } from "react";
import { version, type AppVersion } from "../api";
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
  padding: "20px 24px",
  cursor: "pointer",
  transition: "all 0.15s",
};

const cardSelectedStyle: React.CSSProperties = {
  ...cardStyle,
  outline: "2px solid var(--apple-blue)",
};

export default function VersionUpdate() {
  const [versions, setVersions] = useState<AppVersion[]>([]);
  const [selected, setSelected] = useState<AppVersion | null>(null);
  const [formVersion, setFormVersion] = useState("");
  const [formDesc, setFormDesc] = useState("");
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const [loading, setLoading] = useState(true);

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchVersions = async () => {
    try {
      const res = await version.list();
      if (res.code === 200 && res.data) setVersions(res.data);
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchVersions();
  }, []);

  const handleSelect = (v: AppVersion) => {
    setSelected(v);
    setFormVersion(v.version);
    setFormDesc(v.description || "");
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selected) return;
    try {
      const res = await version.update(selected.appName, formVersion, formDesc);
      if (res.code === 200) {
        showMsg("版本更新成功");
        setSelected(res.data!);
        fetchVersions();
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
        选择应用并更新版本号
      </p>

      <MessageDialog
        visible={!!msg}
        type={msgType}
        message={msg}
        onClose={() => setMsg("")}
      />

      {loading ? (
        <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>加载中...</p>
      ) : versions.length === 0 ? (
        <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无版本数据</p>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
            gap: 16,
            marginBottom: 32,
          }}
        >
          {versions.map((v) => (
            <div
              key={v.id}
              onClick={() => handleSelect(v)}
              style={
                selected?.id === v.id ? cardSelectedStyle : cardStyle
              }
            >
              <div
                style={{
                  fontSize: 17,
                  fontWeight: 600,
                  color: "#ffffff",
                  marginBottom: 6,
                }}
              >
                {v.appName}
              </div>
              <div
                style={{
                  fontSize: 14,
                  color: "rgba(255,255,255,0.64)",
                  marginBottom: 4,
                }}
              >
                版本：{v.version}
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: "rgba(255,255,255,0.4)",
                }}
              >
                {v.description || "无描述"} · {v.updatedAt}
              </div>
            </div>
          ))}
        </div>
      )}

      {selected && (
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
            正在编辑
          </div>
          <div style={{ fontSize: 17, color: "#fff", marginBottom: 24 }}>
            {selected.appName} — {selected.version}
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
