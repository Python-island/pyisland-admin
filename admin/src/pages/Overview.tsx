/**
 * @file Overview.tsx
 * @description 总览页面。
 * @description 展示用户/管理员数量、接口状态和版本信息统计。
 * @author 鸡哥
 */

import { useState, useEffect } from "react";
import {
  version,
  adminUsers,
  appUsers,
  apiStatus,
  weatherAdmin,
  wallpaperAdmin,
  issueFeedbackAdmin,
  agentAdmin,
  type AppVersion,
  type DailyActiveStats,
  type WeatherQuotaStatus,
  type AgentUsageStatsItem,
} from "../api";

const headingStyle: React.CSSProperties = {
  fontFamily: "var(--font-display)",
  fontSize: 21,
  fontWeight: 600,
  lineHeight: 1.19,
  letterSpacing: "0.231px",
  color: "#ffffff",
  marginBottom: 24,
};

/**
 * 总览页组件。
 * @returns 渲染系统运行概况和版本一览。
 */
export default function Overview() {
  const [versions, setVersions] = useState<AppVersion[]>([]);
  const [adminCount, setAdminCount] = useState(0);
  const [userCount, setUserCount] = useState(0);
  const [proCount, setProCount] = useState(0);
  const [apiAvailable, setApiAvailable] = useState(0);
  const [apiUnavailable, setApiUnavailable] = useState(0);
  const [pendingReviewCount, setPendingReviewCount] = useState(0);
  const [pendingFeedbackCount, setPendingFeedbackCount] = useState(0);
  const [dailyActive, setDailyActive] = useState<DailyActiveStats>({ today: 0, days: 7, series: [] });
  const [weatherQuota, setWeatherQuota] = useState<WeatherQuotaStatus | null>(null);
  const [usageStats, setUsageStats] = useState<AgentUsageStatsItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [vRes, adminRes, userRes, userListRes, sRes, dauRes, reviewRes, feedbackRes, weatherQuotaRes, usageRes] = await Promise.all([
          version.list(),
          adminUsers.count(),
          appUsers.count(),
          appUsers.list(),
          apiStatus.list(),
          appUsers.dailyActive(7),
          wallpaperAdmin.list({ status: "pending", page: 1, pageSize: 1000 }),
          issueFeedbackAdmin.list({ status: "pending", page: 1, pageSize: 1 }),
          weatherAdmin.quota(),
          agentAdmin.getUsageStats(),
        ]);
        if (vRes.code === 200 && vRes.data) setVersions(vRes.data);
        if (adminRes.code === 200 && adminRes.data !== undefined) setAdminCount(adminRes.data);
        if (userRes.code === 200 && userRes.data !== undefined) setUserCount(userRes.data);
        if (userListRes.code === 200 && userListRes.data) {
          setProCount(userListRes.data.filter((x) => x.role === "pro").length);
        }
        if (sRes.code === 200 && sRes.data) {
          setApiAvailable(sRes.data.filter((x) => x.status).length);
          setApiUnavailable(sRes.data.filter((x) => !x.status).length);
        }
        if (dauRes.code === 200 && dauRes.data) {
          setDailyActive(dauRes.data);
        }
        if (reviewRes.code === 200 && Array.isArray(reviewRes.data)) {
          setPendingReviewCount(reviewRes.data.length);
        }
        if (feedbackRes.code === 200 && feedbackRes.data) {
          setPendingFeedbackCount(Number(feedbackRes.data.total || 0));
        }
        if (weatherQuotaRes.code === 200 && weatherQuotaRes.data) {
          setWeatherQuota(weatherQuotaRes.data);
        }
        if (usageRes.code === 200 && usageRes.data) {
          setUsageStats(usageRes.data);
        }
      } catch {
        /* ignore */
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center" style={{ minHeight: "60vh" }}>
        <span style={{ color: "rgba(255,255,255,0.48)", fontSize: 17 }}>加载中...</span>
      </div>
    );
  }

  return (
    <div style={{ padding: "48px 48px" }}>
      {/* Hero */}
      <h1
        style={{
          fontFamily: "var(--font-display)",
          fontSize: 56,
          fontWeight: 600,
          lineHeight: 1.07,
          letterSpacing: "-0.28px",
          color: "#ffffff",
          margin: "0 0 8px",
        }}
      >
        总览
      </h1>
      <p
        style={{
          fontFamily: "var(--font-display)",
          fontSize: 21,
          fontWeight: 400,
          lineHeight: 1.19,
          letterSpacing: "0.231px",
          color: "rgba(255,255,255,0.56)",
          marginBottom: 48,
        }}
      >
        系统运行概况
      </p>

      {/* Stats */}
      <div className="flex" style={{ gap: 20, marginBottom: 32 }}>
        <UserCountCard userCount={userCount} proCount={proCount} adminCount={adminCount} />
        <DailyActiveCard data={dailyActive} />
        <ApiStatusCard available={apiAvailable} unavailable={apiUnavailable} />
        <TodoPendingCard pendingReviewCount={pendingReviewCount} pendingFeedbackCount={pendingFeedbackCount} />
        <VersionUpdateCard versions={versions} />
      </div>

      <h2 style={headingStyle}>接口一览</h2>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))",
          gap: 16,
          marginBottom: 40,
        }}
      >
        <ApiOverviewCard weatherQuota={weatherQuota} />
        <AgentUsageStatsCard stats={usageStats} />
      </div>

      {/* Version list */}
      <h2 style={headingStyle}>版本一览</h2>
      {versions.length === 0 ? (
        <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>暂无版本数据</p>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
            gap: 16,
          }}
        >
          {versions.map((v) => (
            <div
              key={v.id}
              style={{
                backgroundColor: "var(--apple-surface-1)",
                borderRadius: 12,
                padding: "20px 24px",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "flex-start",
                  marginBottom: 10,
                }}
              >
                <div
                  style={{
                    fontSize: 17,
                    fontWeight: 600,
                    color: "#ffffff",
                  }}
                >
                  {v.appName}
                </div>
                <div
                  style={{
                    padding: "2px 10px",
                    borderRadius: 980,
                    backgroundColor: "var(--apple-blue)",
                    fontSize: 12,
                    fontWeight: 600,
                    color: "#ffffff",
                    whiteSpace: "nowrap",
                    marginLeft: 12,
                  }}
                >
                  v{v.version}
                </div>
              </div>
              <div
                style={{
                  fontSize: 14,
                  color: "rgba(255,255,255,0.56)",
                  lineHeight: 1.43,
                  marginBottom: 8,
                }}
              >
                {v.description || "无描述"}
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: "rgba(255,255,255,0.32)",
                  marginBottom: 4,
                }}
              >
                更新次数 {v.updateCount ?? 0}
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: "rgba(255,255,255,0.32)",
                }}
              >
                更新于 {v.updatedAt}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function ApiOverviewCard({ weatherQuota }: { weatherQuota: WeatherQuotaStatus | null }) {
  const used = weatherQuota?.used ?? 0;
  const limit = weatherQuota?.limit ?? 50000;
  const remaining = weatherQuota?.remaining ?? Math.max(0, limit - used);
  const month = weatherQuota?.month ?? "-";
  const fused = Boolean(weatherQuota?.fused);

  return (
    <div
      style={{
        backgroundColor: "var(--apple-surface-1)",
        borderRadius: 12,
        padding: "24px 28px",
      }}
    >
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.33,
          letterSpacing: "-0.12px",
          color: "rgba(255,255,255,0.48)",
          textTransform: "uppercase",
          marginBottom: 16,
        }}
      >
        和风天气接口（本月）
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 24 }}>
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: fused ? "#ff453a" : "#ffffff",
            }}
          >
            {used}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>
            调用次数
          </div>
        </div>
        <div style={{ width: 1, height: 48, backgroundColor: "rgba(255,255,255,0.08)" }} />
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 24,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "var(--apple-link-dark)",
            }}
          >
            {remaining}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>
            剩余 / {limit}
          </div>
        </div>
      </div>
      <div
        style={{
          marginTop: 12,
          fontSize: 12,
          color: fused ? "#ff9f94" : "rgba(255,255,255,0.48)",
        }}
      >
        {month} {fused ? "· 已熔断" : "· 正常"}
      </div>
    </div>
  );
}

function VersionUpdateCard({ versions }: { versions: AppVersion[] }) {
  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "var(--apple-surface-1)",
        borderRadius: 12,
        padding: "24px 20px",
      }}
    >
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.33,
          letterSpacing: "-0.12px",
          color: "rgba(255,255,255,0.48)",
          textTransform: "uppercase",
          marginBottom: 12,
          whiteSpace: "nowrap",
        }}
      >
        应用更新数据
      </div>

      {versions.length === 0 ? (
        <div style={{ fontSize: 13, color: "rgba(255,255,255,0.48)", whiteSpace: "nowrap" }}>暂无版本数据</div>
      ) : (
        <div style={{ display: "grid", gap: 8 }}>
          {versions.map((v) => (
            <div
              key={v.id}
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                gap: 8,
              }}
            >
              <div
                style={{
                  fontSize: 13,
                  color: "rgba(255,255,255,0.88)",
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
                title={v.appName}
              >
                {v.appName}
              </div>
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.56)", whiteSpace: "nowrap" }}>
                v{v.version} · {v.updateCount ?? 0} 次
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function TodoPendingCard({
  pendingReviewCount,
  pendingFeedbackCount,
}: {
  pendingReviewCount: number;
  pendingFeedbackCount: number;
}) {
  const total = pendingReviewCount + pendingFeedbackCount;
  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "var(--apple-surface-1)",
        borderRadius: 12,
        padding: "24px 28px",
      }}
    >
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.33,
          letterSpacing: "-0.12px",
          color: "rgba(255,255,255,0.48)",
          textTransform: "uppercase",
          marginBottom: 16,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 8,
          whiteSpace: "nowrap",
        }}
      >
        <span>待办事项</span>
        <span
          style={{
            fontSize: 11,
            fontWeight: 500,
            color: "rgba(255,255,255,0.56)",
            textTransform: "none",
            letterSpacing: 0,
            whiteSpace: "nowrap",
          }}
        >
          总 {total}
        </span>
      </div>
      <div style={{ display: "flex", gap: 24, alignItems: "center" }}>
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "#ffd60a",
            }}
          >
            {pendingReviewCount}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>
            待审核
          </div>
        </div>
        <div style={{ width: 1, height: 48, backgroundColor: "rgba(255,255,255,0.08)" }} />
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "var(--apple-link-dark)",
            }}
          >
            {pendingFeedbackCount}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>
            待反馈处理
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * 用户数量卡片：展示当前普通用户、Pro 用户与管理员人数。
 * @param userCount - 应用用户数量（普通 + Pro）。
 * @param proCount - Pro 用户数量。
 * @param adminCount - 管理员数量。
 */
function UserCountCard({
  userCount,
  proCount,
  adminCount,
}: {
  userCount: number;
  proCount: number;
  adminCount: number;
}) {
  const normalUserCount = Math.max(0, userCount - proCount);
  const total = userCount + adminCount;
  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "var(--apple-surface-1)",
        borderRadius: 12,
        padding: "24px 28px",
      }}
    >
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.33,
          letterSpacing: "-0.12px",
          color: "rgba(255,255,255,0.48)",
          textTransform: "uppercase",
          marginBottom: 16,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 8,
        }}
      >
        <span style={{ whiteSpace: "nowrap" }}>用户数量</span>
        <span
          style={{
            fontSize: 11,
            fontWeight: 500,
            color: "rgba(255,255,255,0.56)",
            textTransform: "none",
            letterSpacing: 0,
            whiteSpace: "nowrap",
          }}
          title="总计"
        >
          总 {total}
        </span>
      </div>
      <div style={{ display: "flex", gap: 24, alignItems: "center" }}>
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "#ffffff",
            }}
          >
            {normalUserCount}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>普通用户</div>
        </div>
        <div style={{ width: 1, height: 48, backgroundColor: "rgba(255,255,255,0.08)" }} />
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "var(--apple-link-dark)",
            }}
          >
            {proCount}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>Pro 用户</div>
        </div>
        <div style={{ width: 1, height: 48, backgroundColor: "rgba(255,255,255,0.08)" }} />
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "var(--apple-link-dark)",
            }}
          >
            {adminCount}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>管理员</div>
        </div>
      </div>
    </div>
  );
}

function DailyActiveCard({ data }: { data: DailyActiveStats }) {
  const total = data.series.reduce((sum, item) => sum + (item.count || 0), 0);
  const avg = data.series.length > 0 ? (total / data.series.length) : 0;
  const avgText = Number.isFinite(avg) ? avg.toFixed(1) : "0.0";
  const latestDate = data.series.length > 0 ? data.series[data.series.length - 1].date : "-";

  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "var(--apple-surface-1)",
        borderRadius: 12,
        padding: "24px 28px",
      }}
    >
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.33,
          letterSpacing: "-0.12px",
          color: "rgba(255,255,255,0.48)",
          textTransform: "uppercase",
          marginBottom: 16,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 8,
        }}
      >
        <span style={{ whiteSpace: "nowrap" }}>日活跃用户</span>
        <span
          style={{
            fontSize: 11,
            fontWeight: 500,
            color: "rgba(255,255,255,0.56)",
            textTransform: "none",
            letterSpacing: 0,
            whiteSpace: "nowrap",
          }}
          title="最近统计日期"
        >
          {latestDate}
        </span>
      </div>
      <div style={{ display: "flex", gap: 24, alignItems: "center" }}>
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "#ffffff",
            }}
          >
            {data.today}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>今日活跃</div>
        </div>
        <div style={{ width: 1, height: 48, backgroundColor: "rgba(255,255,255,0.08)" }} />
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 34,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "var(--apple-link-dark)",
            }}
          >
            {avgText}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>近{data.days}天均值</div>
        </div>
      </div>
    </div>
  );
}

function AgentUsageStatsCard({ stats }: { stats: AgentUsageStatsItem[] }) {
  const totalRequests = stats.reduce((sum, s) => sum + (s.totalRequestCount || 0), 0);
  const totalTokens = stats.reduce(
    (sum, s) => sum + (s.totalInputTokens || 0) + (s.totalOutputTokens || 0),
    0
  );
  const totalCostFen = stats.reduce((sum, s) => sum + parseFloat(s.totalCostFen || "0"), 0);

  const fmtTokens = (n: number) => {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(2) + "M";
    if (n >= 1_000) return (n / 1_000).toFixed(1) + "K";
    return String(n);
  };
  const fmtCost = (fen: number) => {
    if (fen >= 100) return (fen / 100).toFixed(2) + " 元";
    return fen.toFixed(4) + " 分";
  };

  return (
    <div
      style={{
        backgroundColor: "var(--apple-surface-1)",
        borderRadius: 12,
        padding: "24px 28px",
      }}
    >
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.33,
          letterSpacing: "-0.12px",
          color: "rgba(255,255,255,0.48)",
          textTransform: "uppercase",
          marginBottom: 16,
        }}
      >
        Agent 模型用量（全体用户）
      </div>

      {/* 汇总行 */}
      <div style={{ display: "flex", alignItems: "center", gap: 24, marginBottom: 16 }}>
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "#ffffff",
            }}
          >
            {totalRequests}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>
            总请求次数
          </div>
        </div>
        <div style={{ width: 1, height: 48, backgroundColor: "rgba(255,255,255,0.08)" }} />
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 24,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "var(--apple-link-dark)",
            }}
          >
            {fmtTokens(totalTokens)}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>
            总 Tokens
          </div>
        </div>
        <div style={{ width: 1, height: 48, backgroundColor: "rgba(255,255,255,0.08)" }} />
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 24,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "#30d158",
            }}
          >
            {fmtCost(totalCostFen)}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>
            总费用
          </div>
        </div>
      </div>

      {/* 分模型明细 */}
      {stats.length === 0 ? (
        <div style={{ fontSize: 13, color: "rgba(255,255,255,0.48)" }}>暂无用量数据</div>
      ) : (
        <div style={{ display: "grid", gap: 6 }}>
          {stats.map((s) => {
            const modelTokens = (s.totalInputTokens || 0) + (s.totalOutputTokens || 0);
            const modelCost = parseFloat(s.totalCostFen || "0");
            return (
              <div
                key={s.modelName}
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  gap: 8,
                }}
              >
                <div
                  style={{
                    fontSize: 13,
                    color: "rgba(255,255,255,0.88)",
                    whiteSpace: "nowrap",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                  }}
                  title={s.modelName}
                >
                  {s.modelName}
                </div>
                <div style={{ fontSize: 12, color: "rgba(255,255,255,0.56)", whiteSpace: "nowrap" }}>
                  {s.totalRequestCount} 次 · {fmtTokens(modelTokens)} · {fmtCost(modelCost)}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function ApiStatusCard({ available, unavailable }: { available: number; unavailable: number }) {
  return (
    <div
      style={{
        flex: 1,
        backgroundColor: "var(--apple-surface-1)",
        borderRadius: 12,
        padding: "24px 28px",
      }}
    >
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.33,
          letterSpacing: "-0.12px",
          color: "rgba(255,255,255,0.48)",
          textTransform: "uppercase",
          marginBottom: 16,
          whiteSpace: "nowrap",
        }}
      >
        接口状态
      </div>
      <div style={{ display: "flex", gap: 24, alignItems: "center" }}>
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: "#30d158",
            }}
          >
            {available}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>可用</div>
        </div>
        <div style={{ width: 1, height: 48, backgroundColor: "rgba(255,255,255,0.08)" }} />
        <div>
          <div
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              color: unavailable > 0 ? "#ff453a" : "rgba(255,255,255,0.32)",
            }}
          >
            {unavailable}
          </div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.40)", marginTop: 4, whiteSpace: "nowrap" }}>不可用</div>
        </div>
      </div>
    </div>
  );
}
