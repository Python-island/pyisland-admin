import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { paymentAdmin, type PaymentConfigData, type PaymentConfigUpdatePayload } from "../api";

const cardStyle: React.CSSProperties = {
  backgroundColor: "var(--apple-surface-1)",
  borderRadius: 12,
  padding: 32,
};

const headingStyle: React.CSSProperties = {
  fontFamily: "var(--font-display)",
  fontSize: 21,
  fontWeight: 600,
  lineHeight: 1.19,
  letterSpacing: "0.231px",
  color: "#ffffff",
  marginBottom: 20,
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

type FormState = Omit<PaymentConfigUpdatePayload, "apiV3Key"> & { apiV3Key: string };

function emptyForm(): FormState {
  return {
    enabled: false,
    mchId: "",
    appId: "",
    apiV3Key: "",
    privateKeyPath: "",
    serialNo: "",
    notifyUrl: "",
    publicKeyId: "",
    publicKeyPath: "",
    platformCertPath: "",
    orderExpireMinutes: 15,
    queryPendingBatchSize: 100,
  };
}

export default function PaymentConfig() {
  const [form, setForm] = useState<FormState>(emptyForm());
  const [maskedApiV3, setMaskedApiV3] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchConfig = async () => {
    setLoading(true);
    try {
      const res = await paymentAdmin.getConfig();
      if (res.code !== 200 || !res.data) {
        showMsg(res.message || "加载支付配置失败", "err");
        return;
      }
      const data: PaymentConfigData = res.data;
      setForm({
        enabled: data.enabled,
        mchId: data.mchId || "",
        appId: data.appId || "",
        apiV3Key: "",
        privateKeyPath: data.privateKeyPath || "",
        serialNo: data.serialNo || "",
        notifyUrl: data.notifyUrl || "",
        publicKeyId: data.publicKeyId || "",
        publicKeyPath: data.publicKeyPath || "",
        platformCertPath: data.platformCertPath || "",
        orderExpireMinutes: data.orderExpireMinutes || 15,
        queryPendingBatchSize: data.queryPendingBatchSize || 100,
      });
      setMaskedApiV3(data.apiV3KeyMasked || "");
    } catch {
      showMsg("加载支付配置失败", "err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfig();
  }, []);

  const patchForm = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const save = async () => {
    if (!form.notifyUrl.trim()) {
      showMsg("回调地址不能为空", "err");
      return;
    }
    if (form.orderExpireMinutes < 5) {
      showMsg("订单过期分钟数不能小于 5", "err");
      return;
    }
    if (form.queryPendingBatchSize < 1) {
      showMsg("待查单批次不能小于 1", "err");
      return;
    }

    const payload: PaymentConfigUpdatePayload = {
      enabled: form.enabled,
      mchId: form.mchId.trim(),
      appId: form.appId.trim(),
      privateKeyPath: form.privateKeyPath.trim(),
      serialNo: form.serialNo.trim(),
      notifyUrl: form.notifyUrl.trim(),
      publicKeyId: form.publicKeyId.trim(),
      publicKeyPath: form.publicKeyPath.trim(),
      platformCertPath: form.platformCertPath.trim(),
      orderExpireMinutes: Number(form.orderExpireMinutes),
      queryPendingBatchSize: Number(form.queryPendingBatchSize),
      ...(form.apiV3Key.trim() ? { apiV3Key: form.apiV3Key.trim() } : {}),
    };

    setSaving(true);
    try {
      const res = await paymentAdmin.updateConfig(payload);
      if (res.code === 200) {
        showMsg(res.message || "保存成功", "ok");
        patchForm("apiV3Key", "");
        fetchConfig();
      } else {
        showMsg(res.message || "保存失败", "err");
      }
    } catch {
      showMsg("保存失败", "err");
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
        支付服务配置
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
        管理微信支付配置与运行开关
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        <div className="flex items-center justify-between" style={{ marginBottom: 20 }}>
          <h2 style={headingStyle}>微信支付（公钥模式）</h2>
          <div style={{ display: "flex", gap: 10 }}>
            <button
              onClick={fetchConfig}
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
              <div style={{ marginBottom: 6 }}>启用支付</div>
              <select
                value={form.enabled ? "true" : "false"}
                onChange={(e) => patchForm("enabled", e.target.value === "true")}
                style={inputStyle}
              >
                <option value="false">false</option>
                <option value="true">true</option>
              </select>
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>商户号 mchId</div>
              <input value={form.mchId} onChange={(e) => patchForm("mchId", e.target.value)} style={inputStyle} />
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>应用 AppID</div>
              <input value={form.appId} onChange={(e) => patchForm("appId", e.target.value)} style={inputStyle} />
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>商户证书序列号 serialNo</div>
              <input value={form.serialNo} onChange={(e) => patchForm("serialNo", e.target.value)} style={inputStyle} />
            </label>

            <label style={{ color: "#fff", fontSize: 13, gridColumn: "span 2" }}>
              <div style={{ marginBottom: 6 }}>回调地址 notifyUrl</div>
              <input value={form.notifyUrl} onChange={(e) => patchForm("notifyUrl", e.target.value)} style={inputStyle} />
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>微信支付公钥 ID</div>
              <input value={form.publicKeyId} onChange={(e) => patchForm("publicKeyId", e.target.value)} style={inputStyle} />
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>微信支付公钥路径</div>
              <input value={form.publicKeyPath} onChange={(e) => patchForm("publicKeyPath", e.target.value)} style={inputStyle} />
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>商户私钥路径</div>
              <input value={form.privateKeyPath} onChange={(e) => patchForm("privateKeyPath", e.target.value)} style={inputStyle} />
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>平台证书路径（可选）</div>
              <input value={form.platformCertPath} onChange={(e) => patchForm("platformCertPath", e.target.value)} style={inputStyle} />
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>APIv3 密钥（留空不变）</div>
              <input
                value={form.apiV3Key}
                onChange={(e) => patchForm("apiV3Key", e.target.value)}
                placeholder={maskedApiV3 ? `当前：${maskedApiV3}` : "未设置"}
                style={inputStyle}
              />
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>订单过期分钟数</div>
              <input
                type="number"
                min={5}
                value={form.orderExpireMinutes}
                onChange={(e) => patchForm("orderExpireMinutes", Number(e.target.value || 15))}
                style={inputStyle}
              />
            </label>

            <label style={{ color: "#fff", fontSize: 13 }}>
              <div style={{ marginBottom: 6 }}>待查单批次大小</div>
              <input
                type="number"
                min={1}
                value={form.queryPendingBatchSize}
                onChange={(e) => patchForm("queryPendingBatchSize", Number(e.target.value || 100))}
                style={inputStyle}
              />
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
