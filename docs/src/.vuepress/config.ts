import { defineUserConfig } from "vuepress";

import theme from "./theme.js";

export default defineUserConfig({
  base: "/",

  lang: "zh-CN",
  title: "PyIsland API",
  description: "pyisland-api文档站",

  head: [["link", { rel: "icon", href: "/favicon.ico" }]],

  theme,

  // 和 PWA 一起启用
  // shouldPrefetch: false,
});
