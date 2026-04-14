
CREATE TABLE IF NOT EXISTS service_status (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_name   VARCHAR(100)  NOT NULL UNIQUE COMMENT 'API 接口标识',
    status     TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    message    VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '禁用时的提示信息',
    remark     VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '接口备注信息',
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 接口状态管理';

-- 如果表已存在，手动添加 remark 字段：
-- ALTER TABLE service_status ADD COLUMN remark VARCHAR(255) NOT NULL DEFAULT '' COMMENT '接口备注信息' AFTER message;

INSERT IGNORE INTO service_status (api_name, status, message, remark) VALUES

    -- --------------------------------------------------------
    --  认证接口  /auth
    -- --------------------------------------------------------
    ('auth.login',                  1, '', 'POST /auth/login - 用户登录'),

    -- --------------------------------------------------------
    --  版本管理  /v1/version
    -- --------------------------------------------------------
    ('version.list',                1, '', 'GET /v1/version/list - 获取所有版本'),
    ('version.get',                 1, '', 'GET /v1/version?appName= - 获取指定版本'),
    ('version.update-count',        1, '', 'POST /v1/version/update-count - 统计版本更新次数'),
    ('version.create',              1, '', 'POST /v1/version - 创建版本'),
    ('version.update',              1, '', 'PUT /v1/version - 更新版本'),
    ('version.delete',              1, '', 'DELETE /v1/version?appName= - 删除版本'),

    -- --------------------------------------------------------
    --  人员管理  /v1/users
    -- --------------------------------------------------------
    ('users.list',                  1, '', 'GET /v1/users - 管理员列表'),
    ('users.count',                 1, '', 'GET /v1/users/count - 管理员数量'),
    ('users.add',                   1, '', 'POST /v1/users - 添加管理员'),
    ('users.delete',                1, '', 'DELETE /v1/users?username= - 删除管理员'),
    ('users.profile.get',           1, '', 'GET /v1/users/profile - 获取个人资料'),
    ('users.profile.update',        1, '', 'PUT /v1/users/profile - 更新个人资料'),

    -- --------------------------------------------------------
    --  文件上传  /v1/upload
    -- --------------------------------------------------------
    ('upload.avatar',               1, '', 'POST /v1/upload/avatar - 上传头像'),

    -- --------------------------------------------------------
    --  接口状态管理  /v1/service-status
    -- --------------------------------------------------------
    ('service-status.get',          1, '', 'GET /v1/service-status - 获取单个接口状态'),
    ('service-status.list',         1, '', 'GET /v1/service-status/list - 获取所有接口状态'),
    ('service-status.update',       1, '', 'PUT /v1/service-status - 更新接口状态');
