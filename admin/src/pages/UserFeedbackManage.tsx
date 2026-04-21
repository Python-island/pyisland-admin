import { useEffect, useMemo, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import {
  issueFeedbackAdmin,
  type IssueFeedbackAdminItem,
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
  verticalAlign: "top",
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
  if (s === "resolved") return "#30d158";
  if (s === "pending") return "#ffd60a";
  if (s === "rejected") return "#ff453a";
  return "rgba(255,255,255,0.72)";
}

export default function UserFeedbackManage() {
  const [items, setItems] = useState<IssueFeedbackAdminItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [submittingId, setSubmittingId] = useState<number | null>(null);

  const [statusFilter, setStatusFilter] = useState("");
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [total, setTotal] = useState(0);

  const [draftStatuses, setDraftStatuses] = useState<Record<number, string>>({});
  const [draftReplies, setDraftReplies] = useState<Record<number, string>>({});

  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(total / pageSize)),
    [total, pageSize]
  );

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const loadList = async (targetPage = page) => {
    setLoading(true);
    try {
      const res = await issueFeedbackAdmin.list({
        status: statusFilter || undefined,
        keyword: keyword.trim() || undefined,
        page: targetPage,
        pageSize,
      });
      if (res.code === 200 && res.data) {
        const nextItems = Array.isArray(res.data.items) ? res.data.items : [];
        setItems(nextItems);
        setTotal(Number(res.data.total || 0));
        setPage(Number(res.data.page || targetPage));
        setDraftStatuses((prev) => {
          const next = { ...prev };
          nextItems.forEach((item) => {
            if (!next[item.id]) next[item.id] = item.status || "resolved";
          });
          return next;
        });
        setDraftReplies((prev) => {
          const next = { ...prev };
          nextItems.forEach((item) => {
            if (next[item.id] === undefined) next[item.id] = item.adminReply || "";
          });
          return next;
        });
      } else {
        showMsg(res.message || "加载反馈失败", "err");
      }
    } catch {
      showMsg("加载反馈失败", "err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadList(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submitResolve = async (id: number) => {
    const status = draftStatuses[id] || "resolved";
    const adminReply = (draftReplies[id] || "").trim();
    setSubmittingId(id);
    try {
      const res = await issueFeedbackAdmin.resolve({ id, status, adminReply });
      if (res.code === 200) {
        showMsg("反馈处理成功");
        await loadList(page);
      } else {
        showMsg(res.message || "反馈处理失败", "err");
      }
    } catch {
      showMsg("反馈处理失败", "err");
    } finally {
      setSubmittingId(null);
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
        用户反馈
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
        查看用户问题反馈并进行审核处理
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        <div style={{ display: "grid", gridTemplateColumns: "160px 1fr auto", gap: 10, marginBottom: 12 }}>
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={inputStyle}>
            <option value="">全部状态</option>
            <option value="pending">pending</option>
            <option value="resolved">resolved</option>
            <option value="rejected">rejected</option>
          </select>
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索用户名/标题/内容/联系方式"
            style={inputStyle}
          />
          <button
            className="cursor-pointer"
            style={{ width: 120, borderRadius: 8, border: "none", padding: "8px 14px", backgroundColor: "var(--apple-surface-2)", color: "#fff" }}
            onClick={() => loadList(1)}
          >
            查询反馈
          </button>
        </div>

        {loading ? (
          <p style={{ color: "rgba(255,255,255,0.48)" }}>加载中...</p>
        ) : items.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)" }}>暂无反馈</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={thStyle}>ID</th>
                  <th style={thStyle}>用户</th>
                  <th style={thStyle}>类型</th>
                  <th style={thStyle}>标题/内容</th>
                  <th style={thStyle}>联系方式</th>
                  <th style={thStyle}>状态</th>
                  <th style={thStyle}>处理</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td style={tdStyle}>{item.id}</td>
                    <td style={tdStyle}>{item.username}</td>
                    <td style={tdStyle}>{item.feedbackType || "-"}</td>
                    <td style={tdStyle}>
                      <div style={{ maxWidth: 420 }}>
                        <div style={{ fontWeight: 600, marginBottom: 6 }}>{item.title || "-"}</div>
                        <div style={{ whiteSpace: "pre-wrap", color: "rgba(255,255,255,0.8)" }}>{item.content || "-"}</div>
                        <div style={{ color: "rgba(255,255,255,0.48)", marginTop: 6, fontSize: 12 }}>
                          客户端版本：{item.clientVersion || "-"} / 提交时间：{item.createdAt || "-"}
                        </div>
                      </div>
                    </td>
                    <td style={tdStyle}>{item.contact || "-"}</td>
                    <td style={tdStyle}>
                      <div style={{ color: statusColor(item.status), marginBottom: 6 }}>{item.status || "-"}</div>
                      {item.resolvedAt ? (
                        <div style={{ fontSize: 12, color: "rgba(255,255,255,0.48)" }}>{item.resolvedAt}</div>
                      ) : null}
                    </td>
                    <td style={tdStyle}>
                      <div style={{ display: "grid", gap: 8, minWidth: 260 }}>
                        <select
                          value={draftStatuses[item.id] || "resolved"}
                          onChange={(e) => setDraftStatuses((prev) => ({ ...prev, [item.id]: e.target.value }))}
                          style={inputStyle}
                        >
                          <option value="resolved">resolved</option>
                          <option value="rejected">rejected</option>
                          <option value="pending">pending</option>
                        </select>
                        <input
                          value={draftReplies[item.id] || ""}
                          onChange={(e) => setDraftReplies((prev) => ({ ...prev, [item.id]: e.target.value }))}
                          placeholder="管理员回复（可选）"
                          style={inputStyle}
                        />
                        <button
                          className="cursor-pointer"
                          style={{ borderRadius: 8, border: "none", padding: "8px 12px", backgroundColor: "rgba(10,132,255,0.2)", color: "#0a84ff" }}
                          disabled={submittingId === item.id}
                          onClick={() => submitResolve(item.id)}
                        >
                          {submittingId === item.id ? "提交中..." : "提交处理"}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div style={{ marginTop: 14, display: "flex", alignItems: "center", gap: 10, color: "rgba(255,255,255,0.64)", fontSize: 13 }}>
          <span>共 {total} 条</span>
          <span>
            第 {page} / {totalPages} 页
          </span>
          <button
            className="cursor-pointer"
            style={{ borderRadius: 8, border: "none", padding: "6px 10px", backgroundColor: "var(--apple-surface-2)", color: "#fff" }}
            disabled={page <= 1 || loading}
            onClick={() => loadList(page - 1)}
          >
            上一页
          </button>
          <button
            className="cursor-pointer"
            style={{ borderRadius: 8, border: "none", padding: "6px 10px", backgroundColor: "var(--apple-surface-2)", color: "#fff" }}
            disabled={page >= totalPages || loading}
            onClick={() => loadList(page + 1)}
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  );
}
