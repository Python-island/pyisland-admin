import { useEffect, useMemo, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import {
  sanitizeUrl,
  wallpaperAdmin,
  type WallpaperAdminItem,
} from "../api";

const pagePaddingStyle: React.CSSProperties = { padding: "48px 48px" };
const cardStyle: React.CSSProperties = {
  backgroundColor: "var(--apple-surface-1)",
  borderRadius: 12,
  padding: 24,
};
const thStyle: React.CSSProperties = {
  textAlign: "left",
  padding: "8px 10px",
  fontSize: 12,
  color: "rgba(255,255,255,0.48)",
  borderBottom: "1px solid rgba(255,255,255,0.08)",
  textTransform: "uppercase",
};
const tdStyle: React.CSSProperties = {
  padding: "10px",
  fontSize: 13,
  color: "#ffffff",
  borderBottom: "1px solid rgba(255,255,255,0.04)",
  verticalAlign: "middle",
};
const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "8px 10px",
  backgroundColor: "var(--apple-surface-2)",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 13,
  outline: "none",
};

function statusColor(status: string): string {
  const s = (status || "").toLowerCase();
  if (s === "published") return "#30d158";
  if (s === "pending") return "#ffd60a";
  if (s === "rejected" || s === "delisted") return "#ff453a";
  if (s === "resolved") return "#30d158";
  return "rgba(255,255,255,0.72)";
}

function formatDurationMs(durationMs?: number): string {
  if (!durationMs || durationMs <= 0) return "-";
  const totalSeconds = Math.floor(durationMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export default function WallpaperReview() {
  const [wallpapers, setWallpapers] = useState<WallpaperAdminItem[]>([]);
  const [loadingWallpapers, setLoadingWallpapers] = useState(true);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [reviewAction, setReviewAction] = useState<"approve" | "reject" | "delist" | "relist">("approve");
  const [reviewReason, setReviewReason] = useState("");

  const [editTitle, setEditTitle] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editTags, setEditTags] = useState("");
  const [editCopyrightInfo, setEditCopyrightInfo] = useState("");
  const [editType, setEditType] = useState("image");
  const [editStatus, setEditStatus] = useState("pending");

  const selectedWallpaper = useMemo(
    () => wallpapers.find((w) => w.id === selectedId) || null,
    [selectedId, wallpapers]
  );

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const loadWallpapers = async () => {
    setLoadingWallpapers(true);
    try {
      const res = await wallpaperAdmin.list({
        keyword: keyword.trim() || undefined,
        status: statusFilter || undefined,
        page: 1,
        pageSize: 50,
      });
      if (res.code === 200 && Array.isArray(res.data)) {
        setWallpapers(res.data);
        if (!selectedId && res.data.length > 0) {
          setSelectedId(res.data[0].id);
        }
      } else {
        showMsg(res.message || "加载壁纸失败", "err");
      }
    } catch {
      showMsg("加载壁纸失败", "err");
    } finally {
      setLoadingWallpapers(false);
    }
  };

  const deleteWallpaper = async () => {
    if (!selectedWallpaper) return;
    const confirmed = window.confirm(`确认删除壁纸「${selectedWallpaper.title || selectedWallpaper.id}」吗？`);
    if (!confirmed) return;
    try {
      const res = await wallpaperAdmin.delete(selectedWallpaper.id);
      if (res.code === 200) {
        showMsg("壁纸删除成功");
        setSelectedId(null);
        await loadWallpapers();
      } else {
        showMsg(res.message || "壁纸删除失败", "err");
      }
    } catch {
      showMsg("壁纸删除失败", "err");
    }
  };

  useEffect(() => {
    loadWallpapers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!selectedWallpaper) return;
    setEditTitle(selectedWallpaper.title || "");
    setEditDescription(selectedWallpaper.description || "");
    setEditTags(selectedWallpaper.tagsText || "");
    setEditCopyrightInfo(selectedWallpaper.copyrightInfo || "");
    setEditType(selectedWallpaper.type || "image");
    setEditStatus(selectedWallpaper.status || "pending");
  }, [selectedWallpaper]);

  const submitReview = async () => {
    if (!selectedWallpaper) return;
    try {
      const res = await wallpaperAdmin.review({
        id: selectedWallpaper.id,
        action: reviewAction,
        reason: reviewReason.trim(),
      });
      if (res.code === 200) {
        showMsg("审核操作完成");
        setReviewReason("");
        await loadWallpapers();
      } else {
        showMsg(res.message || "审核操作失败", "err");
      }
    } catch {
      showMsg("审核操作失败", "err");
    }
  };

  const saveMetadata = async () => {
    if (!selectedWallpaper) return;
    try {
      const res = await wallpaperAdmin.updateMetadata({
        id: selectedWallpaper.id,
        title: editTitle,
        description: editDescription,
        type: editType,
        tags: editTags,
        copyrightInfo: editCopyrightInfo,
        status: editStatus,
      });
      if (res.code === 200) {
        showMsg("元数据更新成功");
        await loadWallpapers();
      } else {
        showMsg(res.message || "元数据更新失败", "err");
      }
    } catch {
      showMsg("元数据更新失败", "err");
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
        壁纸审核
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
        管理壁纸元数据与审核状态
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={{ ...cardStyle, marginBottom: 16 }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 160px auto", gap: 10, marginBottom: 12 }}>
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索标题 / 作者 / 标签"
            style={inputStyle}
          />
          <select style={inputStyle} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">全部状态</option>
            <option value="pending">pending</option>
            <option value="published">published</option>
            <option value="rejected">rejected</option>
            <option value="delisted">delisted</option>
          </select>
          <button
            className="cursor-pointer"
            style={{ borderRadius: 8, border: "none", padding: "8px 14px", backgroundColor: "var(--apple-blue)", color: "#fff" }}
            onClick={loadWallpapers}
          >
            搜索
          </button>
        </div>

        {loadingWallpapers ? (
          <p style={{ color: "rgba(255,255,255,0.48)" }}>加载中...</p>
        ) : wallpapers.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)" }}>暂无壁纸</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={thStyle}>预览</th>
                  <th style={thStyle}>标题</th>
                  <th style={thStyle}>类型</th>
                  <th style={thStyle}>视频信息</th>
                  <th style={thStyle}>作者</th>
                  <th style={thStyle}>状态</th>
                  <th style={thStyle}>评分</th>
                  <th style={thStyle}>应用/下载</th>
                </tr>
              </thead>
              <tbody>
                {wallpapers.map((item) => {
                  const preview = sanitizeUrl(item.thumb320Url || item.thumb720Url || item.thumb1280Url || item.originalUrl);
                  return (
                    <tr
                      key={item.id}
                      onClick={() => setSelectedId(item.id)}
                      style={{ backgroundColor: selectedId === item.id ? "rgba(10,132,255,0.12)" : "transparent", cursor: "pointer" }}
                    >
                      <td style={tdStyle}>
                        {preview ? (
                          <img src={preview} alt={item.title} style={{ width: 96, height: 56, objectFit: "cover", borderRadius: 8 }} />
                        ) : (
                          "-"
                        )}
                      </td>
                      <td style={tdStyle}>{item.title}</td>
                      <td style={tdStyle}>{item.type || "-"}</td>
                      <td style={tdStyle}>
                        {item.type === "video"
                          ? `${formatDurationMs(item.durationMs)} / ${item.frameRate ? `${Number(item.frameRate).toFixed(2)} fps` : "-"}`
                          : "-"}
                      </td>
                      <td style={tdStyle}>{item.ownerUsername}</td>
                      <td style={tdStyle}>
                        <span style={{ color: statusColor(item.status) }}>{item.status || "-"}</span>
                      </td>
                      <td style={tdStyle}>{Number(item.ratingAvg || 0).toFixed(1)} ({item.ratingCount || 0})</td>
                      <td style={tdStyle}>{item.applyCount || 0} / {item.downloadCount || 0}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selectedWallpaper && (
        <div style={{ ...cardStyle, marginBottom: 16 }}>
          <h2 style={{ margin: "0 0 12px", color: "#fff", fontSize: 20 }}>审核与元数据</h2>
          {(() => {
            const originalUrl = sanitizeUrl(selectedWallpaper.originalUrl);
            const posterUrl = sanitizeUrl(
              selectedWallpaper.thumb1280Url || selectedWallpaper.thumb720Url || selectedWallpaper.thumb320Url
            );
            const previewUrl = posterUrl || originalUrl;
            if (!previewUrl) return null;
            return (
              <div style={{ marginBottom: 12, borderRadius: 8, overflow: "hidden", backgroundColor: "#000" }}>
                {selectedWallpaper.type === "video" && originalUrl ? (
                  <video
                    key={originalUrl}
                    src={originalUrl}
                    poster={posterUrl || undefined}
                    controls
                    playsInline
                    preload="metadata"
                    style={{ width: "100%", maxHeight: 360, objectFit: "contain", display: "block", background: "#000" }}
                  />
                ) : (
                  <img
                    src={previewUrl}
                    alt={selectedWallpaper.title}
                    style={{ width: "100%", maxHeight: 360, objectFit: "contain", display: "block", background: "#000" }}
                  />
                )}
              </div>
            );
          })()}
          <div style={{ color: "rgba(255,255,255,0.72)", fontSize: 12, marginBottom: 10 }}>
            资源类型：{selectedWallpaper.type || "-"}
            {selectedWallpaper.type === "video" && (
              <>
                {" | 时长："}
                {formatDurationMs(selectedWallpaper.durationMs)}
                {" | 帧率："}
                {selectedWallpaper.frameRate ? `${Number(selectedWallpaper.frameRate).toFixed(2)} fps` : "-"}
              </>
            )}
          </div>
          <div style={{ color: "rgba(255,255,255,0.62)", fontSize: 12, marginBottom: 10 }}>
            版权声明：{selectedWallpaper.copyrightInfo || "-"}
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 10 }}>
            <input value={editTitle} onChange={(e) => setEditTitle(e.target.value)} style={inputStyle} placeholder="标题" />
            <input value={editTags} onChange={(e) => setEditTags(e.target.value)} style={inputStyle} placeholder="标签（逗号分隔）" />
            <input
              value={editCopyrightInfo}
              onChange={(e) => setEditCopyrightInfo(e.target.value)}
              style={{ ...inputStyle, gridColumn: "1 / -1" }}
              placeholder="版权声明信息"
            />
            <textarea
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
              style={{ ...inputStyle, minHeight: 72, resize: "vertical", gridColumn: "1 / -1" }}
              placeholder="描述"
            />
            <select value={editType} onChange={(e) => setEditType(e.target.value)} style={inputStyle}>
              <option value="image">image</option>
              <option value="video">video</option>
            </select>
            <select value={editStatus} onChange={(e) => setEditStatus(e.target.value)} style={inputStyle}>
              <option value="pending">pending</option>
              <option value="published">published</option>
              <option value="rejected">rejected</option>
              <option value="delisted">delisted</option>
            </select>
          </div>
          <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
            <button
              className="cursor-pointer"
              style={{ borderRadius: 8, border: "none", padding: "8px 14px", backgroundColor: "var(--apple-blue)", color: "#fff" }}
              onClick={saveMetadata}
            >
              保存元数据
            </button>
            <button
              className="cursor-pointer"
              style={{ borderRadius: 8, border: "none", padding: "8px 14px", backgroundColor: "rgba(255,69,58,0.16)", color: "#ff453a" }}
              onClick={deleteWallpaper}
            >
              删除壁纸
            </button>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "180px 1fr auto", gap: 10 }}>
            <select value={reviewAction} onChange={(e) => setReviewAction(e.target.value as "approve" | "reject" | "delist" | "relist")} style={inputStyle}>
              <option value="approve">approve</option>
              <option value="reject">reject</option>
              <option value="delist">delist</option>
              <option value="relist">relist</option>
            </select>
            <input value={reviewReason} onChange={(e) => setReviewReason(e.target.value)} placeholder="审核备注 / 拒绝原因" style={inputStyle} />
            <button
              className="cursor-pointer"
              style={{ borderRadius: 8, border: "none", padding: "8px 14px", backgroundColor: "rgba(10,132,255,0.2)", color: "#0a84ff" }}
              onClick={submitReview}
            >
              执行审核
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
