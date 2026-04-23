import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import {
  announcementAdmin,
  type AnnouncementConfigData,
} from "../api";

const pagePaddingStyle: React.CSSProperties = { padding: "48px 48px" };
const cardStyle: React.CSSProperties = {
  backgroundColor: "var(--apple-surface-1)",
  borderRadius: 12,
  padding: 24,
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
const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "10px 12px",
  backgroundColor: "var(--apple-surface-2)",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 13,
  outline: "none",
};

const toDateTimeLocal = (raw?: string | null): string => {
  if (!raw) return "";
  const normalized = raw.replace(" ", "T");
  const d = new Date(normalized);
  if (Number.isNaN(d.getTime())) return "";
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hh = String(d.getHours()).padStart(2, "0");
  const mi = String(d.getMinutes()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}T${hh}:${mi}`;
};

const toIsoSeconds = (localValue: string): string | null => {
  const v = localValue.trim();
  if (!v) return null;
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(v)) return v;
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(v)) return `${v}:00`;
  return v;
};

export default function AnnouncementManage() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [enabled, setEnabled] = useState(false);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [startAt, setStartAt] = useState("");
  const [endAt, setEndAt] = useState("");
  const [updatedBy, setUpdatedBy] = useState("");
  const [updatedAt, setUpdatedAt] = useState("");

  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const applyConfig = (cfg: AnnouncementConfigData) => {
    setEnabled(!!cfg.enabled);
    setTitle(cfg.title || "");
    setContent(cfg.content || "");
    setStartAt(toDateTimeLocal(cfg.startAt));
    setEndAt(toDateTimeLocal(cfg.endAt));
    setUpdatedBy(cfg.updatedBy || "");
    setUpdatedAt(cfg.updatedAt || "");
  };

  const load = async () => {
    setLoading(true);
    try {
      const res = await announcementAdmin.getConfig();
      if (res.code === 200 && res.data) {
        applyConfig(res.data);
      } else {
        showMsg(res.message || "加载公告配置失败", "err");
      }
    } catch (e: any) {
      showMsg(e?.message || "加载公告配置失败", "err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load().catch(() => {});
  }, []);

  const handleSave = async () => {
    if (saving) return;
    if (enabled && !title.trim() && !content.trim()) {
      showMsg("启用公告时标题和内容不能同时为空", "err");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        enabled,
        title: title.trim(),
        content: content.trim(),
        startAt: toIsoSeconds(startAt),
        endAt: toIsoSeconds(endAt),
      };
      const res = await announcementAdmin.updateConfig(payload);
      if (res.code === 200 && res.data) {
        applyConfig(res.data);
        showMsg("公告配置已保存");
      } else {
        showMsg(res.message || "保存公告配置失败", "err");
      }
    } catch (e: any) {
      showMsg(e?.message || "保存公告配置失败", "err");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={pagePaddingStyle}>
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
        公告管理
      </h1>
      <p
        style={{
          fontSize: 21,
          fontWeight: 400,
          lineHeight: 1.19,
          color: "rgba(255,255,255,0.56)",
          marginBottom: 24,
        }}
      >
        配置客户端公告内容与生效时间窗口
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        {loading ? (
          <p style={{ color: "rgba(255,255,255,0.56)" }}>加载中...</p>
        ) : (
          <>
            <div style={{ display: "grid", gap: 16 }}>
              <label style={{ display: "flex", alignItems: "center", gap: 10, color: "#fff", fontSize: 14 }}>
                <input
                  type="checkbox"
                  checked={enabled}
                  onChange={(e) => setEnabled(e.target.checked)}
                />
                启用公告下发
              </label>

              <div>
                <label style={labelStyle}>公告标题</label>
                <input
                  style={inputStyle}
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="例如：服务维护通知"
                />
              </div>

              <div>
                <label style={labelStyle}>公告内容</label>
                <textarea
                  style={{ ...inputStyle, minHeight: 160, resize: "vertical", fontFamily: "inherit" }}
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  placeholder="输入要下发给客户端展示的公告正文"
                />
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div>
                  <label style={labelStyle}>开始时间（可选）</label>
                  <input
                    type="datetime-local"
                    style={inputStyle}
                    value={startAt}
                    onChange={(e) => setStartAt(e.target.value)}
                  />
                </div>
                <div>
                  <label style={labelStyle}>结束时间（可选）</label>
                  <input
                    type="datetime-local"
                    style={inputStyle}
                    value={endAt}
                    onChange={(e) => setEndAt(e.target.value)}
                  />
                </div>
              </div>

              <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
                <button
                  className="cursor-pointer"
                  style={{
                    borderRadius: 8,
                    border: "none",
                    padding: "10px 16px",
                    backgroundColor: "rgba(10,132,255,0.2)",
                    color: "#0a84ff",
                  }}
                  disabled={saving}
                  onClick={handleSave}
                >
                  {saving ? "保存中..." : "保存配置"}
                </button>
                <button
                  className="cursor-pointer"
                  style={{
                    borderRadius: 8,
                    border: "none",
                    padding: "10px 16px",
                    backgroundColor: "var(--apple-surface-2)",
                    color: "#fff",
                  }}
                  disabled={loading || saving}
                  onClick={() => load().catch(() => {})}
                >
                  刷新
                </button>
              </div>

              <div style={{ color: "rgba(255,255,255,0.48)", fontSize: 12 }}>
                最后更新：{updatedAt || "-"} {updatedBy ? `（${updatedBy}）` : ""}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
