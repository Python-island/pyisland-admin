/**
 * @file App.tsx
 * @description 管理后台前端路由入口。
 * @description 定义登录路由、鉴权路由与各业务页面映射关系。
 * @author 鸡哥
 */

import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { isLoggedIn } from "./api";
import Login from "./pages/Login";
import Layout from "./components/Layout";
import Overview from "./pages/Overview";
import VersionUpdate from "./pages/VersionUpdate";
import VersionCreate from "./pages/VersionCreate";
import VersionDelete from "./pages/VersionDelete";
import UserList from "./pages/UserList";
import AppUserList from "./pages/AppUserList";
import AppUserAdd from "./pages/AppUserAdd";
import AppUserEdit from "./pages/AppUserEdit";
import Profile from "./pages/Profile";
import ApiStatusPage from "./pages/ApiStatus";
import ApiStatusManage from "./pages/ApiStatusManage";
import ApiDebug from "./pages/ApiDebug";

/**
 * 鉴权路由包装器。
 * @param children - 需要受保护的页面节点。
 * @returns 已登录返回页面节点，否则重定向到登录页。
 */
function PrivateRoute({ children }: { children: React.ReactNode }) {
  return isLoggedIn() ? children : <Navigate to="/login" replace />;
}

/**
 * 应用根组件。
 * @returns 路由容器与页面树。
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          element={
            <PrivateRoute>
              <Layout />
            </PrivateRoute>
          }
        >
          <Route index element={<Overview />} />
          <Route path="/version/update" element={<VersionUpdate />} />
          <Route path="/version/create" element={<VersionCreate />} />
          <Route path="/version/delete" element={<VersionDelete />} />
          <Route path="/admin-users/list" element={<UserList />} />
          <Route path="/app-users/list" element={<AppUserList />} />
          <Route path="/app-users/add" element={<AppUserAdd />} />
          <Route path="/app-users/edit" element={<AppUserEdit />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/api-status" element={<ApiStatusPage />} />
          <Route path="/api-message" element={<ApiStatusManage />} />
          <Route path="/api-debug" element={<ApiDebug />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
