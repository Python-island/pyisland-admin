import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { agentAdmin, type AgentModelPricingItem } from "../api";

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

const thStyle: React.CSSProperties = {
  textAlign: "left",
  padding: "10px 12px",
  fontSize: 12,
  fontWeight: 600,
  color: "rgba(255,255,255,0.56)",
  borderBottom: "1px solid rgba(255,255,255,0.08)",
};

const tdStyle: React.CSSProperties = {
  padding: "10px 12px",
  fontSize: 14,
  color: "#ffffff",
  borderBottom: "1px solid rgba(255,255,255,0.06)",
};

export default function AgentModelPricing() {
  const [list, setList] = useState<AgentModelPricingItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  // Agent 服务开关
  const [serviceEnabled, setServiceEnabled] = useState(true);
  const [serviceMsg, setServiceMsg] = useState("");
  const [serviceToggling, setServiceToggling] = useState(false);
  const [serviceLoading, setServiceLoading] = useState(true);

  // 全量赠送余额
  const [giftBalanceYuan, setGiftBalanceYuan] = useState("1.00");
  const [giftingBalance, setGiftingBalance] = useState(false);

  // form
  const [modelName, setModelName] = useState("");
  const [inputPrice, setInputPrice] = useState(0);
  const [outputPrice, setOutputPrice] = useState(0);
  const [enabled, setEnabled] = useState(true);
  const [editingModel, setEditingModel] = useState<string | null>(null);

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await agentAdmin.listModelPricing();
      if (res.code === 200 && Array.isArray(res.data)) {
        setList(res.data);
      } else {
        showMsg(res.message || "加载失败", "err");
      }
    } catch {
      showMsg("加载模型定价失败", "err");
    } finally {
      setLoading(false);
    }
  };

  const fetchServiceEnabled = async () => {
    setServiceLoading(true);
    try {
      const res = await agentAdmin.getServiceEnabled();
      if (res.code === 200 && res.data) {
        setServiceEnabled(res.data.enabled);
        setServiceMsg(res.data.statusMessage || "");
      }
    } catch { /* ignore */ }
    finally { setServiceLoading(false); }
  };

  useEffect(() => {
    fetchList();
    fetchServiceEnabled();
  }, []);

  const handleToggleService = async () => {
    setServiceToggling(true);
    try {
      const next = !serviceEnabled;
      const res = await agentAdmin.setServiceEnabled(next, serviceMsg);
      if (res.code === 200) {
        setServiceEnabled(next);
        showMsg(res.message || (next ? "Agent 服务已开启" : "Agent 服务已关闭"));
      } else {
        showMsg(res.message || "操作失败", "err");
      }
    } catch {
      showMsg("操作失败", "err");
    } finally {
      setServiceToggling(false);
    }
  };

  const handleGiftBalanceAll = async () => {
    const yuan = parseFloat(giftBalanceYuan);
    if (Number.isNaN(yuan) || yuan <= 0) {
      showMsg("赠送金额必须大于 0", "err");
      return;
    }
    const amountFen = Math.round(yuan * 100);
    if (amountFen <= 0) {
      showMsg("赠送金额必须大于 0", "err");
      return;
    }
    if (!confirm(`确认给所有普通用户与 Pro 用户赠送 ¥${(amountFen / 100).toFixed(2)} 余额？`)) {
      return;
    }
    setGiftingBalance(true);
    try {
      const res = await agentAdmin.giftBalanceAll(amountFen);
      if (res.code === 200) {
        const affected = typeof res.data?.affected === "number" ? res.data.affected : 0;
        showMsg(`赠送成功，已处理 ${affected} 位用户`, "ok");
      } else {
        showMsg(res.message || "赠送失败", "err");
      }
    } catch {
      showMsg("赠送失败", "err");
    } finally {
      setGiftingBalance(false);
    }
  };

  const handleSaveServiceMsg = async () => {
    setServiceToggling(true);
    try {
      const res = await agentAdmin.setServiceEnabled(serviceEnabled, serviceMsg);
      if (res.code === 200) {
        showMsg("状态说明已保存");
      } else {
        showMsg(res.message || "保存失败", "err");
      }
    } catch {
      showMsg("保存失败", "err");
    } finally {
      setServiceToggling(false);
    }
  };

  const resetForm = () => {
    setModelName("");
    setInputPrice(0);
    setOutputPrice(0);
    setEnabled(true);
    setEditingModel(null);
  };

  const startEdit = (item: AgentModelPricingItem) => {
    setModelName(item.modelName);
    setInputPrice(item.inputPriceFenPerMillion);
    setOutputPrice(item.outputPriceFenPerMillion);
    setEnabled(item.enabled);
    setEditingModel(item.modelName);
  };

  const save = async () => {
    const trimmedName = modelName.trim();
    if (!trimmedName) {
      showMsg("模型名不能为空", "err");
      return;
    }
    if (inputPrice < 0 || outputPrice < 0) {
      showMsg("价格不能为负数", "err");
      return;
    }
    setSaving(true);
    try {
      const res = await agentAdmin.upsertModelPricing({
        modelName: trimmedName,
        inputPriceFenPerMillion: Math.max(0, Math.round(inputPrice)),
        outputPriceFenPerMillion: Math.max(0, Math.round(outputPrice)),
        enabled,
      });
      if (res.code === 200) {
        showMsg(res.message || "保存成功", "ok");
        resetForm();
        await fetchList();
      } else {
        showMsg(res.message || "保存失败", "err");
      }
    } catch {
      showMsg("保存失败", "err");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (name: string) => {
    if (!confirm(`确认删除模型「${name}」的定价配置？`)) return;
    try {
      const res = await agentAdmin.deleteModelPricing(name);
      if (res.code === 200) {
        showMsg("删除成功", "ok");
        if (editingModel === name) resetForm();
        await fetchList();
      } else {
        showMsg(res.message || "删除失败", "err");
      }
    } catch {
      showMsg("删除失败", "err");
    }
  };

  const fenToYuan = (fen: number) => (fen / 100).toFixed(2);

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
        Agent 管理
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
        配置各模型每百万 token 的定价（分），用于按量扣费
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      {/* Agent 服务开关 */}
      <div style={{ ...cardStyle, marginBottom: 24 }}>
        <div className="flex items-center justify-between" style={{ marginBottom: 16 }}>
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
            Agent 服务开关
          </h2>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <span
              style={{
                display: "inline-block",
                padding: "2px 12px",
                borderRadius: 980,
                fontSize: 13,
                fontWeight: 500,
                backgroundColor: serviceEnabled ? "rgba(48,209,88,0.16)" : "rgba(255,69,58,0.16)",
                color: serviceEnabled ? "#30d158" : "#ff453a",
              }}
            >
              {serviceLoading ? "加载中..." : serviceEnabled ? "已开启" : "已关闭"}
            </span>
            <button
              onClick={handleToggleService}
              disabled={serviceToggling || serviceLoading}
              className="cursor-pointer"
              style={{
                padding: "8px 16px",
                backgroundColor: serviceEnabled ? "#ff453a" : "#30d158",
                color: "#fff",
                borderRadius: 980,
                border: "none",
                fontSize: 14,
                fontWeight: 500,
                opacity: serviceToggling || serviceLoading ? 0.6 : 1,
                cursor: serviceToggling || serviceLoading ? "not-allowed" : "pointer",
                transition: "background-color 0.15s",
              }}
            >
              {serviceToggling ? "操作中..." : serviceEnabled ? "关闭服务" : "开启服务"}
            </button>
          </div>
        </div>
        <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
          <input
            type="text"
            value={serviceMsg}
            onChange={(e) => setServiceMsg(e.target.value)}
            placeholder="状态说明（可选，关闭时将展示给用户）"
            style={{ ...inputStyle, flex: 1 }}
          />
          <button
            onClick={handleSaveServiceMsg}
            disabled={serviceToggling}
            className="cursor-pointer"
            style={{
              padding: "8px 16px",
              backgroundColor: "var(--apple-surface-2)",
              color: "rgba(255,255,255,0.8)",
              borderRadius: 980,
              border: "none",
              fontSize: 14,
              whiteSpace: "nowrap",
              opacity: serviceToggling ? 0.6 : 1,
              cursor: serviceToggling ? "not-allowed" : "pointer",
            }}
          >
            保存说明
          </button>
        </div>
        <div style={{ fontSize: 12, color: "rgba(255,255,255,0.4)", marginTop: 8 }}>
          关闭后所有用户的 Agent 对话请求将被拒绝，状态说明内容会展示给用户。
        </div>
      </div>

      {/* 全量赠送余额 */}
      <div style={{ ...cardStyle, marginBottom: 24 }}>
        <div className="flex items-center justify-between" style={{ marginBottom: 16 }}>
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
            全员赠送余额
          </h2>
        </div>
        <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
          <input
            type="number"
            min="0.01"
            step="0.01"
            value={giftBalanceYuan}
            onChange={(e) => setGiftBalanceYuan(e.target.value)}
            placeholder="赠送金额（元）"
            style={{ ...inputStyle, flex: 1 }}
          />
          <button
            onClick={handleGiftBalanceAll}
            disabled={giftingBalance}
            className="cursor-pointer"
            style={{
              padding: "8px 16px",
              backgroundColor: "#30d158",
              color: "#fff",
              borderRadius: 980,
              border: "none",
              fontSize: 14,
              fontWeight: 500,
              whiteSpace: "nowrap",
              opacity: giftingBalance ? 0.6 : 1,
              cursor: giftingBalance ? "not-allowed" : "pointer",
            }}
          >
            {giftingBalance ? "赠送中..." : "确认赠送"}
          </button>
        </div>
        <div style={{ fontSize: 12, color: "rgba(255,255,255,0.4)", marginTop: 8 }}>
          将给所有普通用户与 Pro 用户统一增加指定余额（管理员账号不受影响）。
        </div>
      </div>

      {/* 表单 */}
      <div style={{ ...cardStyle, marginBottom: 24 }}>
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
            {editingModel ? `编辑：${editingModel}` : "新增模型定价"}
          </h2>
          <div style={{ display: "flex", gap: 10 }}>
            {editingModel && (
              <button
                onClick={resetForm}
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
                取消
              </button>
            )}
            <button
              onClick={save}
              disabled={saving}
              className="cursor-pointer"
              style={{
                padding: "8px 16px",
                backgroundColor: "var(--apple-blue)",
                color: "#fff",
                borderRadius: 980,
                border: "none",
                fontSize: 14,
                opacity: saving ? 0.6 : 1,
                cursor: saving ? "not-allowed" : "pointer",
              }}
            >
              {saving ? "保存中..." : "保存"}
            </button>
          </div>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 16 }}>
          <label style={{ color: "#fff", fontSize: 13 }}>
            <div style={{ marginBottom: 6 }}>模型名称</div>
            <input
              type="text"
              value={modelName}
              onChange={(e) => setModelName(e.target.value)}
              disabled={!!editingModel}
              placeholder="例如 deepseek-v4-flash、mimo-v2.5"
              style={{ ...inputStyle, opacity: editingModel ? 0.6 : 1 }}
            />
          </label>

          <label style={{ color: "#fff", fontSize: 13 }}>
            <div style={{ marginBottom: 6 }}>
              启用计费
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 10, height: 40 }}>
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
                style={{ width: 18, height: 18, accentColor: "var(--apple-blue)" }}
              />
              <span style={{ fontSize: 14, color: enabled ? "var(--apple-link-dark)" : "rgba(255,255,255,0.48)" }}>
                {enabled ? "已启用" : "已禁用"}
              </span>
            </div>
          </label>

          <label style={{ color: "#fff", fontSize: 13 }}>
            <div style={{ marginBottom: 6 }}>输入价格（分 / 百万 token）</div>
            <input
              type="number"
              min={0}
              value={inputPrice}
              onChange={(e) => setInputPrice(Math.max(0, Number(e.target.value) || 0))}
              style={inputStyle}
            />
            <div style={{ marginTop: 4, fontSize: 12, color: "rgba(255,255,255,0.4)" }}>
              ≈ ¥{fenToYuan(inputPrice)} / 百万 token
            </div>
          </label>

          <label style={{ color: "#fff", fontSize: 13 }}>
            <div style={{ marginBottom: 6 }}>输出价格（分 / 百万 token）</div>
            <input
              type="number"
              min={0}
              value={outputPrice}
              onChange={(e) => setOutputPrice(Math.max(0, Number(e.target.value) || 0))}
              style={inputStyle}
            />
            <div style={{ marginTop: 4, fontSize: 12, color: "rgba(255,255,255,0.4)" }}>
              ≈ ¥{fenToYuan(outputPrice)} / 百万 token
            </div>
          </label>
        </div>
      </div>

      {/* 列表 */}
      <div style={cardStyle}>
        <div className="flex items-center justify-between" style={{ marginBottom: 16 }}>
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
            已配置模型
          </h2>
          <button
            onClick={fetchList}
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
        </div>

        {loading ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>加载中...</p>
        ) : list.length === 0 ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无模型定价配置，请在上方新增。</p>
        ) : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr>
                <th style={thStyle}>模型名称</th>
                <th style={thStyle}>输入价（分/百万token）</th>
                <th style={thStyle}>输出价（分/百万token）</th>
                <th style={thStyle}>状态</th>
                <th style={thStyle}>更新时间</th>
                <th style={{ ...thStyle, textAlign: "right" }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {list.map((item) => (
                <tr key={item.id}>
                  <td style={tdStyle}>
                    <span style={{ fontWeight: 500 }}>{item.modelName}</span>
                  </td>
                  <td style={tdStyle}>
                    {item.inputPriceFenPerMillion}
                    <span style={{ marginLeft: 6, fontSize: 12, color: "rgba(255,255,255,0.4)" }}>
                      (¥{fenToYuan(item.inputPriceFenPerMillion)})
                    </span>
                  </td>
                  <td style={tdStyle}>
                    {item.outputPriceFenPerMillion}
                    <span style={{ marginLeft: 6, fontSize: 12, color: "rgba(255,255,255,0.4)" }}>
                      (¥{fenToYuan(item.outputPriceFenPerMillion)})
                    </span>
                  </td>
                  <td style={tdStyle}>
                    <span
                      style={{
                        display: "inline-block",
                        padding: "2px 10px",
                        borderRadius: 980,
                        fontSize: 12,
                        fontWeight: 500,
                        backgroundColor: item.enabled ? "rgba(48,209,88,0.16)" : "rgba(255,69,58,0.16)",
                        color: item.enabled ? "#30d158" : "#ff453a",
                      }}
                    >
                      {item.enabled ? "启用" : "禁用"}
                    </span>
                  </td>
                  <td style={{ ...tdStyle, fontSize: 12, color: "rgba(255,255,255,0.48)" }}>
                    {item.updatedAt || "-"}
                  </td>
                  <td style={{ ...tdStyle, textAlign: "right" }}>
                    <button
                      onClick={() => startEdit(item)}
                      className="cursor-pointer"
                      style={{
                        padding: "4px 12px",
                        backgroundColor: "transparent",
                        color: "var(--apple-link-dark)",
                        border: "1px solid var(--apple-link-dark)",
                        borderRadius: 6,
                        fontSize: 12,
                        marginRight: 6,
                      }}
                    >
                      编辑
                    </button>
                    <button
                      onClick={() => handleDelete(item.modelName)}
                      className="cursor-pointer"
                      style={{
                        padding: "4px 12px",
                        backgroundColor: "transparent",
                        color: "#ff453a",
                        border: "1px solid #ff453a",
                        borderRadius: 6,
                        fontSize: 12,
                      }}
                    >
                      删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
