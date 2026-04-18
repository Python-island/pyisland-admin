import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { wallpaperAdmin, type WallpaperReportItem } from "../api";

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
  if (s === "resolved") return "#30d158";
  if (s === "pending") return "#ffd60a";
  if (s === "rejected") return "#ff453a";
  return "rgba(255,255,255,0.72)";
}

export default function WallpaperReportManage() {
  const [reports, setReports] = useState<WallpaperReportItem[]>([]);
  const [loadingReports, setLoadingReports] = useState(true);
  const [reportStatusFilter, setReportStatusFilter] = useState("");
  const [reportResolveStatus, setReportResolveStatus] = useState("resolved");
  const [reportResolveNote, setReportResolveNote] = useState("");
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const loadReports = async () => {
    setLoadingReports(true);
    try {
      const res = await wallpaperAdmin.reports({
        status: reportStatusFilter || undefined,
        page: 1,
        pageSize: 50,
      });
      if (res.code === 200 && Array.isArray(res.data)) {
        setReports(res.data);
      } else {
        showMsg(res.message || "加载举报失败", "err");
      }
    } catch {
      showMsg("加载举报失败", "err");
    } finally {
      setLoadingReports(false);
    }
  };

  useEffect(() => {
    loadReports();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const resolveReport = async (id: number) => {
    try {
      const res = await wallpaperAdmin.resolveReport({
        id,
        status: reportResolveStatus,
        resolutionNote: reportResolveNote.trim(),
      });
      if (res.code === 200) {
        showMsg("举报处理成功");
        setReportResolveNote("");
        await loadReports();
      } else {
        showMsg(res.message || "举报处理失败", "err");
      }
    } catch {
      showMsg("举报处理失败", "err");
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
        举报处理
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
        集中处理壁纸举报记录与状态
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        <div style={{ display: "grid", gridTemplateColumns: "160px auto", gap: 10, marginBottom: 12 }}>
          <select value={reportStatusFilter} onChange={(e) => setReportStatusFilter(e.target.value)} style={inputStyle}>
            <option value="">全部状态</option>
            <option value="pending">pending</option>
            <option value="resolved">resolved</option>
            <option value="rejected">rejected</option>
          </select>
          <button
            className="cursor-pointer"
            style={{ width: 120, borderRadius: 8, border: "none", padding: "8px 14px", backgroundColor: "var(--apple-surface-2)", color: "#fff" }}
            onClick={loadReports}
          >
            刷新举报
          </button>
        </div>

        {loadingReports ? (
          <p style={{ color: "rgba(255,255,255,0.48)" }}>加载中...</p>
        ) : reports.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)" }}>暂无举报</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={thStyle}>ID</th>
                  <th style={thStyle}>壁纸ID</th>
                  <th style={thStyle}>举报人</th>
                  <th style={thStyle}>原因</th>
                  <th style={thStyle}>状态</th>
                  <th style={thStyle}>处理</th>
                </tr>
              </thead>
              <tbody>
                {reports.map((item) => (
                  <tr key={item.id}>
                    <td style={tdStyle}>{item.id}</td>
                    <td style={tdStyle}>{item.wallpaperId}</td>
                    <td style={tdStyle}>{item.reporterUsername}</td>
                    <td style={tdStyle}>{item.reasonType}{item.reasonDetail ? ` / ${item.reasonDetail}` : ""}</td>
                    <td style={tdStyle}><span style={{ color: statusColor(item.status) }}>{item.status || "-"}</span></td>
                    <td style={tdStyle}>
                      <div style={{ display: "grid", gridTemplateColumns: "140px 1fr auto", gap: 8 }}>
                        <select value={reportResolveStatus} onChange={(e) => setReportResolveStatus(e.target.value)} style={inputStyle}>
                          <option value="resolved">resolved</option>
                          <option value="rejected">rejected</option>
                        </select>
                        <input
                          value={reportResolveNote}
                          onChange={(e) => setReportResolveNote(e.target.value)}
                          placeholder="处理说明"
                          style={inputStyle}
                        />
                        <button
                          className="cursor-pointer"
                          style={{ borderRadius: 8, border: "none", padding: "8px 12px", backgroundColor: "rgba(10,132,255,0.2)", color: "#0a84ff" }}
                          onClick={() => resolveReport(item.id)}
                        >
                          提交
                        </button>
                      </div>
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
