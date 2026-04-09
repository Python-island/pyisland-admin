import { useEffect, useState } from "react";
import MessageDialog from "../components/MessageDialog";
import { apiStatus, type ApiStatus } from "../api";

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
  marginBottom: 24,
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
  fontSize: 14,
  lineHeight: 1.43,
  letterSpacing: "-0.224px",
  color: "#ffffff",
  borderBottom: "1px solid rgba(255,255,255,0.04)",
  verticalAlign: "middle",
};

const inputStyle: React.CSSProperties = {
  padding: "7px 12px",
  backgroundColor: "var(--apple-surface-2)",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 14,
  lineHeight: 1.43,
  letterSpacing: "-0.224px",
  width: 190,
  outline: "none",
};

interface EditBuffer {
  message: string;
  remark: string;
}

export default function ApiStatusManage() {
  const [list, setList] = useState<ApiStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const [saving, setSaving] = useState<Record<string, boolean>>({});
  const [editing, setEditing] = useState<Record<string, EditBuffer>>({});

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await apiStatus.list();
      if (res.code === 200 && res.data) setList(res.data);
      else showMsg(res.message || "加载失败", "err");
    } catch {
      showMsg("加载失败", "err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
  }, []);

  const startEdit = (row: ApiStatus) => {
    setEditing((p) => ({
      ...p,
      [row.apiName]: { message: row.message || "", remark: row.remark || "" },
    }));
  };

  const cancelEdit = (apiName: string) => {
    setEditing((p) => {
      const next = { ...p };
      delete next[apiName];
      return next;
    });
  };

  const patchBuffer = (apiName: string, patch: Partial<EditBuffer>) => {
    setEditing((p) => ({
      ...p,
      [apiName]: { ...p[apiName], ...patch },
    }));
  };

  const saveEdit = async (apiName: string, currentStatus: boolean) => {
    const buf = editing[apiName];
    if (!buf) return;
    setSaving((p) => ({ ...p, [apiName]: true }));
    try {
      const res = await apiStatus.update(apiName, currentStatus, buf.message, buf.remark);
      if (res.code === 200) {
        showMsg("保存成功");
        cancelEdit(apiName);
        fetchList();
      } else {
        showMsg(res.message || "保存失败", "err");
      }
    } catch {
      showMsg("保存失败", "err");
    } finally {
      setSaving((p) => ({ ...p, [apiName]: false }));
    }
  };

  const toggleStatus = async (row: ApiStatus) => {
    const next = !row.status;
    setSaving((p) => ({ ...p, [row.apiName]: true }));
    setList((prev) => prev.map((x) => (x.apiName === row.apiName ? { ...x, status: next } : x)));
    try {
      const res = await apiStatus.update(row.apiName, next, row.message || "", row.remark || "");
      if (res.code === 200) {
        showMsg("更新成功");
        fetchList();
      } else {
        showMsg(res.message || "更新失败", "err");
        setList((prev) => prev.map((x) => (x.apiName === row.apiName ? { ...x, status: row.status } : x)));
      }
    } catch {
      showMsg("更新失败", "err");
      setList((prev) => prev.map((x) => (x.apiName === row.apiName ? { ...x, status: row.status } : x)));
    } finally {
      setSaving((p) => ({ ...p, [row.apiName]: false }));
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
        状态管理
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
        启用或禁用各个 API 接口，并设置提示信息与备注
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        <div className="flex items-center justify-between" style={{ marginBottom: 10 }}>
          <h2 style={headingStyle}>
            全部接口
            <span style={{ fontSize: 14, fontWeight: 400, color: "rgba(255,255,255,0.48)", marginLeft: 12 }}>
              共 {list.length} 项
            </span>
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
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无数据</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr>
                  <th style={thStyle}>API</th>
                  <th style={thStyle}>状态</th>
                  <th style={thStyle}>提示信息</th>
                  <th style={thStyle}>备注信息</th>
                  <th style={thStyle}>操作</th>
                </tr>
              </thead>
              <tbody>
                {list.map((row) => {
                  const busy = !!saving[row.apiName];
                  const buf = editing[row.apiName];
                  const isEditing = !!buf;

                  return (
                    <tr key={row.apiName}>
                      <td style={tdStyle}>{row.apiName}</td>

                      {/* 状态 badge */}
                      <td style={tdStyle}>
                        <span
                          style={{
                            padding: "2px 12px",
                            borderRadius: 980,
                            border: "1px solid rgba(255,255,255,0.16)",
                            backgroundColor: row.status ? "rgba(48,209,88,0.16)" : "rgba(255,69,58,0.16)",
                            color: row.status ? "#30d158" : "#ff453a",
                            fontSize: 12,
                            lineHeight: 1.43,
                            display: "inline-block",
                          }}
                        >
                          {row.status ? "启用" : "禁用"}
                        </span>
                      </td>

                      {/* 提示信息 */}
                      <td style={tdStyle}>
                        {isEditing ? (
                          <input
                            value={buf.message}
                            onChange={(e) => patchBuffer(row.apiName, { message: e.target.value })}
                            placeholder="禁用时填写原因，如：维护中"
                            autoFocus
                            style={inputStyle}
                          />
                        ) : (
                          <span style={{ color: row.message ? "rgba(255,255,255,0.72)" : "rgba(255,255,255,0.24)" }}>
                            {row.message || "—"}
                          </span>
                        )}
                      </td>

                      {/* 备注信息 */}
                      <td style={tdStyle}>
                        {isEditing ? (
                          <input
                            value={buf.remark}
                            onChange={(e) => patchBuffer(row.apiName, { remark: e.target.value })}
                            placeholder="接口用途说明"
                            style={inputStyle}
                          />
                        ) : (
                          <span style={{ color: row.remark ? "rgba(255,255,255,0.56)" : "rgba(255,255,255,0.20)", fontSize: 13 }}>
                            {row.remark || "—"}
                          </span>
                        )}
                      </td>

                      {/* 操作 */}
                      <td style={tdStyle}>
                        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "nowrap" }}>
                          {/* 切换状态：始终显示 */}
                          <button
                            onClick={() => toggleStatus(row)}
                            disabled={busy}
                            className="cursor-pointer"
                            style={{
                              padding: "6px 14px",
                              backgroundColor: row.status ? "rgba(255,69,58,0.16)" : "rgba(48,209,88,0.16)",
                              color: row.status ? "#ff453a" : "#30d158",
                              borderRadius: 980,
                              border: "1px solid rgba(255,255,255,0.12)",
                              fontSize: 12,
                              lineHeight: 1.43,
                              opacity: busy ? 0.6 : 1,
                              cursor: busy ? "not-allowed" : "pointer",
                              whiteSpace: "nowrap",
                            }}
                          >
                            {row.status ? "设为禁用" : "设为启用"}
                          </button>

                          {/* 编辑 / 保存 + 取消 */}
                          {isEditing ? (
                            <>
                              <button
                                onClick={() => saveEdit(row.apiName, row.status)}
                                disabled={busy}
                                className="cursor-pointer"
                                style={{
                                  padding: "6px 14px",
                                  backgroundColor: "var(--apple-blue)",
                                  color: "#ffffff",
                                  borderRadius: 980,
                                  border: "none",
                                  fontSize: 12,
                                  lineHeight: 1.43,
                                  opacity: busy ? 0.6 : 1,
                                  cursor: busy ? "not-allowed" : "pointer",
                                  whiteSpace: "nowrap",
                                }}
                              >
                                保存
                              </button>
                              <button
                                onClick={() => cancelEdit(row.apiName)}
                                disabled={busy}
                                className="cursor-pointer"
                                style={{
                                  padding: "6px 14px",
                                  backgroundColor: "var(--apple-surface-2)",
                                  color: "rgba(255,255,255,0.72)",
                                  borderRadius: 980,
                                  border: "none",
                                  fontSize: 12,
                                  lineHeight: 1.43,
                                  opacity: busy ? 0.6 : 1,
                                  cursor: busy ? "not-allowed" : "pointer",
                                  whiteSpace: "nowrap",
                                }}
                              >
                                取消
                              </button>
                            </>
                          ) : (
                            <button
                              onClick={() => startEdit(row)}
                              className="cursor-pointer"
                              style={{
                                padding: "6px 14px",
                                backgroundColor: "var(--apple-surface-2)",
                                color: "rgba(255,255,255,0.72)",
                                borderRadius: 980,
                                border: "none",
                                fontSize: 12,
                                lineHeight: 1.43,
                                whiteSpace: "nowrap",
                              }}
                            >
                              编辑
                            </button>
                          )}
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
