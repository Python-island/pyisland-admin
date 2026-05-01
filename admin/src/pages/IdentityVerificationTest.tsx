import { useState } from "react";
import MessageDialog from "../components/MessageDialog";
import {
  identityAdmin,
  type IdentityTestStartData,
  type IdentityTestQueryData,
  type IdentityTestRecordItem,
} from "../api";

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

const btnStyle: React.CSSProperties = {
  padding: "10px 28px",
  backgroundColor: "var(--apple-blue)",
  color: "#ffffff",
  borderRadius: 980,
  border: "none",
  fontSize: 15,
  fontWeight: 500,
  cursor: "pointer",
};

const statusColors: Record<string, string> = {
  CERTIFYING: "#f5a623",
  PASSED: "#34c759",
  FAILED: "#ff3b30",
};

export default function IdentityVerificationTest() {
  const [username, setUsername] = useState("");
  const [certName, setCertName] = useState("");
  const [certNo, setCertNo] = useState("");
  const [startLoading, setStartLoading] = useState(false);
  const [startResult, setStartResult] = useState<IdentityTestStartData | null>(null);

  const [queryUsername, setQueryUsername] = useState("");
  const [queryCertifyId, setQueryCertifyId] = useState("");
  const [queryLoading, setQueryLoading] = useState(false);
  const [queryResult, setQueryResult] = useState<IdentityTestQueryData | null>(null);

  const [statusUsername, setStatusUsername] = useState("");
  const [statusLoading, setStatusLoading] = useState(false);
  const [statusResult, setStatusResult] = useState<{ verified: boolean } | null>(null);

  const [recordsUsername, setRecordsUsername] = useState("");
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [records, setRecords] = useState<IdentityTestRecordItem[]>([]);

  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const handleStart = async () => {
    if (!username.trim() || !certName.trim() || !certNo.trim()) {
      setMsgType("err");
      setMsg("用户名、姓名和身份证号不能为空");
      return;
    }
    setStartLoading(true);
    try {
      const res = await identityAdmin.testStart({
        username: username.trim(),
        certName: certName.trim(),
        certNo: certNo.trim(),
      });
      if (res.code === 200 && res.data) {
        setStartResult(res.data);
        setQueryUsername(username.trim());
        setQueryCertifyId(res.data.certifyId);
        setMsgType("ok");
        setMsg("认证发起成功");
      } else {
        setMsgType("err");
        setMsg(res.message || "发起认证失败");
      }
    } catch {
      setMsgType("err");
      setMsg("发起认证请求失败");
    } finally {
      setStartLoading(false);
    }
  };

  const handleQuery = async () => {
    if (!queryUsername.trim() || !queryCertifyId.trim()) {
      setMsgType("err");
      setMsg("用户名和 certifyId 不能为空");
      return;
    }
    setQueryLoading(true);
    try {
      const res = await identityAdmin.testQuery(queryUsername.trim(), queryCertifyId.trim());
      if (res.code === 200 && res.data) {
        setQueryResult(res.data);
        setMsgType("ok");
        setMsg(res.data.passed ? "认证已通过" : "认证未通过");
      } else {
        setMsgType("err");
        setMsg(res.message || "查询失败");
      }
    } catch {
      setMsgType("err");
      setMsg("查询认证结果失败");
    } finally {
      setQueryLoading(false);
    }
  };

  const handleStatus = async () => {
    if (!statusUsername.trim()) {
      setMsgType("err");
      setMsg("请输入用户名");
      return;
    }
    setStatusLoading(true);
    try {
      const res = await identityAdmin.testStatus(statusUsername.trim());
      if (res.code === 200 && res.data) {
        setStatusResult(res.data);
      } else {
        setMsgType("err");
        setMsg(res.message || "查询状态失败");
      }
    } catch {
      setMsgType("err");
      setMsg("查询状态失败");
    } finally {
      setStatusLoading(false);
    }
  };

  const handleRecords = async () => {
    if (!recordsUsername.trim()) {
      setMsgType("err");
      setMsg("请输入用户名");
      return;
    }
    setRecordsLoading(true);
    try {
      const res = await identityAdmin.testRecords(recordsUsername.trim());
      if (res.code === 200 && res.data) {
        setRecords(res.data);
      } else {
        setMsgType("err");
        setMsg(res.message || "查询记录失败");
      }
    } catch {
      setMsgType("err");
      setMsg("查询记录失败");
    } finally {
      setRecordsLoading(false);
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
        身份认证接口测试
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
        以指定用户身份调试支付宝实名认证全流程
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      {/* 发起认证 + 结果 */}
      <div style={{ display: "grid", gridTemplateColumns: "minmax(420px, 1fr) minmax(420px, 1fr)", gap: 24, marginBottom: 24 }}>
        <div style={cardStyle}>
          <div style={{ color: "#ffffff", fontSize: 20, fontWeight: 600, marginBottom: 16 }}>发起认证</div>
          <label style={{ color: "#fff", fontSize: 13, display: "block", marginBottom: 14 }}>
            <div style={{ marginBottom: 6 }}>用户名</div>
            <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="输入目标用户名" style={inputStyle} />
          </label>
          <label style={{ color: "#fff", fontSize: 13, display: "block", marginBottom: 14 }}>
            <div style={{ marginBottom: 6 }}>真实姓名</div>
            <input value={certName} onChange={(e) => setCertName(e.target.value)} placeholder="身份证上的姓名" style={inputStyle} />
          </label>
          <label style={{ color: "#fff", fontSize: 13, display: "block", marginBottom: 18 }}>
            <div style={{ marginBottom: 6 }}>身份证号</div>
            <input value={certNo} onChange={(e) => setCertNo(e.target.value)} placeholder="18 位身份证号" style={inputStyle} />
          </label>
          <button onClick={handleStart} disabled={startLoading} className="cursor-pointer" style={{ ...btnStyle, opacity: startLoading ? 0.6 : 1 }}>
            {startLoading ? "发起中..." : "发起认证"}
          </button>
        </div>

        <div style={cardStyle}>
          <div style={{ color: "#ffffff", fontSize: 20, fontWeight: 600, marginBottom: 16 }}>发起结果</div>
          {!startResult ? (
            <div style={{ color: "rgba(255,255,255,0.40)", fontSize: 14 }}>发起认证后显示 certifyId 与认证页面链接</div>
          ) : (
            <div style={{ display: "grid", gap: 10, color: "rgba(255,255,255,0.84)", fontSize: 13 }}>
              <div><strong>订单号：</strong>{startResult.outerOrderNo}</div>
              <div><strong>certifyId：</strong>{startResult.certifyId}</div>
              <div>
                <strong>认证页面：</strong>
                <a href={startResult.certifyUrl} target="_blank" rel="noreferrer" style={{ color: "var(--apple-link-dark)", wordBreak: "break-all", marginLeft: 6 }}>
                  打开认证页
                </a>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 查询认证结果 */}
      <div style={{ display: "grid", gridTemplateColumns: "minmax(420px, 1fr) minmax(420px, 1fr)", gap: 24, marginBottom: 24 }}>
        <div style={cardStyle}>
          <div style={{ color: "#ffffff", fontSize: 20, fontWeight: 600, marginBottom: 16 }}>查询认证结果</div>
          <label style={{ color: "#fff", fontSize: 13, display: "block", marginBottom: 14 }}>
            <div style={{ marginBottom: 6 }}>用户名</div>
            <input value={queryUsername} onChange={(e) => setQueryUsername(e.target.value)} placeholder="用户名" style={inputStyle} />
          </label>
          <label style={{ color: "#fff", fontSize: 13, display: "block", marginBottom: 18 }}>
            <div style={{ marginBottom: 6 }}>certifyId</div>
            <input value={queryCertifyId} onChange={(e) => setQueryCertifyId(e.target.value)} placeholder="支付宝返回的 certifyId" style={inputStyle} />
          </label>
          <button onClick={handleQuery} disabled={queryLoading} className="cursor-pointer" style={{ ...btnStyle, opacity: queryLoading ? 0.6 : 1 }}>
            {queryLoading ? "查询中..." : "查询结果"}
          </button>
        </div>

        <div style={cardStyle}>
          <div style={{ color: "#ffffff", fontSize: 20, fontWeight: 600, marginBottom: 16 }}>查询结果</div>
          {!queryResult ? (
            <div style={{ color: "rgba(255,255,255,0.40)", fontSize: 14 }}>查询后显示认证结果</div>
          ) : (
            <div style={{ display: "grid", gap: 10, color: "rgba(255,255,255,0.84)", fontSize: 13 }}>
              <div>
                <strong>通过状态：</strong>
                <span style={{ color: queryResult.passed ? "#34c759" : "#ff3b30", fontWeight: 600 }}>
                  {queryResult.passed ? "已通过" : "未通过"}
                </span>
              </div>
              <div><strong>消息：</strong>{queryResult.message}</div>
            </div>
          )}
        </div>
      </div>

      {/* 认证状态查询 */}
      <div style={{ display: "grid", gridTemplateColumns: "minmax(420px, 1fr) minmax(420px, 1fr)", gap: 24, marginBottom: 24 }}>
        <div style={cardStyle}>
          <div style={{ color: "#ffffff", fontSize: 20, fontWeight: 600, marginBottom: 16 }}>用户认证状态</div>
          <label style={{ color: "#fff", fontSize: 13, display: "block", marginBottom: 18 }}>
            <div style={{ marginBottom: 6 }}>用户名</div>
            <input value={statusUsername} onChange={(e) => setStatusUsername(e.target.value)} placeholder="输入用户名查询认证状态" style={inputStyle} />
          </label>
          <button onClick={handleStatus} disabled={statusLoading} className="cursor-pointer" style={{ ...btnStyle, opacity: statusLoading ? 0.6 : 1 }}>
            {statusLoading ? "查询中..." : "查询状态"}
          </button>
          {statusResult !== null && (
            <div style={{ marginTop: 16, color: "rgba(255,255,255,0.84)", fontSize: 14 }}>
              <strong>实名状态：</strong>
              <span style={{ color: statusResult.verified ? "#34c759" : "#ff3b30", fontWeight: 600 }}>
                {statusResult.verified ? "已认证" : "未认证"}
              </span>
            </div>
          )}
        </div>

        {/* 认证记录查询 */}
        <div style={cardStyle}>
          <div style={{ color: "#ffffff", fontSize: 20, fontWeight: 600, marginBottom: 16 }}>认证记录</div>
          <label style={{ color: "#fff", fontSize: 13, display: "block", marginBottom: 18 }}>
            <div style={{ marginBottom: 6 }}>用户名</div>
            <input value={recordsUsername} onChange={(e) => setRecordsUsername(e.target.value)} placeholder="输入用户名查询认证记录" style={inputStyle} />
          </label>
          <button onClick={handleRecords} disabled={recordsLoading} className="cursor-pointer" style={{ ...btnStyle, opacity: recordsLoading ? 0.6 : 1 }}>
            {recordsLoading ? "查询中..." : "查询记录"}
          </button>
        </div>
      </div>

      {/* 认证记录列表 */}
      {records.length > 0 && (
        <div style={cardStyle}>
          <div style={{ color: "#ffffff", fontSize: 20, fontWeight: 600, marginBottom: 16 }}>
            认证记录列表（{records.length} 条）
          </div>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13, color: "rgba(255,255,255,0.84)" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid rgba(255,255,255,0.12)" }}>
                  {["ID", "用户名", "订单号", "certifyId", "状态", "素材URL", "创建时间", "更新时间"].map((h) => (
                    <th key={h} style={{ textAlign: "left", padding: "8px 10px", fontWeight: 600, whiteSpace: "nowrap" }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {records.map((r) => (
                  <tr key={r.id} style={{ borderBottom: "1px solid rgba(255,255,255,0.06)" }}>
                    <td style={{ padding: "8px 10px" }}>{r.id}</td>
                    <td style={{ padding: "8px 10px" }}>{r.username}</td>
                    <td style={{ padding: "8px 10px", fontFamily: "monospace" }}>{r.outerOrderNo}</td>
                    <td style={{ padding: "8px 10px", fontFamily: "monospace", maxWidth: 180, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{r.certifyId}</td>
                    <td style={{ padding: "8px 10px" }}>
                      <span style={{ color: statusColors[r.status] || "#fff", fontWeight: 600 }}>{r.status}</span>
                    </td>
                    <td style={{ padding: "8px 10px", maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {r.materialInfoUrl ? (
                        <a href={r.materialInfoUrl} target="_blank" rel="noreferrer" style={{ color: "var(--apple-link-dark)" }}>查看</a>
                      ) : "-"}
                    </td>
                    <td style={{ padding: "8px 10px", whiteSpace: "nowrap" }}>{r.createdAt}</td>
                    <td style={{ padding: "8px 10px", whiteSpace: "nowrap" }}>{r.updatedAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
