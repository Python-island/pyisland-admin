import { useState, useRef } from "react";
import { users, uploadAvatar } from "../api";
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

export default function UserAdd() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPwd, setConfirmPwd] = useState("");
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [msg, setMsg] = useState("");
  const [msgType, setMsgType] = useState<"ok" | "err">("ok");

  const showMsg = (text: string, type: "ok" | "err" = "ok") => {
    setMsg(text);
    setMsgType(type);
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
    if (password !== confirmPwd) {
      showMsg("两次密码输入不一致", "err");
      return;
    }
    try {
      const res = await users.add(username, password);
      if (res.code === 200) {
        if (avatarFile) {
          const upRes = await uploadAvatar(avatarFile);
          if (upRes.code === 200 && upRes.data) {
            await users.updateProfile(username, null, upRes.data);
          }
        }
        showMsg("添加管理员成功");
        setUsername("");
        setPassword("");
        setConfirmPwd("");
        setAvatarPreview(null);
        setAvatarFile(null);
      } else {
        showMsg(res.message, "err");
      }
    } catch {
      showMsg("添加失败", "err");
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
        添加管理员
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
        注册新的管理员账号
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
          {/* Avatar */}
          <div style={{ marginBottom: 24, display: "flex", alignItems: "center", gap: 20 }}>
            <div
              onClick={() => fileRef.current?.click()}
              className="cursor-pointer"
              style={{
                width: 72,
                height: 72,
                borderRadius: "50%",
                backgroundColor: "var(--apple-surface-2)",
                backgroundImage: avatarPreview ? `url(${avatarPreview})` : "none",
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
              {!avatarPreview && "+"}
            </div>
            <div>
              <label style={labelStyle}>头像</label>
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.4)" }}>点击圆圈上传，支持 JPG/PNG，最大 5MB</div>
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
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="新管理员用户名"
              required
              style={inputStyle}
            />
          </div>
          <div style={{ marginBottom: 16 }}>
            <label style={labelStyle}>密码</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="设置密码"
              required
              style={inputStyle}
            />
          </div>
          <div style={{ marginBottom: 24 }}>
            <label style={labelStyle}>确认密码</label>
            <input
              type="password"
              value={confirmPwd}
              onChange={(e) => setConfirmPwd(e.target.value)}
              placeholder="再次输入密码"
              required
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
            添加
          </button>
        </form>
      </div>
    </div>
  );
}
