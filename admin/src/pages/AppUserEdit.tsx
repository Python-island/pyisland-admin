/**
 * @file AppUserEdit.tsx
 * @description 管理员编辑普通用户信息页面。
 * @description 支持切换用户、修改头像、性别、生日以及可选重置密码。
 * @author 鸡哥
 */

import { useState, useEffect, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import { appUsers, uploadUserAvatar, sanitizeUrl, type AppUserInfo, type Gender } from "../api";
import MessageDialog from "../components/MessageDialog";

const inputStyle: React.CSSProperties = {
  padding: "10px 14px",
  backgroundColor: "var(--apple-surface-2)",
  borderRadius: 8,
  border: "none",
  color: "#ffffff",
  fontSize: 17,
  lineHeight: 1.47,
  letterSpacing: "-0.374px",
  width: "100%",
  outline: "none",
};

const readonlyStyle: React.CSSProperties = {
  ...inputStyle,
  color: "rgba(255,255,255,0.56)",
  cursor: "not-allowed",
};

const labelStyle: React.CSSProperties = {
  display: "block",
  marginBottom: 8,
  fontSize: 14,
  fontWeight: 600,
  lineHeight: 1.29,
  letterSpacing: "-0.224px",
  color: "rgba(255,255,255,0.64)",
};

export default function AppUserEdit() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selected = searchParams.get("username") || "";

  const [userList, setUserList] = useState<AppUserInfo[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingUser, setLoadingUser] = useState(false);

  const [email, setEmail] = useState("");
  const [createdAt, setCreatedAt] = useState("");
  const [balanceYuan, setBalanceYuan] = useState("");
  const [balanceSubmitting, setBalanceSubmitting] = useState(false);
  const [gender, setGender] = useState<Gender>("undisclosed");
  const [genderCustom, setGenderCustom] = useState("");
  const [birthday, setBirthday] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPwd, setConfirmPwd] = useState("");
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const [submitting, setSubmitting] = useState(false);

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  useEffect(() => {
    appUsers.list()
      .then((res) => {
        if (res.code === 200 && res.data) setUserList(res.data);
      })
      .catch(() => {})
      .finally(() => setLoadingList(false));
  }, []);

  useEffect(() => {
    if (!selected) return;
    setLoadingUser(true);
    appUsers.getProfile(selected)
      .then((res) => {
        if (res.code === 200 && res.data) {
          setEmail(res.data.email || "");
          setCreatedAt(res.data.createdAt || "");
          setGender((res.data.gender as Gender) || "undisclosed");
          setGenderCustom(res.data.genderCustom || "");
          setBirthday(res.data.birthday || "");
          setAvatarPreview(res.data.avatar || null);
          setAvatarFile(null);
          setPassword("");
          setConfirmPwd("");
          const fen = typeof res.data.balanceFen === "number" ? res.data.balanceFen : 0;
          setBalanceYuan((fen / 100).toFixed(2));
        } else {
          showMsg(res.message || "获取用户信息失败", "err");
        }
      })
      .catch(() => showMsg("获取用户信息失败", "err"))
      .finally(() => setLoadingUser(false));
  }, [selected]);

  const handleSelectUser = (username: string) => {
    if (username) {
      setSearchParams({ username });
    } else {
      setSearchParams({});
    }
  };

  const handleAvatar = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      showMsg("头像文件不能超过 5MB", "err");
      return;
    }
    setAvatarFile(file);
    const reader = new FileReader();
    reader.onload = () => setAvatarPreview(reader.result as string);
    reader.readAsDataURL(file);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    if (!selected) {
      showMsg("请先选择要修改的用户", "err");
      return;
    }
    if (password && password !== confirmPwd) {
      showMsg("两次密码输入不一致", "err");
      return;
    }
    setSubmitting(true);
    try {
      let avatarUrl: string | null = avatarPreview;
      if (avatarFile) {
        const upRes = await uploadUserAvatar(avatarFile);
        if (upRes.code !== 200 || !upRes.data) {
          showMsg(upRes.message || "头像上传失败", "err");
          return;
        }
        avatarUrl = upRes.data;
      }
      const extras = {
        gender,
        genderCustom: gender === "custom" ? genderCustom.trim() || null : null,
        birthday: birthday || null,
      };
      const res = await appUsers.updateProfile(
        selected,
        password ? password : null,
        avatarUrl,
        extras
      );
      if (res.code === 200) {
        showMsg("保存成功");
        setAvatarFile(null);
        setPassword("");
        setConfirmPwd("");
        if (avatarUrl !== null) setAvatarPreview(avatarUrl);
      } else {
        showMsg(res.message || "保存失败", "err");
      }
    } catch {
      showMsg("保存失败", "err");
    } finally {
      setSubmitting(false);
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
        修改用户信息
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
        选择一个普通用户，修改头像、性别、生日或重置密码
      </p>

      <MessageDialog
        visible={!!msg}
        type={msgType}
        message={msg}
        onClose={() => setMsg("")}
      />

      <div
        style={{
          backgroundColor: "var(--apple-surface-1)",
          borderRadius: 12,
          padding: 32,
        }}
      >
        <div style={{ marginBottom: 24 }}>
          <label style={labelStyle}>选择用户</label>
          <select
            value={selected}
            onChange={(e) => handleSelectUser(e.target.value)}
            style={{ ...inputStyle, appearance: "auto" }}
            disabled={loadingList}
          >
            <option value="">{loadingList ? "加载中..." : "请选择用户"}</option>
            {userList.map((u) => (
              <option key={u.id} value={u.username}>
                {u.username} ({u.email})
              </option>
            ))}
          </select>
        </div>

        {!selected ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>请先选择需要修改的用户。</p>
        ) : loadingUser ? (
          <p style={{ color: "rgba(255,255,255,0.48)", fontSize: 14 }}>加载用户信息中...</p>
        ) : (
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: 24, display: "flex", alignItems: "center", gap: 20 }}>
              <div
                onClick={() => fileRef.current?.click()}
                className="cursor-pointer"
                style={{
                  width: 72,
                  height: 72,
                  borderRadius: "50%",
                  backgroundColor: "var(--apple-surface-2)",
                  backgroundImage: sanitizeUrl(avatarPreview) ? `url(${sanitizeUrl(avatarPreview)})` : "none",
                  backgroundSize: "cover",
                  backgroundPosition: "center",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: 24,
                  color: "rgba(255,255,255,0.32)",
                  flexShrink: 0,
                }}
              >
                {!avatarPreview && selected.charAt(0).toUpperCase()}
              </div>
              <div>
                <label style={labelStyle}>头像</label>
                <div style={{ fontSize: 12, color: "rgba(255,255,255,0.4)" }}>
                  点击圆圈上传新头像，R2 存储，JPG/PNG，最大 5MB
                </div>
              </div>
              <input
                ref={fileRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handleAvatar}
                style={{ display: "none" }}
              />
            </div>

            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>用户名</label>
              <input type="text" value={selected} readOnly style={readonlyStyle} />
            </div>

            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>邮箱</label>
              <input type="text" value={email} readOnly style={readonlyStyle} />
            </div>

            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>性别</label>
              <select
                value={gender}
                onChange={(e) => setGender(e.target.value as Gender)}
                style={{ ...inputStyle, appearance: "auto" }}
              >
                <option value="male">男</option>
                <option value="female">女</option>
                <option value="custom">自定义</option>
                <option value="undisclosed">不愿透露</option>
              </select>
            </div>

            {gender === "custom" && (
              <div style={{ marginBottom: 16 }}>
                <label style={labelStyle}>自定义性别</label>
                <input
                  type="text"
                  value={genderCustom}
                  onChange={(e) => setGenderCustom(e.target.value)}
                  placeholder="最多 64 个字符"
                  maxLength={64}
                  style={inputStyle}
                />
              </div>
            )}

            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>生日</label>
              <input
                type="date"
                value={birthday}
                onChange={(e) => setBirthday(e.target.value)}
                max={new Date().toISOString().slice(0, 10)}
                style={inputStyle}
              />
            </div>

            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>重置密码（可选）</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="留空则保持当前密码"
                style={inputStyle}
              />
            </div>

            <div style={{ marginBottom: 24 }}>
              <label style={labelStyle}>确认新密码</label>
              <input
                type="password"
                value={confirmPwd}
                onChange={(e) => setConfirmPwd(e.target.value)}
                placeholder="再次输入新密码"
                disabled={!password}
                style={password ? inputStyle : readonlyStyle}
              />
            </div>

            <div
              style={{
                marginBottom: 24,
                padding: 20,
                backgroundColor: "var(--apple-surface-2)",
                borderRadius: 10,
              }}
            >
              <label style={labelStyle}>用户余额（元）</label>
              <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={balanceYuan}
                  onChange={(e) => setBalanceYuan(e.target.value)}
                  placeholder="0.00"
                  style={{ ...inputStyle, flex: 1 }}
                />
                <button
                  type="button"
                  disabled={balanceSubmitting}
                  className={balanceSubmitting ? "" : "cursor-pointer"}
                  onClick={async () => {
                    const yuan = parseFloat(balanceYuan);
                    if (isNaN(yuan) || yuan < 0) {
                      showMsg("余额不能为负数", "err");
                      return;
                    }
                    const fen = Math.round(yuan * 100);
                    setBalanceSubmitting(true);
                    try {
                      const res = await appUsers.updateBalance(selected, fen);
                      if (res.code === 200) {
                        showMsg("余额已更新");
                        setBalanceYuan((fen / 100).toFixed(2));
                      } else {
                        showMsg(res.message || "更新余额失败", "err");
                      }
                    } catch {
                      showMsg("更新余额失败", "err");
                    } finally {
                      setBalanceSubmitting(false);
                    }
                  }}
                  style={{
                    padding: "8px 16px",
                    backgroundColor: balanceSubmitting ? "rgba(52,199,89,0.5)" : "#34c759",
                    color: "#ffffff",
                    borderRadius: 980,
                    border: "none",
                    fontSize: 14,
                    fontWeight: 500,
                    whiteSpace: "nowrap",
                    cursor: balanceSubmitting ? "not-allowed" : "pointer",
                    transition: "background-color 0.15s",
                  }}
                >
                  {balanceSubmitting ? "保存中..." : "保存余额"}
                </button>
              </div>
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.4)", marginTop: 6 }}>
                直接设置该用户的 Agent 对话余额，单位：元
              </div>
            </div>

            <div style={{ fontSize: 12, color: "rgba(255,255,255,0.4)", marginBottom: 16 }}>
              创建时间：{createdAt || "—"}
            </div>

            <button
              type="submit"
              disabled={submitting}
              className={submitting ? "" : "cursor-pointer"}
              style={{
                padding: "8px 20px",
                backgroundColor: submitting ? "rgba(10,132,255,0.5)" : "var(--apple-blue)",
                color: "#ffffff",
                borderRadius: 980,
                border: "none",
                fontSize: 17,
                fontWeight: 400,
                display: "inline-flex",
                alignItems: "center",
                gap: 8,
                cursor: submitting ? "not-allowed" : "pointer",
                transition: "background-color 0.15s",
              }}
            >
              {submitting && (
                <span
                  aria-hidden
                  style={{
                    width: 14,
                    height: 14,
                    borderRadius: "50%",
                    border: "2px solid rgba(255,255,255,0.4)",
                    borderTopColor: "#fff",
                    animation: "spin 0.8s linear infinite",
                    display: "inline-block",
                  }}
                />
              )}
              {submitting ? "保存中..." : "保存"}
            </button>
            <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
          </form>
        )}
      </div>
    </div>
  );
}
