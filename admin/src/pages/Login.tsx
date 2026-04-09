import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { auth, setToken, setUsername } from "../api";

export default function Login() {
  const navigate = useNavigate();
  const [username, setUsernameInput] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await auth.login(username, password);
      if (res.code === 200 && res.data) {
        setToken(res.data.token);
        setUsername(res.data.username);
        navigate("/");
      } else {
        setError(res.message);
      }
    } catch {
      setError("网络错误，请重试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center p-4"
      style={{ backgroundColor: "var(--apple-black)" }}
    >
      <div className="w-full" style={{ maxWidth: 400 }}>
        <div className="text-center mb-10">
          <h1
            className="text-white"
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 40,
              fontWeight: 600,
              lineHeight: 1.1,
              letterSpacing: "normal",
            }}
          >
            PyIsland
          </h1>
          <p
            className="mt-2"
            style={{
              fontFamily: "var(--font-body)",
              fontSize: 21,
              fontWeight: 400,
              lineHeight: 1.19,
              letterSpacing: "0.231px",
              color: "rgba(255, 255, 255, 0.56)",
            }}
          >
            管理面板
          </p>
        </div>

        <div
          className="p-10"
          style={{
            backgroundColor: "var(--apple-surface-1)",
            borderRadius: 12,
          }}
        >
          <h2
            className="text-white mb-8"
            style={{
              fontFamily: "var(--font-display)",
              fontSize: 28,
              fontWeight: 600,
              lineHeight: 1.14,
              letterSpacing: "0.196px",
            }}
          >
            登录
          </h2>

          <form onSubmit={handleSubmit}>
            <div className="mb-5">
              <label
                className="block mb-2"
                style={{
                  fontSize: 14,
                  fontWeight: 600,
                  lineHeight: 1.29,
                  letterSpacing: "-0.224px",
                  color: "rgba(255, 255, 255, 0.64)",
                }}
              >
                用户名
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsernameInput(e.target.value)}
                placeholder="请输入用户名"
                required
                className="w-full outline-none transition-all"
                style={{
                  padding: "10px 14px",
                  backgroundColor: "var(--apple-surface-2)",
                  borderRadius: 8,
                  border: "none",
                  color: "#ffffff",
                  fontSize: 17,
                  lineHeight: 1.47,
                  letterSpacing: "-0.374px",
                }}
              />
            </div>

            <div className="mb-5">
              <label
                className="block mb-2"
                style={{
                  fontSize: 14,
                  fontWeight: 600,
                  lineHeight: 1.29,
                  letterSpacing: "-0.224px",
                  color: "rgba(255, 255, 255, 0.64)",
                }}
              >
                密码
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入密码"
                required
                className="w-full outline-none transition-all"
                style={{
                  padding: "10px 14px",
                  backgroundColor: "var(--apple-surface-2)",
                  borderRadius: 8,
                  border: "none",
                  color: "#ffffff",
                  fontSize: 17,
                  lineHeight: 1.47,
                  letterSpacing: "-0.374px",
                }}
              />
            </div>

            {error && (
              <div
                className="mb-5"
                style={{
                  padding: "10px 14px",
                  borderRadius: 8,
                  backgroundColor: "rgba(255, 59, 48, 0.12)",
                  color: "#ff453a",
                  fontSize: 14,
                  lineHeight: 1.29,
                  letterSpacing: "-0.224px",
                }}
              >
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full cursor-pointer transition-all"
              style={{
                padding: "12px 15px",
                backgroundColor: "var(--apple-blue)",
                color: "#ffffff",
                borderRadius: 980,
                border: "none",
                fontSize: 17,
                fontWeight: 400,
                lineHeight: 1,
                letterSpacing: "normal",
                opacity: loading ? 0.5 : 1,
              }}
            >
              {loading ? "请稍候..." : "登录"}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
