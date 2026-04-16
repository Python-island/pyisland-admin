import { useState, useRef } from "react";
import { appUsers, uploadUserAvatar, sanitizeUrl, type Gender } from "../api";
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

export default function AppUserAdd() {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPwd, setConfirmPwd] = useState("");
  const [gender, setGender] = useState<Gender>("undisclosed");
  const [genderCustom, setGenderCustom] = useState("");
  const [birthday, setBirthday] = useState("");
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
      const extras = {
        gender,
        genderCustom: gender === "custom" ? genderCustom.trim() || null : null,
        birthday: birthday || null,
      };
      const res = await appUsers.add(username, email, password, extras);
      if (res.code === 200) {
        if (avatarFile) {
          const upRes = await uploadUserAvatar(avatarFile);
          if (upRes.code === 200 && upRes.data) {
            await appUsers.updateProfile(username, null, upRes.data);
          } else {
            showMsg(upRes.message || "头像上传失败", "err");
            return;
          }
        }
        showMsg("添加用户成功");
        setUsername("");
        setEmail("");
        setPassword("");
        setConfirmPwd("");
        setGender("undisclosed");
        setGenderCustom("");
        setBirthday("");
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
        添加用户
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
        创建新的普通用户账号
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
              {!avatarPreview && "+"}
            </div>
            <div>
              <label style={labelStyle}>头像</label>
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.4)" }}>点击圆圈上传，使用 R2 存储，支持 JPG/PNG，最大 5MB</div>
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
              placeholder="新用户用户名"
              required
              style={inputStyle}
            />
          </div>

          <div style={{ marginBottom: 16 }}>
            <label style={labelStyle}>邮箱</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="用户邮箱"
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

          <div style={{ marginBottom: 16 }}>
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

          <div style={{ marginBottom: 24 }}>
            <label style={labelStyle}>生日</label>
            <input
              type="date"
              value={birthday}
              onChange={(e) => setBirthday(e.target.value)}
              max={new Date().toISOString().slice(0, 10)}
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
