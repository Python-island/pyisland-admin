import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { isLoggedIn } from "./api";
import Login from "./pages/Login";
import Layout from "./components/Layout";
import Overview from "./pages/Overview";
import VersionUpdate from "./pages/VersionUpdate";
import VersionCreate from "./pages/VersionCreate";
import VersionDelete from "./pages/VersionDelete";
import UserList from "./pages/UserList";
import UserAdd from "./pages/UserAdd";
import Profile from "./pages/Profile";
import ApiStatusPage from "./pages/ApiStatus";
import ApiStatusManage from "./pages/ApiStatusManage";
import ApiDebug from "./pages/ApiDebug";

function PrivateRoute({ children }: { children: React.ReactNode }) {
  return isLoggedIn() ? children : <Navigate to="/login" replace />;
}

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
          <Route path="/users/list" element={<UserList />} />
          <Route path="/users/add" element={<UserAdd />} />
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
