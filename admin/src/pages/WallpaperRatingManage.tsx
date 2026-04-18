import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { sanitizeUrl, wallpaperAdmin, type WallpaperAdminItem, type WallpaperRatingItem } from "../api";

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
  return "rgba(255,255,255,0.72)";
}

export default function WallpaperRatingManage() {
  const [wallpapers, setWallpapers] = useState<WallpaperAdminItem[]>([]);
  const [ratings, setRatings] = useState<WallpaperRatingItem[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [loadingWallpapers, setLoadingWallpapers] = useState(true);
  const [loadingRatings, setLoadingRatings] = useState(false);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

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

  const loadRatings = async (wallpaperId: number) => {
    setLoadingRatings(true);
    try {
      const res = await wallpaperAdmin.ratings(wallpaperId, 1, 50);
      if (res.code === 200 && Array.isArray(res.data)) {
        setRatings(res.data);
      } else {
        showMsg(res.message || "加载评分失败", "err");
      }
    } catch {
      showMsg("加载评分失败", "err");
    } finally {
      setLoadingRatings(false);
    }
  };

  useEffect(() => {
    loadWallpapers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!selectedId) {
      setRatings([]);
      return;
    }
    loadRatings(selectedId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  const removeRating = async (id: number) => {
    if (!selectedId) return;
    try {
      const res = await wallpaperAdmin.deleteRating(id, selectedId);
      if (res.code === 200) {
        showMsg("评分删除成功");
        await loadRatings(selectedId);
        await loadWallpapers();
      } else {
        showMsg(res.message || "评分删除失败", "err");
      }
    } catch {
      showMsg("评分删除失败", "err");
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
        评分管理
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
        按壁纸查看与管理用户评分记录
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
                  <th style={thStyle}>作者</th>
                  <th style={thStyle}>状态</th>
                  <th style={thStyle}>评分</th>
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
                      <td style={tdStyle}>{item.ownerUsername}</td>
                      <td style={tdStyle}>
                        <span style={{ color: statusColor(item.status) }}>{item.status || "-"}</span>
                      </td>
                      <td style={tdStyle}>{Number(item.ratingAvg || 0).toFixed(1)} ({item.ratingCount || 0})</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div style={cardStyle}>
        <h2 style={{ margin: "0 0 12px", color: "#fff", fontSize: 20 }}>评分记录</h2>
        {!selectedId ? (
          <p style={{ color: "rgba(255,255,255,0.48)" }}>请先在上方选择壁纸</p>
        ) : loadingRatings ? (
          <p style={{ color: "rgba(255,255,255,0.48)" }}>加载中...</p>
        ) : ratings.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)" }}>暂无评分记录</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={thStyle}>ID</th>
                  <th style={thStyle}>用户</th>
                  <th style={thStyle}>分数</th>
                  <th style={thStyle}>更新时间</th>
                  <th style={thStyle}>操作</th>
                </tr>
              </thead>
              <tbody>
                {ratings.map((item) => (
                  <tr key={item.id}>
                    <td style={tdStyle}>{item.id}</td>
                    <td style={tdStyle}>{item.username}</td>
                    <td style={tdStyle}>{item.score}</td>
                    <td style={tdStyle}>{item.updatedAt || item.createdAt || "-"}</td>
                    <td style={tdStyle}>
                      <button
                        className="cursor-pointer"
                        style={{ borderRadius: 8, border: "none", padding: "6px 12px", backgroundColor: "rgba(255,69,58,0.16)", color: "#ff453a" }}
                        onClick={() => removeRating(item.id)}
                      >
                        删除
                      </button>
                    </td>
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
