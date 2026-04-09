import { sidebar } from "vuepress-theme-hope";

export default sidebar({
  "/": [
    "",
    {
      text: "API 文档",
      icon: "book",
      prefix: "api/",
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
  ],
});
