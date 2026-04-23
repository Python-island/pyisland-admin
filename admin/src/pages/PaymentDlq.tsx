import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { paymentAdmin, type PaymentDlqAdminItem } from "../api";

const cardStyle: React.CSSProperties = {
  backgroundColor: "var(--apple-surface-1)",
  borderRadius: 12,
  padding: 32,
};

const inputStyle: React.CSSProperties = {
  padding: "8px 12px",
  backgroundColor: "var(--apple-surface-2)",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 14,
  outline: "none",
};

const thStyle: React.CSSProperties = {
  textAlign: "left",
  padding: "8px 12px",
  fontSize: 12,
  fontWeight: 600,
  lineHeight: 1.33,
  letterSpacing: "-0.12px",
  color: "rgba(255,255,255,0.48)",
  textTransform: "uppercase",
  borderBottom: "1px solid rgba(255,255,255,0.08)",
};

const tdStyle: React.CSSProperties = {
  padding: "10px 12px",
  fontSize: 13,
  lineHeight: 1.43,
  letterSpacing: "-0.2px",
  color: "#ffffff",
  borderBottom: "1px solid rgba(255,255,255,0.04)",
  verticalAlign: "middle",
};

function trimOrUndefined(value: string): string | undefined {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : undefined;
}

export default function PaymentDlq() {
  const [rows, setRows] = useState<PaymentDlqAdminItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [notifyId, setNotifyId] = useState("");
  const [outTradeNo, setOutTradeNo] = useState("");
  const [limit, setLimit] = useState(50);

  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchRows = async () => {
    setLoading(true);
    try {
      const res = await paymentAdmin.listDlq({
        notifyId: trimOrUndefined(notifyId),
        outTradeNo: trimOrUndefined(outTradeNo),
        limit,
      });
      if (res.code === 200 && Array.isArray(res.data)) {
        setRows(res.data);
      } else {
        showMsg(res.message || "加载支付 DLQ 失败", "err");
      }
    } catch {
      showMsg("加载支付 DLQ 失败", "err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRows();
  }, []);

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
        支付 DLQ 管理
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
        查询支付通知死信记录，可按 notifyId/outTradeNo 检索
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        <div style={{ display: "grid", gridTemplateColumns: "2fr 2fr 1fr auto", gap: 10, marginBottom: 16 }}>
          <input
            placeholder="notifyId"
            value={notifyId}
            onChange={(e) => setNotifyId(e.target.value)}
            style={inputStyle}
          />
          <input
            placeholder="outTradeNo"
            value={outTradeNo}
            onChange={(e) => setOutTradeNo(e.target.value)}
            style={inputStyle}
          />
          <input
            type="number"
            min={1}
            max={200}
            value={limit}
            onChange={(e) => setLimit(Math.max(1, Math.min(200, Number(e.target.value || 50))))}
            style={inputStyle}
          />
          <button
            onClick={fetchRows}
            className="cursor-pointer"
            style={{
              padding: "8px 16px",
              backgroundColor: "var(--apple-blue)",
              color: "#fff",
              borderRadius: 980,
              border: "none",
              fontSize: 14,
            }}
          >
            查询
          </button>
        </div>

        {loading ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>加载中...</p>
        ) : rows.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无支付 DLQ 记录</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={thStyle}>notifyId</th>
                  <th style={thStyle}>outTradeNo</th>
                  <th style={thStyle}>tradeState</th>
                  <th style={thStyle}>retry</th>
                  <th style={thStyle}>error</th>
                  <th style={thStyle}>createdAt</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td style={tdStyle}>{row.notifyId || "-"}</td>
                    <td style={tdStyle}>{row.outTradeNo || "-"}</td>
                    <td style={tdStyle}>{row.tradeState || "-"}</td>
                    <td style={tdStyle}>{typeof row.retryCount === "number" ? row.retryCount : "-"}</td>
                    <td style={{ ...tdStyle, maxWidth: 360, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{row.errorMessage || "-"}</td>
                    <td style={tdStyle}>{row.createdAt || "-"}</td>
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
