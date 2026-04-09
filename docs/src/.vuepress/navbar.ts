import { navbar } from "vuepress-theme-hope";

export default navbar([
  "/",
  {
    text: "通用",
    children: [
      { text: "接口总览", link: "/api/overview" },
      { text: "前端 SDK 示例", link: "/api/sdk-example" },
    ],
  },
  {
    text: "认证接口",
    children: [
      { text: "管理员登录", link: "/api/endpoints/auth-login" },
    ],
  },
  {
    text: "用户管理接口",
    children: [
      { text: "查询管理员列表", link: "/api/endpoints/users-list" },
      { text: "查询管理员数量", link: "/api/endpoints/users-count" },
      { text: "新增管理员", link: "/api/endpoints/users-create" },
      { text: "删除管理员", link: "/api/endpoints/users-delete" },
      { text: "查询管理员资料", link: "/api/endpoints/users-profile-get" },
      { text: "更新管理员资料", link: "/api/endpoints/users-profile-update" },
    ],
  },
  {
    text: "版本管理接口",
    children: [
      { text: "查询全部版本", link: "/api/endpoints/version-list" },
      { text: "查询单个版本", link: "/api/endpoints/version-get" },
      { text: "创建应用版本", link: "/api/endpoints/version-create" },
      { text: "更新应用版本", link: "/api/endpoints/version-update" },
      { text: "删除应用版本", link: "/api/endpoints/version-delete" },
    ],
  },
  {
    text: "服务状态接口",
    children: [
      { text: "查询单个接口状态", link: "/api/endpoints/service-status-get" },
      { text: "查询全部接口状态", link: "/api/endpoints/service-status-list" },
      { text: "更新接口状态", link: "/api/endpoints/service-status-update" },
    ],
  },
  {
    text: "上传接口",
    children: [
      { text: "上传头像", link: "/api/endpoints/upload-avatar" },
    ],
  },
]);
