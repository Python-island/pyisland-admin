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
  const [freeDesc, setFreeDesc] = useState("");
  const [proDesc, setProDesc] = useState("");
  const [freeFeaturesText, setFreeFeaturesText] = useState("");
  const [proFeaturesText, setProFeaturesText] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const parseFeatures = (text: string): string[] =>
    text
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter((item) => !!item);

  const fetchPricing = async () => {
    setLoading(true);
    try {
      const res = await paymentAdmin.getConfig();
      if (res.code !== 200 || !res.data) {
        showMsg(res.message || "加载定价配置失败", "err");
        return;
      }
      setProMonthAmountFen(Math.max(1, Number(res.data.proMonthAmountFen) || 1500));
      setFreeDesc(String(res.data.freeDesc || ""));
      setProDesc(String(res.data.proDesc || ""));
      setFreeFeaturesText(Array.isArray(res.data.freeFeatures) ? res.data.freeFeatures.join("\n") : "");
      setProFeaturesText(Array.isArray(res.data.proFeatures) ? res.data.proFeatures.join("\n") : "");
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
    const normalizedFreeDesc = freeDesc.trim();
    const normalizedProDesc = proDesc.trim();
    const normalizedFreeFeatures = parseFeatures(freeFeaturesText);
    const normalizedProFeatures = parseFeatures(proFeaturesText);
    if (!normalizedFreeDesc || !normalizedProDesc) {
      showMsg("Free/Pro 权益简介不能为空", "err");
      return;
    }
    if (normalizedFreeFeatures.length === 0 || normalizedProFeatures.length === 0) {
      showMsg("Free/Pro 权益列表至少保留一项", "err");
      return;
    }

    setSaving(true);
    try {
      const res = await paymentAdmin.updateConfig({
        proMonthAmountFen: normalizedFen,
        freeDesc: normalizedFreeDesc,
        proDesc: normalizedProDesc,
        freeFeatures: normalizedFreeFeatures,
        proFeatures: normalizedProFeatures,
      });
      if (res.code === 200) {
        showMsg(res.message || "定价保存成功", "ok");
        setProMonthAmountFen(normalizedFen);
        setFreeDesc(normalizedFreeDesc);
        setProDesc(normalizedProDesc);
        setFreeFeaturesText(normalizedFreeFeatures.join("\n"));
        setProFeaturesText(normalizedProFeatures.join("\n"));
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
        管理 Pro 月付价格与 Free/Pro 权益介绍，支付下单与客户端展示会同步使用
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

            <div style={{ gridColumn: "span 2", height: 1, backgroundColor: "rgba(255,255,255,0.08)", margin: "6px 0" }} />

            <div style={{ color: "rgba(255,255,255,0.8)", fontSize: 15, fontWeight: 600, gridColumn: "span 2" }}>Free 权益</div>
            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>Free 简介</div>
              <textarea value={freeDesc} onChange={(e) => setFreeDesc(e.target.value)} rows={3} style={inputStyle} />
            </label>
            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>Free 权益列表（每行一条）</div>
              <textarea value={freeFeaturesText} onChange={(e) => setFreeFeaturesText(e.target.value)} rows={5} style={inputStyle} />
            </label>

            <div style={{ color: "rgba(255,255,255,0.8)", fontSize: 15, fontWeight: 600, gridColumn: "span 2" }}>Pro 权益</div>
            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>Pro 简介</div>
              <textarea value={proDesc} onChange={(e) => setProDesc(e.target.value)} rows={3} style={inputStyle} />
            </label>
            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>Pro 权益列表（每行一条）</div>
              <textarea value={proFeaturesText} onChange={(e) => setProFeaturesText(e.target.value)} rows={5} style={inputStyle} />
            </label>
          </div>
        )}

        <p style={{ marginTop: 16, color: "rgba(255,255,255,0.48)", fontSize: 12 }}>
          注意：本页面保存后仅更新当前运行中的服务实例；若需永久生效，请同步更新服务器 `.env` 配置并重启。
        </p>
      </div>
    </div>
  );
}
