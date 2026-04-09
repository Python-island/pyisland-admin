import { sidebar } from "vuepress-theme-hope";

export default sidebar({
  "/": [
    {
      text: "API 接口详细文档",
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
