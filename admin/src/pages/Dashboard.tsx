import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { version, clearToken, getUsername, type AppVersion } from "../api";

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
  color: "rgba(255, 255, 255, 0.64)",
};

const sectionStyle: React.CSSProperties = {
  backgroundColor: "var(--apple-surface-1)",
  borderRadius: 12,
  padding: 32,
};

const sectionHeadingStyle: React.CSSProperties = {
  fontFamily: "var(--font-display)",
  fontSize: 21,
  fontWeight: 600,
  lineHeight: 1.19,
  letterSpacing: "0.231px",
  color: "#ffffff",
  marginBottom: 24,
};

export default function Dashboard() {
  const navigate = useNavigate();
  const [searchName, setSearchName] = useState("");
  const [current, setCurrent] = useState<AppVersion | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  // form
  const [formAppName, setFormAppName] = useState("");
  const [formVersion, setFormVersion] = useState("");
  const [formDesc, setFormDesc] = useState("");
  const [formMode, setFormMode] = useState<"create" | "update">("create");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
    setTimeout(() => setMsg(""), 3000);
  };

  const handleSearch = useCallback(async () => {
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
  }, [searchName]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await version.create(formAppName, formVersion, formDesc);
      if (res.code === 200) {
        showMsg("应用创建成功");
        setSearchName(formAppName);
        setFormAppName("");
        setFormVersion("");
        setFormDesc("");
        const r = await version.get(formAppName);
        if (r.code === 200 && r.data) {
          setCurrent(r.data);
          setNotFound(false);
        }
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("创建失败", "err");
    }
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const name = formAppName || current?.appName || "";
      const res = await version.update(name, formVersion, formDesc);
      if (res.code === 200) {
        showMsg("版本更新成功");
        setCurrent(res.data!);
        setFormVersion("");
        setFormDesc("");
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("更新失败", "err");
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
        setNotFound(true);
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("删除失败", "err");
    }
  };

  const handleLogout = () => {
    clearToken();
    navigate("/login");
  };

  const fillUpdateForm = () => {
    if (!current) return;
    setFormMode("update");
    setFormAppName(current.appName);
    setFormVersion(current.version);
    setFormDesc(current.description || "");
  };

  useEffect(() => {
    if (searchName && !current) handleSearch();
  }, []);

  return (
    <div className="min-h-screen" style={{ backgroundColor: "var(--apple-black)" }}>
      {/* Navigation — Apple glass nav */}
      <header
        className="sticky top-0 z-40"
        style={{
          height: 48,
          backgroundColor: "rgba(0, 0, 0, 0.8)",
          backdropFilter: "saturate(180%) blur(20px)",
          WebkitBackdropFilter: "saturate(180%) blur(20px)",
        }}
      >
        <div
          className="flex items-center justify-between h-full mx-auto"
          style={{ maxWidth: 980, padding: "0 24px" }}
        >
          <span
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 21,
              fontWeight: 600,
              lineHeight: 1.19,
              letterSpacing: "0.231px",
              color: "#ffffff",
            }}
          >
            PyIsland
          </span>
          <div className="flex items-center" style={{ gap: 20 }}>
            <span
              style={{
                fontSize: 12,
                fontWeight: 400,
                color: "rgba(255, 255, 255, 0.8)",
              }}
            >
              {getUsername()}
            </span>
            <button
              onClick={handleLogout}
              className="cursor-pointer border-none bg-transparent"
              style={{
                fontSize: 12,
                fontWeight: 400,
                color: "var(--apple-link-dark)",
              }}
            >
              退出登录
            </button>
          </div>
        </div>
      </header>

      {/* Toast */}
      {msg && (
        <div
          className="fixed z-50"
          style={{
            top: 64,
            right: 24,
            padding: "10px 20px",
            borderRadius: 8,
            fontSize: 14,
            fontWeight: 400,
            lineHeight: 1.29,
            letterSpacing: "-0.224px",
            color: "#ffffff",
            backgroundColor:
              msgType === "ok" ? "var(--apple-blue)" : "#ff453a",
          }}
        >
          {msg}
        </div>
      )}

      <main className="mx-auto" style={{ maxWidth: 980, padding: "48px 24px" }}>
        {/* Hero */}
        <div className="text-center" style={{ marginBottom: 48 }}>
          <h1
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 56,
              fontWeight: 600,
              lineHeight: 1.07,
              letterSpacing: "-0.28px",
              color: "#ffffff",
              margin: 0,
            }}
          >
            版本管理
          </h1>
          <p
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 21,
              fontWeight: 400,
              lineHeight: 1.19,
              letterSpacing: "0.231px",
              color: "rgba(255, 255, 255, 0.56)",
              marginTop: 8,
            }}
          >
            查询、创建和管理应用版本
          </p>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
          {/* Search Section — light gray */}
          <section style={{ ...sectionStyle, backgroundColor: "var(--apple-gray)", borderRadius: 12 }}>
            <h2 style={{ ...sectionHeadingStyle, color: "var(--apple-near-black)" }}>
              查询版本
            </h2>
            <div className="flex" style={{ gap: 10 }}>
              <input
                type="text"
                value={searchName}
                onChange={(e) => setSearchName(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSearch()}
                placeholder="输入应用名称"
                style={{
                  ...inputStyle,
                  backgroundColor: "#ffffff",
                  color: "var(--apple-near-black)",
                  flex: 1,
                }}
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
                  fontWeight: 400,
                  lineHeight: 1.43,
                  letterSpacing: "-0.224px",
                  opacity: loading ? 0.5 : 1,
                  whiteSpace: "nowrap",
                }}
              >
                {loading ? "查询中..." : "查询"}
              </button>
            </div>
          </section>

          {/* Result Section — dark */}
          {current && (
            <section style={sectionStyle}>
              <div className="flex items-center justify-between" style={{ marginBottom: 24 }}>
                <h2 style={{ ...sectionHeadingStyle, marginBottom: 0 }}>版本信息</h2>
                <div className="flex" style={{ gap: 8 }}>
                  <button
                    onClick={fillUpdateForm}
                    className="cursor-pointer"
                    style={{
                      padding: "4px 14px",
                      backgroundColor: "transparent",
                      color: "var(--apple-link-dark)",
                      borderRadius: 980,
                      border: "1px solid var(--apple-link-dark)",
                      fontSize: 14,
                      lineHeight: 1.43,
                      letterSpacing: "-0.224px",
                    }}
                  >
                    编辑
                  </button>
                  <button
                    onClick={handleDelete}
                    className="cursor-pointer"
                    style={{
                      padding: "4px 14px",
                      backgroundColor: "transparent",
                      color: "#ff453a",
                      borderRadius: 980,
                      border: "1px solid #ff453a",
                      fontSize: 14,
                      lineHeight: 1.43,
                      letterSpacing: "-0.224px",
                    }}
                  >
                    删除
                  </button>
                </div>
              </div>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 20,
                }}
              >
                <InfoItem label="应用名称" value={current.appName} />
                <InfoItem label="版本号" value={current.version} />
                <InfoItem label="描述" value={current.description || "-"} />
                <InfoItem label="更新时间" value={current.updatedAt} />
              </div>
            </section>
          )}

          {notFound && !current && (
            <div
              className="text-center"
              style={{
                padding: "32px 24px",
                borderRadius: 12,
                backgroundColor: "var(--apple-surface-1)",
                color: "rgba(255, 255, 255, 0.56)",
                fontSize: 17,
                lineHeight: 1.47,
                letterSpacing: "-0.374px",
              }}
            >
              未找到该应用的版本信息
            </div>
          )}

          {/* Create / Update Form — dark */}
          <section style={sectionStyle}>
            <div className="flex" style={{ gap: 8, marginBottom: 24 }}>
              <button
                onClick={() => {
                  setFormMode("create");
                  setFormAppName("");
                  setFormVersion("");
                  setFormDesc("");
                }}
                className="cursor-pointer"
                style={{
                  padding: "6px 16px",
                  borderRadius: 980,
                  border: "none",
                  fontSize: 14,
                  lineHeight: 1.43,
                  letterSpacing: "-0.224px",
                  backgroundColor:
                    formMode === "create" ? "var(--apple-blue)" : "var(--apple-surface-2)",
                  color: formMode === "create" ? "#ffffff" : "rgba(255,255,255,0.64)",
                }}
              >
                创建应用
              </button>
              <button
                onClick={() => setFormMode("update")}
                className="cursor-pointer"
                style={{
                  padding: "6px 16px",
                  borderRadius: 980,
                  border: "none",
                  fontSize: 14,
                  lineHeight: 1.43,
                  letterSpacing: "-0.224px",
                  backgroundColor:
                    formMode === "update" ? "var(--apple-blue)" : "var(--apple-surface-2)",
                  color: formMode === "update" ? "#ffffff" : "rgba(255,255,255,0.64)",
                }}
              >
                更新版本
              </button>
            </div>

            <form onSubmit={formMode === "create" ? handleCreate : handleUpdate}>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 16,
                  marginBottom: 16,
                }}
              >
                <div>
                  <label style={labelStyle}>应用名称</label>
                  <input
                    type="text"
                    value={formAppName}
                    onChange={(e) => setFormAppName(e.target.value)}
                    placeholder="如 pyisland"
                    required={formMode === "create"}
                    style={inputStyle}
                  />
                </div>
                <div>
                  <label style={labelStyle}>版本号</label>
                  <input
                    type="text"
                    value={formVersion}
                    onChange={(e) => setFormVersion(e.target.value)}
                    placeholder="如 1.0.0"
                    required
                    style={inputStyle}
                  />
                </div>
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
                  lineHeight: 1,
                  letterSpacing: "normal",
                }}
              >
                {formMode === "create" ? "创建" : "更新"}
              </button>
            </form>
          </section>
        </div>
      </main>
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
          color: "rgba(255, 255, 255, 0.48)",
          textTransform: "uppercase",
        }}
      >
        {label}
      </span>
      <p
        style={{
          marginTop: 4,
          fontSize: 17,
          lineHeight: 1.47,
          letterSpacing: "-0.374px",
          color: "#ffffff",
        }}
      >
        {value}
      </p>
    </div>
  );
}
