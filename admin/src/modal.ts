export function showKickedModal(onConfirm: () => void) {
  if (document.getElementById("kicked-modal-overlay")) return;

  const overlay = document.createElement("div");
  overlay.id = "kicked-modal-overlay";
  Object.assign(overlay.style, {
    position: "fixed",
    inset: "0",
    zIndex: "9999",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(0,0,0,0.55)",
    backdropFilter: "blur(8px)",
    WebkitBackdropFilter: "blur(8px)",
  });

  const card = document.createElement("div");
  Object.assign(card.style, {
    backgroundColor: "#1c1c1e",
    borderRadius: "14px",
    padding: "32px 28px 24px",
    maxWidth: "340px",
    width: "90%",
    textAlign: "center",
    boxShadow: "0 8px 32px rgba(0,0,0,0.4)",
  });

  const title = document.createElement("div");
  title.textContent = "\u8D26\u53F7\u5DF2\u5728\u5176\u4ED6\u8BBE\u5907\u767B\u5F55";
  Object.assign(title.style, {
    color: "#ffffff",
    fontSize: "17px",
    fontWeight: "600",
    lineHeight: "1.3",
    marginBottom: "8px",
  });

  const desc = document.createElement("div");
  desc.textContent = "\u5F53\u524D\u4F1A\u8BDD\u5DF2\u5931\u6548\uFF0C\u8BF7\u91CD\u65B0\u767B\u5F55";
  Object.assign(desc.style, {
    color: "rgba(255,255,255,0.5)",
    fontSize: "14px",
    lineHeight: "1.4",
    marginBottom: "24px",
  });

  const btn = document.createElement("button");
  btn.textContent = "\u91CD\u65B0\u767B\u5F55";
  Object.assign(btn.style, {
    width: "100%",
    padding: "12px",
    border: "none",
    borderRadius: "980px",
    backgroundColor: "#0a84ff",
    color: "#ffffff",
    fontSize: "17px",
    fontWeight: "400",
    cursor: "pointer",
  });
  btn.onmouseenter = () => (btn.style.opacity = "0.85");
  btn.onmouseleave = () => (btn.style.opacity = "1");
  btn.onclick = () => {
    overlay.remove();
    onConfirm();
  };

  card.appendChild(title);
  card.appendChild(desc);
  card.appendChild(btn);
  overlay.appendChild(card);
  document.body.appendChild(overlay);
}
