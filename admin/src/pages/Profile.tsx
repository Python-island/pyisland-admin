import { useState, useEffect, useRef } from "react";
import { users, getUsername, uploadAvatar } from "../api";
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

const labelStyle: React.CSSProperties = {
  display: "block",
  marginBottom: 8,
  fontSize: 14,
  fontWeight: 600,
  lineHeight: 1.29,
  letterSpacing: "-0.224px",
  color: "rgba(255,255,255,0.64)",
};

export default function Profile() {
  const username = getUsername();
  const [avatar, setAvatar] = useState<string | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPwd, setConfirmPwd] = useState("");
  const [createdAt, setCreatedAt] = useState("");
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");
  const [loading, setLoading] = useState(true);
  const fileRef = useRef<HTMLInputElement>(null);

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
  };

  useEffect(() => {
    (async () => {
      try {
        const res = await users.getProfile(username);
        if (res.code === 200 && res.data) {
          setAvatar(res.data.avatar);
          setAvatarPreview(res.data.avatar);
          setCreatedAt(res.data.createdAt);
        }
      } catch {
        /* ignore */
      } finally {
        setLoading(false);
      }
    })();
  }, [username]);

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
    if (newPassword && newPassword !== confirmPwd) {
      showMsg("两次密码输入不一致", "err");
      return;
    }
    try {
      let avatarUrl = avatar;
      if (avatarFile) {
        const upRes = await uploadAvatar(avatarFile);
        if (upRes.code === 200 && upRes.data) {
          avatarUrl = upRes.data;
        } else {
          showMsg(upRes.message || "头像上传失败", "err");
          return;
        }
      }
      const res = await users.updateProfile(
        username,
        newPassword || null,
        avatarUrl
      );
      if (res.code === 200) {
        showMsg("更新成功");
        setAvatar(avatarUrl);
        setAvatarPreview(avatarUrl);
        setAvatarFile(null);
        setNewPassword("");
        setConfirmPwd("");
        window.dispatchEvent(new CustomEvent("avatar-updated", { detail: avatarUrl }));
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("更新失败", "err");
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center" style={{ minHeight: "60vh" }}>
        <span style={{ color: "rgba(255,255,255,0.48)", fontSize: 17 }}>加载中...</span>
      </div>
    );
  }

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
        个人信息
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
        修改头像和密码
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
        <form onSubmit={handleSubmit}>
          {/* Avatar + Info */}
          <div style={{ display: "flex", alignItems: "center", gap: 24, marginBottom: 32 }}>
            <div
              onClick={() => fileRef.current?.click()}
              className="cursor-pointer"
              style={{
                width: 88,
                height: 88,
                borderRadius: "50%",
                backgroundColor: "var(--apple-surface-2)",
                backgroundImage: avatarPreview ? `url(${avatarPreview})` : "none",
                backgroundSize: "cover",
                backgroundPosition: "center",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 28,
                color: "rgba(255,255,255,0.32)",
                flexShrink: 0,
              }}
            >
              {!avatarPreview && username.charAt(0).toUpperCase()}
            </div>
            <div>
              <div style={{ fontSize: 21, fontWeight: 600, color: "#ffffff", marginBottom: 4 }}>
                {username}
              </div>
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.4)" }}>
                创建于 {createdAt}
              </div>
              <div
                onClick={() => fileRef.current?.click()}
                className="cursor-pointer"
                style={{ fontSize: 14, color: "var(--apple-link-dark)", marginTop: 8 }}
              >
                更换头像
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

          {/* Password */}
          <div style={{ marginBottom: 16 }}>
            <label style={labelStyle}>新密码</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="留空则不修改密码"
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
              style={inputStyle}
            />
          </div>

          <button
            type="submit"
            className="cursor-pointer"
            style={{
              padding: "8px 20px",
              backgroundColor: "var(--apple-blue)",
              color: "#ffffff",
              borderRadius: 980,
              border: "none",
              fontSize: 17,
              fontWeight: 400,
            }}
          >
            保存
          </button>
        </form>
      </div>
    </div>
  );
}
