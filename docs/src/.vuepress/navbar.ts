import { navbar } from "vuepress-theme-hope";

export default navbar([
  "/",
  {
    text: "API 文档",
    icon: "book",
    prefix: "/api/",
    children: [
      "overview",
      "sdk-example",
      "auth",
      "users",
      "version",
      "service-status",
      "upload",
    ],
  },
]);
