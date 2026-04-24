import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { paymentAdmin } from "../api";

const cardStyle: React.CSSProperties = {
  backgroundColor: "var(--apple-surface-1)",
  borderRadius: 12,
  padding: 32,
};

const inputStyle: React.CSSProperties = {
  padding: "10px 12px",
  backgroundColor: "var(--apple-surface-2)",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 14,
  lineHeight: 1.43,
  letterSpacing: "-0.224px",
  width: "100%",
  outline: "none",
};

export default function PaymentPricing() {
  const [proMonthAmountFen, setProMonthAmountFen] = useState(1500);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchPricing = async () => {
    setLoading(true);
    try {
      const res = await paymentAdmin.getConfig();
      if (res.code !== 200 || !res.data) {
        showMsg(res.message || "加载定价配置失败", "err");
        return;
      }
      setProMonthAmountFen(Math.max(1, Number(res.data.proMonthAmountFen) || 1500));
    } catch {
      showMsg("加载定价配置失败", "err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPricing();
  }, []);

  const save = async () => {
    const normalizedFen = Math.max(1, Number(proMonthAmountFen) || 0);
    if (normalizedFen < 1) {
      showMsg("Pro 月付价格（分）不能小于 1", "err");
      return;
    }

    setSaving(true);
    try {
      const res = await paymentAdmin.updateConfig({
        proMonthAmountFen: normalizedFen,
      });
      if (res.code === 200) {
        showMsg(res.message || "定价保存成功", "ok");
        setProMonthAmountFen(normalizedFen);
      } else {
        showMsg(res.message || "定价保存失败", "err");
      }
    } catch {
      showMsg("定价保存失败", "err");
    } finally {
      setSaving(false);
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
        定价服务
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
        管理 Pro 月付价格，支付下单与客户端展示会同步使用
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        <div className="flex items-center justify-between" style={{ marginBottom: 20 }}>
          <h2
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 21,
              fontWeight: 600,
              lineHeight: 1.19,
              letterSpacing: "0.231px",
              color: "#ffffff",
            }}
          >
            Pro 套餐定价
          </h2>
          <div style={{ display: "flex", gap: 10 }}>
            <button
              onClick={fetchPricing}
              className="cursor-pointer"
              style={{
                padding: "8px 16px",
                backgroundColor: "var(--apple-surface-2)",
                color: "rgba(255,255,255,0.8)",
                borderRadius: 980,
                border: "none",
                fontSize: 14,
              }}
            >
              刷新
            </button>
            <button
              onClick={save}
              disabled={loading || saving}
              className="cursor-pointer"
              style={{
                padding: "8px 16px",
                backgroundColor: "var(--apple-blue)",
                color: "#fff",
                borderRadius: 980,
                border: "none",
                fontSize: 14,
                opacity: loading || saving ? 0.6 : 1,
                cursor: loading || saving ? "not-allowed" : "pointer",
              }}
            >
              {saving ? "保存中..." : "保存"}
            </button>
          </div>
        </div>

        {loading ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>加载中...</p>
        ) : (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 16 }}>
            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>Pro 月付价格（分）</div>
              <input
                type="number"
                min={1}
                value={proMonthAmountFen}
                onChange={(e) => setProMonthAmountFen(Math.max(1, Number(e.target.value || 1500)))}
                style={inputStyle}
              />
            </label>

            <div style={{ color: "#fff", fontSize: 13, display: "flex", alignItems: "end" }}>
              当前价格：
              <span style={{ marginLeft: 8, color: "var(--apple-link-dark)" }}>
                ¥{(Math.max(1, Number(proMonthAmountFen) || 0) / 100).toFixed(2)} / 月
              </span>
            </div>
          </div>
        )}

        <p style={{ marginTop: 16, color: "rgba(255,255,255,0.48)", fontSize: 12 }}>
          注意：本页面保存后仅更新当前运行中的服务实例；若需永久生效，请同步更新服务器 `.env` 配置并重启。
        </p>
      </div>
    </div>
  );
}
