import { useEffect, useMemo, useState } from "react";
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

export default function ApiStatusManage() {
  const [list, setList] = useState<ApiStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const [saving, setSaving] = useState<Record<string, boolean>>({});

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

  const byName = useMemo(() => {
    const map = new Map<string, ApiStatus>();
    list.forEach((x) => map.set(x.apiName, x));
    return map;
  }, [list]);

  const updateRow = (apiName: string, patch: Partial<ApiStatus>) => {
    setList((prev) =>
      prev.map((x) => (x.apiName === apiName ? { ...x, ...patch } : x))
    );
  };

  const toggleStatus = async (apiName: string) => {
    const row = byName.get(apiName);
    if (!row) return;
    const next = !row.status;
    updateRow(apiName, { status: next });
    setSaving((p) => ({ ...p, [apiName]: true }));
    try {
      const res = await apiStatus.update(apiName, next, row.message || "", row.remark || "");
      if (res.code === 200) {
        showMsg("更新成功");
        fetchList();
      } else {
        showMsg(res.message || "更新失败", "err");
        updateRow(apiName, { status: row.status });
      }
    } catch {
      showMsg("更新失败", "err");
      updateRow(apiName, { status: row.status });
    } finally {
      setSaving((p) => ({ ...p, [apiName]: false }));
    }
  };

  const saveMessage = async (apiName: string) => {
    const row = byName.get(apiName);
    if (!row) return;
    setSaving((p) => ({ ...p, [apiName]: true }));
    try {
      const res = await apiStatus.update(apiName, row.status, row.message || "", row.remark || "");
      if (res.code === 200) {
        showMsg("保存成功");
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
        启用或禁用各个 API 接口，并设置提示信息
      </p>

      <MessageDialog visible={!!msg} type={msgType} message={msg} onClose={() => setMsg("")} />

      <div style={cardStyle}>
        <div className="flex items-center justify-between" style={{ marginBottom: 10 }}>
          <h2 style={headingStyle}>
            全部接口
            <span
              style={{
                fontSize: 14,
                fontWeight: 400,
                color: "rgba(255,255,255,0.48)",
                marginLeft: 12,
              }}
            >
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
                  return (
                    <tr key={row.apiName}>
                      <td style={tdStyle}>{row.apiName}</td>
                      <td style={tdStyle}>
                        <span
                          style={{
                            padding: "2px 12px",
                            borderRadius: 980,
                            border: "1px solid rgba(255,255,255,0.16)",
                            backgroundColor: row.status
                              ? "rgba(48, 209, 88, 0.16)"
                              : "rgba(255, 69, 58, 0.16)",
                            color: row.status ? "#30d158" : "#ff453a",
                            fontSize: 12,
                            lineHeight: 1.43,
                            display: "inline-block",
                          }}
                        >
                          {row.status ? "启用" : "禁用"}
                        </span>
                      </td>
                      <td style={tdStyle}>
                        <input
                          value={row.message || ""}
                          onChange={(e) =>
                            updateRow(row.apiName, { message: e.target.value })
                          }
                          placeholder="禁用时填写原因，如：维护中"
                          className="outline-none"
                          style={{
                            padding: "7px 12px",
                            backgroundColor: "var(--apple-surface-2)",
                            borderRadius: 8,
                            border: "none",
                            color: "#ffffff",
                            fontSize: 14,
                            lineHeight: 1.43,
                            letterSpacing: "-0.224px",
                            width: 200,
                          }}
                        />
                      </td>
                      <td style={tdStyle}>
                        <input
                          value={row.remark || ""}
                          onChange={(e) =>
                            updateRow(row.apiName, { remark: e.target.value })
                          }
                          placeholder="接口用途说明"
                          className="outline-none"
                          style={{
                            padding: "7px 12px",
                            backgroundColor: "var(--apple-surface-2)",
                            borderRadius: 8,
                            border: "none",
                            color: "#ffffff",
                            fontSize: 14,
                            lineHeight: 1.43,
                            letterSpacing: "-0.224px",
                            width: 200,
                          }}
                        />
                      </td>
                      <td style={tdStyle}>
                        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                          <button
                            onClick={() => toggleStatus(row.apiName)}
                            disabled={busy}
                            className="cursor-pointer"
                            style={{
                              padding: "6px 14px",
                              backgroundColor: row.status
                                ? "rgba(255, 69, 58, 0.16)"
                                : "rgba(48, 209, 88, 0.16)",
                              color: row.status ? "#ff453a" : "#30d158",
                              borderRadius: 980,
                              border: "1px solid rgba(255,255,255,0.12)",
                              fontSize: 12,
                              lineHeight: 1.43,
                              opacity: busy ? 0.6 : 1,
                              cursor: busy ? "not-allowed" : "pointer",
                            }}
                          >
                            {row.status ? "设为禁用" : "设为启用"}
                          </button>
                          <button
                            onClick={() => saveMessage(row.apiName)}
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
                            }}
                          >
                            保存
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
