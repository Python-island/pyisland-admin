import { useMemo, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { paymentAdmin, type PaymentTestOrderData } from "../api";

type Channel = "WECHAT" | "ALIPAY";

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

function PaymentInterfaceTestPage({ channel }: { channel: Channel }) {
  const [amountYuan, setAmountYuan] = useState("0.01");
  const [subject, setSubject] = useState("eIsland 支付接口测试");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<PaymentTestOrderData | null>(null);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const title = channel === "WECHAT" ? "微信支付接口测试" : "支付宝支付接口测试";
  const subtitle = channel === "WECHAT"
    ? "创建微信 Native 测试订单并返回收款二维码"
    : "创建支付宝 PC 测试订单并返回收银台链接";

  const amountFen = useMemo(() => {
    const value = Number(amountYuan);
    if (!Number.isFinite(value)) {
      return NaN;
    }
    return Math.round(value * 100);
  }, [amountYuan]);

  const createOrder = async () => {
    if (!Number.isFinite(amountFen) || amountFen < 1) {
      setMsgType("err");
      setMsg("金额无效，请输入大于等于 0.01 元的数值");
      return;
    }
    if (amountFen > 1_000_000) {
      setMsgType("err");
      setMsg("金额过大，最高支持 10000.00 元");
      return;
    }
    setLoading(true);
    try {
      const res = await paymentAdmin.createTestOrder({
        channel,
        amountFen,
        subject: subject.trim() || undefined,
      });
      if (res.code === 200 && res.data) {
        setResult(res.data);
        setMsgType("ok");
        setMsg("测试订单创建成功");
      } else {
        setMsgType("err");
        setMsg(res.message || "创建测试订单失败");
      }
    } catch {
      setMsgType("err");
      setMsg("创建测试订单失败");
    } finally {
      setLoading(false);
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
        {title}
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
        {subtitle}
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={{ display: "grid", gridTemplateColumns: "minmax(420px, 1fr) minmax(420px, 1fr)", gap: 24 }}>
        <div style={cardStyle}>
          <div style={{ color: "#ffffff", fontSize: 20, fontWeight: 600, marginBottom: 16 }}>参数</div>

          <label style={{ color: "#fff", fontSize: 13, display: "block", marginBottom: 14 }}>
            <div style={{ marginBottom: 6 }}>收款金额（元）</div>
            <input
              type="number"
              min={0.01}
              step={0.01}
              value={amountYuan}
              onChange={(e) => setAmountYuan(e.target.value)}
              style={inputStyle}
            />
          </label>

          <label style={{ color: "#fff", fontSize: 13, display: "block", marginBottom: 18 }}>
            <div style={{ marginBottom: 6 }}>订单标题（可选）</div>
            <input
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              placeholder="eIsland 支付接口测试"
              style={inputStyle}
            />
          </label>

          <button
            onClick={createOrder}
            disabled={loading}
            className="cursor-pointer"
            style={{
              padding: "10px 28px",
              backgroundColor: "var(--apple-blue)",
              color: "#ffffff",
              borderRadius: 980,
              border: "none",
              fontSize: 15,
              fontWeight: 500,
              opacity: loading ? 0.6 : 1,
              cursor: loading ? "not-allowed" : "pointer",
            }}
          >
            {loading ? "创建中..." : `创建${channel === "WECHAT" ? "微信" : "支付宝"}测试单`}
          </button>
        </div>

        <div style={cardStyle}>
          <div style={{ color: "#ffffff", fontSize: 20, fontWeight: 600, marginBottom: 16 }}>结果</div>
          {!result ? (
            <div style={{ color: "rgba(255,255,255,0.40)", fontSize: 14 }}>
              创建测试订单后显示订单信息与支付地址
            </div>
          ) : (
            <div style={{ display: "grid", gap: 10, color: "rgba(255,255,255,0.84)", fontSize: 13 }}>
              <div><strong>订单号：</strong>{result.outTradeNo}</div>
              <div><strong>通道：</strong>{result.channel}</div>
              <div><strong>金额：</strong>{(result.amountFen / 100).toFixed(2)} 元</div>
              <div><strong>状态：</strong>{result.status}</div>
              <div><strong>过期时间：</strong>{result.expireAt || "-"}</div>
              {channel === "WECHAT" ? (
                <>
                  <div><strong>二维码：</strong></div>
                  {result.qrCodeUrl ? (
                    <div
                      style={{
                        marginTop: 4,
                        width: 224,
                        backgroundColor: "#ffffff",
                        borderRadius: 12,
                        padding: 12,
                      }}
                    >
                      <img
                        src={`https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=${encodeURIComponent(result.qrCodeUrl)}`}
                        alt="支付二维码"
                        style={{ display: "block", width: "100%", height: "auto", borderRadius: 6 }}
                        loading="lazy"
                        referrerPolicy="no-referrer"
                      />
                    </div>
                  ) : (
                    <div style={{ color: "rgba(255,255,255,0.64)" }}>-</div>
                  )}
                  <div>
                    <strong>二维码地址：</strong>
                    {result.qrCodeUrl ? (
                      <a
                        href={result.qrCodeUrl}
                        target="_blank"
                        rel="noreferrer"
                        style={{ color: "var(--apple-link-dark)", wordBreak: "break-all", marginLeft: 6 }}
                      >
                        查看原始地址
                      </a>
                    ) : (
                      <span style={{ marginLeft: 6 }}>-</span>
                    )}
                  </div>
                </>
              ) : (
                <>
                  <div>
                    <strong>收银台地址：</strong>
                    {result.payUrl ? (
                      <a
                        href={result.payUrl}
                        target="_blank"
                        rel="noreferrer"
                        style={{ color: "var(--apple-link-dark)", wordBreak: "break-all", marginLeft: 6 }}
                      >
                        打开支付页
                      </a>
                    ) : (
                      <span style={{ marginLeft: 6 }}>-</span>
                    )}
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export function WechatPaymentInterfaceTest() {
  return <PaymentInterfaceTestPage channel="WECHAT" />;
}

export function AlipayPaymentInterfaceTest() {
  return <PaymentInterfaceTestPage channel="ALIPAY" />;
}
