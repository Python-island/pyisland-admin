import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { paymentAdmin, type PaymentOrderAdminItem } from "../api";

const cardStyle: React.CSSProperties = {
  backgroundColor: "var(--apple-surface-1)",
  borderRadius: 12,
  padding: 32,
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

const inputStyle: React.CSSProperties = {
  padding: "8px 12px",
  backgroundColor: "var(--apple-surface-2)",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 14,
  outline: "none",
};

function moneyFenToYuan(fen?: number) {
  if (typeof fen !== "number") return "-";
  return (fen / 100).toFixed(2);
}

function statusColor(status: string) {
  if (status === "SUCCESS") return "#30d158";
  if (status === "PAYING") return "#0a84ff";
  if (status === "CLOSED") return "#ff9f0a";
  if (status === "FAILED") return "#ff453a";
  return "rgba(255,255,255,0.64)";
}

export default function PaymentOrders() {
  const [orders, setOrders] = useState<PaymentOrderAdminItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyMap, setBusyMap] = useState<Record<string, boolean>>({});

  const [username, setUsername] = useState("");
  const [status, setStatus] = useState("");
  const [limit, setLimit] = useState(100);

  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchOrders = async () => {
    setLoading(true);
    try {
      const res = await paymentAdmin.listOrders({
        username: username.trim() || undefined,
        status: status || undefined,
        limit,
      });
      if (res.code === 200 && res.data) {
        setOrders(res.data);
      } else {
        showMsg(res.message || "加载订单失败", "err");
      }
    } catch {
      showMsg("加载订单失败", "err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, []);

  const rowBusy = (outTradeNo: string, val: boolean) => {
    setBusyMap((prev) => ({ ...prev, [outTradeNo]: val }));
  };

  const doRefresh = async (outTradeNo: string) => {
    rowBusy(outTradeNo, true);
    try {
      const res = await paymentAdmin.refreshOrder(outTradeNo);
      if (res.code === 200) {
        showMsg("订单状态已刷新");
        fetchOrders();
      } else {
        showMsg(res.message || "刷新失败", "err");
      }
    } catch {
      showMsg("刷新失败", "err");
    } finally {
      rowBusy(outTradeNo, false);
    }
  };

  const doClose = async (outTradeNo: string) => {
    rowBusy(outTradeNo, true);
    try {
      const res = await paymentAdmin.closeOrder(outTradeNo);
      if (res.code === 200) {
        showMsg("订单已关闭");
        fetchOrders();
      } else {
        showMsg(res.message || "关闭失败", "err");
      }
    } catch {
      showMsg("关闭失败", "err");
    } finally {
      rowBusy(outTradeNo, false);
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
        支付订单管理
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
        查看订单状态，支持手动刷新和关闭 PAYING 订单
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr auto", gap: 10, marginBottom: 16 }}>
          <input
            placeholder="按用户名筛选"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            style={inputStyle}
          />
          <select value={status} onChange={(e) => setStatus(e.target.value)} style={inputStyle}>
            <option value="">全部状态</option>
            <option value="PAYING">PAYING</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="CLOSED">CLOSED</option>
            <option value="FAILED">FAILED</option>
          </select>
          <input
            type="number"
            min={1}
            max={200}
            value={limit}
            onChange={(e) => setLimit(Math.max(1, Math.min(200, Number(e.target.value || 100))))}
            style={inputStyle}
          />
          <button
            onClick={fetchOrders}
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
        ) : orders.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无订单</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={thStyle}>订单号</th>
                  <th style={thStyle}>用户</th>
                  <th style={thStyle}>商品</th>
                  <th style={thStyle}>金额</th>
                  <th style={thStyle}>状态</th>
                  <th style={thStyle}>创建时间</th>
                  <th style={thStyle}>操作</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((row) => {
                  const busy = !!busyMap[row.outTradeNo];
                  const canClose = row.status === "PAYING";
                  return (
                    <tr key={row.outTradeNo}>
                      <td style={tdStyle}>{row.outTradeNo}</td>
                      <td style={tdStyle}>{row.username || "-"}</td>
                      <td style={tdStyle}>{row.productCode || "-"}</td>
                      <td style={tdStyle}>{moneyFenToYuan(row.amountFen)} {row.currency || "CNY"}</td>
                      <td style={tdStyle}>
                        <span
                          style={{
                            padding: "2px 10px",
                            borderRadius: 980,
                            border: "1px solid rgba(255,255,255,0.12)",
                            color: statusColor(row.status),
                          }}
                        >
                          {row.status || "-"}
                        </span>
                      </td>
                      <td style={tdStyle}>{row.createdAt || "-"}</td>
                      <td style={tdStyle}>
                        <div style={{ display: "flex", gap: 8 }}>
                          <button
                            onClick={() => doRefresh(row.outTradeNo)}
                            disabled={busy}
                            className="cursor-pointer"
                            style={{
                              padding: "5px 12px",
                              backgroundColor: "var(--apple-surface-2)",
                              color: "rgba(255,255,255,0.9)",
                              borderRadius: 980,
                              border: "none",
                              fontSize: 12,
                              opacity: busy ? 0.6 : 1,
                            }}
                          >
                            刷新
                          </button>
                          <button
                            onClick={() => doClose(row.outTradeNo)}
                            disabled={busy || !canClose}
                            className="cursor-pointer"
                            style={{
                              padding: "5px 12px",
                              backgroundColor: "rgba(255,69,58,0.16)",
                              color: "#ff453a",
                              borderRadius: 980,
                              border: "1px solid rgba(255,69,58,0.25)",
                              fontSize: 12,
                              opacity: busy || !canClose ? 0.4 : 1,
                              cursor: busy || !canClose ? "not-allowed" : "pointer",
                            }}
                          >
                            关闭
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
