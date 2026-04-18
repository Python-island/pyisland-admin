import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import ConfirmDialog from "../components/ConfirmDialog";
import {
  wallpaperTagAdmin,
  type WallpaperTagAdminItem,
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
const btnStyle: React.CSSProperties = {
  padding: "6px 12px",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 12,
  cursor: "pointer",
  backgroundColor: "var(--apple-surface-2)",
};

const PAGE_SIZE = 20;

export default function TagManage() {
  const [items, setItems] = useState<WallpaperTagAdminItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [enabledFilter, setEnabledFilter] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState("");
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

  const load = async (targetPage = page) => {
    setLoading(true);
    try {
      const params: {
        keyword?: string;
        enabled?: number;
        page?: number;
        pageSize?: number;
      } = { page: targetPage, pageSize: PAGE_SIZE };
      if (keyword.trim()) params.keyword = keyword.trim();
      if (enabledFilter !== "") params.enabled = Number(enabledFilter);
      const res = await wallpaperTagAdmin.list(params);
      if (res.code === 200 && res.data) {
        setItems(res.data.items || []);
        setTotal(res.data.total || 0);
      } else {
        setMsg(res.message || "加载失败");
        setMsgType("err");
      }
    } catch (e: any) {
      setMsg(e?.message || "加载失败");
      setMsgType("err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(1).catch(() => {});
    setPage(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabledFilter]);

  const handleSearch = () => {
    setPage(1);
    load(1).catch(() => {});
  };

  const startEdit = (tag: WallpaperTagAdminItem) => {
    setEditingId(tag.id);
    setEditingName(tag.name);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditingName("");
  };

  const saveEdit = async () => {
    if (editingId == null || !editingName.trim()) return;
    try {
      const res = await wallpaperTagAdmin.updateName(editingId, editingName.trim());
      if (res.code === 200) {
        setMsg("已更新");
        setMsgType("ok");
        setEditingId(null);
        setEditingName("");
        await load();
      } else {
        setMsg(res.message || "更新失败");
        setMsgType("err");
      }
    } catch (e: any) {
      setMsg(e?.message || "更新失败");
      setMsgType("err");
    }
  };

  const toggleEnabled = async (tag: WallpaperTagAdminItem) => {
    const currentEnabled = Boolean(
      typeof tag.enabled === "number" ? tag.enabled : tag.enabled
    );
    try {
      const res = await wallpaperTagAdmin.setEnabled(tag.id, !currentEnabled);
      if (res.code === 200) {
        setMsg(!currentEnabled ? "已启用" : "已禁用");
        setMsgType("ok");
        await load();
      } else {
        setMsg(res.message || "操作失败");
        setMsgType("err");
      }
    } catch (e: any) {
      setMsg(e?.message || "操作失败");
      setMsgType("err");
    }
  };

  const handleDelete = async () => {
    if (confirmDeleteId == null) return;
    try {
      const res = await wallpaperTagAdmin.deleteTag(confirmDeleteId);
      if (res.code === 200) {
        setMsg("已删除");
        setMsgType("ok");
        await load();
      } else {
        setMsg(res.message || "删除失败");
        setMsgType("err");
      }
    } catch (e: any) {
      setMsg(e?.message || "删除失败");
      setMsgType("err");
    } finally {
      setConfirmDeleteId(null);
    }
  };

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div style={pagePaddingStyle}>
      <h1 style={{ color: "#ffffff", fontSize: 20, marginBottom: 16 }}>
        标签管理
      </h1>

      <div style={{ ...cardStyle, marginBottom: 16 }}>
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 160px auto auto",
            gap: 8,
            alignItems: "center",
          }}
        >
          <input
            style={inputStyle}
            placeholder="按名称或 slug 搜索"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSearch();
            }}
          />
          <select
            style={inputStyle}
            value={enabledFilter}
            onChange={(e) => setEnabledFilter(e.target.value)}
          >
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">禁用</option>
          </select>
          <button style={btnStyle} onClick={handleSearch} disabled={loading}>
            {loading ? "加载中…" : "搜索"}
          </button>
          <button
            style={btnStyle}
            onClick={() => {
              setKeyword("");
              setEnabledFilter("");
              setPage(1);
              load(1).catch(() => {});
            }}
          >
            重置
          </button>
        </div>
      </div>

      <div style={cardStyle}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr>
              <th style={thStyle}>ID</th>
              <th style={thStyle}>名称</th>
              <th style={thStyle}>Slug</th>
              <th style={thStyle}>创建者</th>
              <th style={thStyle}>使用数</th>
              <th style={thStyle}>状态</th>
              <th style={thStyle}>操作</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 && !loading ? (
              <tr>
                <td
                  style={{
                    ...tdStyle,
                    textAlign: "center",
                    color: "rgba(255,255,255,0.48)",
                  }}
                  colSpan={7}
                >
                  暂无标签
                </td>
              </tr>
            ) : (
              items.map((tag) => {
                const isEnabled = Boolean(
                  typeof tag.enabled === "number" ? tag.enabled : tag.enabled
                );
                return (
                  <tr key={tag.id}>
                    <td style={tdStyle}>{tag.id}</td>
                    <td style={tdStyle}>
                      {editingId === tag.id ? (
                        <input
                          style={inputStyle}
                          value={editingName}
                          onChange={(e) => setEditingName(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") saveEdit();
                            if (e.key === "Escape") cancelEdit();
                          }}
                          autoFocus
                        />
                      ) : (
                        tag.name
                      )}
                    </td>
                    <td
                      style={{ ...tdStyle, color: "rgba(255,255,255,0.6)" }}
                    >
                      {tag.slug}
                    </td>
                    <td
                      style={{ ...tdStyle, color: "rgba(255,255,255,0.72)" }}
                    >
                      {tag.creatorUsername || "-"}
                    </td>
                    <td style={tdStyle}>{tag.usageCount}</td>
                    <td
                      style={{
                        ...tdStyle,
                        color: isEnabled ? "#30d158" : "#ff453a",
                      }}
                    >
                      {isEnabled ? "启用" : "禁用"}
                    </td>
                    <td style={tdStyle}>
                      <div style={{ display: "flex", gap: 8 }}>
                        {editingId === tag.id ? (
                          <>
                            <button style={btnStyle} onClick={saveEdit}>
                              保存
                            </button>
                            <button style={btnStyle} onClick={cancelEdit}>
                              取消
                            </button>
                          </>
                        ) : (
                          <>
                            <button
                              style={btnStyle}
                              onClick={() => startEdit(tag)}
                            >
                              重命名
                            </button>
                            <button
                              style={btnStyle}
                              onClick={() => toggleEnabled(tag)}
                            >
                              {isEnabled ? "禁用" : "启用"}
                            </button>
                            <button
                              style={{ ...btnStyle, color: "#ff453a" }}
                              onClick={() => setConfirmDeleteId(tag.id)}
                            >
                              删除
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>

        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            marginTop: 16,
            color: "rgba(255,255,255,0.56)",
            fontSize: 12,
          }}
        >
          <span>
            共 {total} 条 · 第 {page} / {totalPages} 页
          </span>
          <div style={{ display: "flex", gap: 8 }}>
            <button
              style={btnStyle}
              disabled={page <= 1 || loading}
              onClick={() => {
                const next = Math.max(1, page - 1);
                setPage(next);
                load(next).catch(() => {});
              }}
            >
              上一页
            </button>
            <button
              style={btnStyle}
              disabled={page >= totalPages || loading}
              onClick={() => {
                const next = Math.min(totalPages, page + 1);
                setPage(next);
                load(next).catch(() => {});
              }}
            >
              下一页
            </button>
          </div>
        </div>
      </div>

      <MessageDialog
        visible={!!msg}
        type={msgType}
        message={msg}
        onClose={() => setMsg("")}
      />
      <ConfirmDialog
        visible={confirmDeleteId !== null}
        title="删除标签"
        message="确认删除该标签？所有引用关系会被一并清除。"
        confirmText="确认删除"
        cancelText="取消"
        danger
        onConfirm={handleDelete}
        onCancel={() => setConfirmDeleteId(null)}
      />
    </div>
  );
}
