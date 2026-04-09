import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { version, clearToken, getUsername, type AppVersion } from "../api";

export default function Dashboard() {
  const navigate = useNavigate();
  const [appName, setAppName] = useState("");
  const [searchName, setSearchName] = useState("");
  const [current, setCurrent] = useState<AppVersion | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  // form
  const [formAppName, setFormAppName] = useState("");
  const [formVersion, setFormVersion] = useState("");
  const [formDesc, setFormDesc] = useState("");
  const [formMode, setFormMode] = useState<"create" | "update">("create");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
    setTimeout(() => setMsg(""), 3000);
  };

  const handleSearch = useCallback(async () => {
    if (!searchName.trim()) return;
    setLoading(true);
    try {
      const res = await version.get(searchName.trim());
      if (res.code === 200 && res.data) {
        setCurrent(res.data);
        setNotFound(false);
      } else {
        setCurrent(null);
        setNotFound(true);
      }
    } catch {
      showMsg("查询失败", "err");
    } finally {
      setLoading(false);
    }
  }, [searchName]);

  useEffect(() => {
    if (appName) {
      setSearchName(appName);
    }
  }, [appName]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await version.create(formAppName, formVersion, formDesc);
      if (res.code === 200) {
        showMsg("应用创建成功");
        setAppName(formAppName);
        setSearchName(formAppName);
        setFormAppName("");
        setFormVersion("");
        setFormDesc("");
        // refresh
        const r = await version.get(formAppName || appName);
        if (r.code === 200 && r.data) {
          setCurrent(r.data);
          setNotFound(false);
        }
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("创建失败", "err");
    }
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const name = formAppName || current?.appName || "";
      const res = await version.update(name, formVersion, formDesc);
      if (res.code === 200) {
        showMsg("版本更新成功");
        setCurrent(res.data!);
        setFormVersion("");
        setFormDesc("");
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("更新失败", "err");
    }
  };

  const handleDelete = async () => {
    if (!current) return;
    if (!confirm(`确定删除 ${current.appName} 吗？`)) return;
    try {
      const res = await version.delete(current.appName);
      if (res.code === 200) {
        showMsg("删除成功");
        setCurrent(null);
        setNotFound(true);
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("删除失败", "err");
    }
  };

  const handleLogout = () => {
    clearToken();
    navigate("/login");
  };

  const fillUpdateForm = () => {
    if (!current) return;
    setFormMode("update");
    setFormAppName(current.appName);
    setFormVersion(current.version);
    setFormDesc(current.description || "");
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900">
      {/* Header */}
      <header className="border-b border-white/10 bg-white/5 backdrop-blur-xl">
        <div className="max-w-5xl mx-auto px-6 py-4 flex items-center justify-between">
          <h1 className="text-xl font-bold text-white">PyIsland Admin</h1>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-400">{getUsername()}</span>
            <button
              onClick={handleLogout}
              className="text-sm text-red-400 hover:text-red-300 transition cursor-pointer"
            >
              退出登录
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-8 space-y-6">
        {/* Toast */}
        {msg && (
          <div
            className={`fixed top-6 right-6 px-5 py-3 rounded-lg text-sm font-medium shadow-lg z-50 transition ${
              msgType === "ok"
                ? "bg-emerald-500/90 text-white"
                : "bg-red-500/90 text-white"
            }`}
          >
            {msg}
          </div>
        )}

        {/* Search */}
        <section className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-6">
          <h2 className="text-lg font-semibold text-white mb-4">查询版本</h2>
          <div className="flex gap-3">
            <input
              type="text"
              value={searchName}
              onChange={(e) => setSearchName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              placeholder="输入应用名称"
              className="flex-1 px-4 py-2.5 bg-white/5 border border-white/10 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
            />
            <button
              onClick={handleSearch}
              disabled={loading}
              className="px-6 py-2.5 bg-blue-600 hover:bg-blue-500 disabled:bg-blue-600/50 text-white font-medium rounded-lg transition cursor-pointer"
            >
              {loading ? "查询中..." : "查询"}
            </button>
          </div>
        </section>

        {/* Result */}
        {current && (
          <section className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-white">版本信息</h2>
              <div className="flex gap-2">
                <button
                  onClick={fillUpdateForm}
                  className="px-4 py-1.5 text-sm bg-amber-600 hover:bg-amber-500 text-white rounded-lg transition cursor-pointer"
                >
                  编辑
                </button>
                <button
                  onClick={handleDelete}
                  className="px-4 py-1.5 text-sm bg-red-600 hover:bg-red-500 text-white rounded-lg transition cursor-pointer"
                >
                  删除
                </button>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <InfoItem label="应用名称" value={current.appName} />
              <InfoItem label="版本号" value={current.version} />
              <InfoItem label="描述" value={current.description || "-"} />
              <InfoItem label="更新时间" value={current.updatedAt} />
            </div>
          </section>
        )}

        {notFound && !current && (
          <div className="bg-amber-500/10 border border-amber-500/20 rounded-2xl p-6 text-center text-amber-300">
            未找到该应用的版本信息
          </div>
        )}

        {/* Create / Update Form */}
        <section className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-6">
          <div className="flex gap-3 mb-4">
            <button
              onClick={() => {
                setFormMode("create");
                setFormAppName("");
                setFormVersion("");
                setFormDesc("");
              }}
              className={`px-4 py-1.5 text-sm rounded-lg transition cursor-pointer ${
                formMode === "create"
                  ? "bg-blue-600 text-white"
                  : "bg-white/5 text-slate-400 hover:text-white"
              }`}
            >
              创建应用
            </button>
            <button
              onClick={() => setFormMode("update")}
              className={`px-4 py-1.5 text-sm rounded-lg transition cursor-pointer ${
                formMode === "update"
                  ? "bg-blue-600 text-white"
                  : "bg-white/5 text-slate-400 hover:text-white"
              }`}
            >
              更新版本
            </button>
          </div>

          <form
            onSubmit={formMode === "create" ? handleCreate : handleUpdate}
            className="space-y-4"
          >
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1.5">
                  应用名称
                </label>
                <input
                  type="text"
                  value={formAppName}
                  onChange={(e) => setFormAppName(e.target.value)}
                  placeholder="如 pyisland"
                  required={formMode === "create"}
                  className="w-full px-4 py-2.5 bg-white/5 border border-white/10 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1.5">
                  版本号
                </label>
                <input
                  type="text"
                  value={formVersion}
                  onChange={(e) => setFormVersion(e.target.value)}
                  placeholder="如 1.0.0"
                  required
                  className="w-full px-4 py-2.5 bg-white/5 border border-white/10 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1.5">
                描述
              </label>
              <input
                type="text"
                value={formDesc}
                onChange={(e) => setFormDesc(e.target.value)}
                placeholder="版本描述（可选）"
                className="w-full px-4 py-2.5 bg-white/5 border border-white/10 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
              />
            </div>
            <button
              type="submit"
              className="px-6 py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-lg transition cursor-pointer"
            >
              {formMode === "create" ? "创建" : "更新"}
            </button>
          </form>
        </section>
      </main>
    </div>
  );
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span className="text-xs text-slate-500 uppercase tracking-wide">
        {label}
      </span>
      <p className="text-white mt-0.5">{value}</p>
    </div>
  );
}
