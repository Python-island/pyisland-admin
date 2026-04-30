
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
    ('auth.admin.login',            1, '', 'POST /auth/admin/login - 管理员登录'),
    ('auth.admin.register',         1, '', 'POST /auth/admin/register - 管理员注册'),
    ('auth.user.login',             1, '', 'POST /auth/user/login - 用户登录'),
    ('auth.user.register',          1, '', 'POST /auth/user/register - 用户注册'),
    ('auth.user.email-code.config', 1, '', 'GET /auth/user/email-code/captcha-config - 获取滑块验证码配置'),
    ('auth.user.email-code.challenge', 1, '', 'POST /auth/user/email-code/captcha-challenge - 创建滑块验证码挑战'),
    ('auth.user.email-code.send',   1, '', 'POST /auth/user/email-code/send - 发送邮箱验证码'),
    ('auth.user.email-code.verify', 1, '', 'POST /auth/user/email-code/verify - 校验邮箱验证码'),

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
    --  管理员管理  /v1/admin-users
    -- --------------------------------------------------------
    ('admin-users.list',            1, '', 'GET /v1/admin-users - 管理员列表'),
    ('admin-users.count',           1, '', 'GET /v1/admin-users/count - 管理员数量'),
    ('admin-users.add',             1, '', 'POST /v1/admin-users - 添加管理员'),
    ('admin-users.delete',          1, '', 'DELETE /v1/admin-users?username= - 删除管理员'),
    ('admin-users.profile.get',     1, '', 'GET /v1/admin-users/profile - 获取管理员资料'),
    ('admin-users.profile.update',  1, '', 'PUT /v1/admin-users/profile - 更新管理员资料'),

    -- --------------------------------------------------------
    --  用户管理（管理员侧）  /v1/app-users
    -- --------------------------------------------------------
    ('app-users.list',              1, '', 'GET /v1/app-users - 用户列表'),
    ('app-users.count',             1, '', 'GET /v1/app-users/count - 用户数量'),
    ('app-users.add',               1, '', 'POST /v1/app-users - 添加用户'),
    ('app-users.delete',            1, '', 'DELETE /v1/app-users?username= - 删除用户'),
    ('app-users.profile.get',       1, '', 'GET /v1/app-users/profile - 获取用户资料'),
    ('app-users.profile.update',    1, '', 'PUT /v1/app-users/profile - 更新用户资料'),
    ('app-users.balance.update',    1, '', 'PUT /v1/app-users/balance - 管理员设置用户余额'),

    -- --------------------------------------------------------
    --  统一用户管理（合表）  /v1/admin/users
    -- --------------------------------------------------------
    ('admin.users.list',            1, '', 'GET /v1/admin/users - 统一用户列表（可按 role 过滤）'),
    ('admin.users.role.update',     1, '', 'PUT /v1/admin/users/role - 更新用户角色（设为管理员/降为普通用户）'),
    ('admin.users.enabled.update', 1, '', 'PUT /v1/admin/users/enabled - 启用或禁用账号'),
    ('admin.users.ban.update',      1, '', 'PUT /v1/admin/users/ban - 封禁或解封账号'),

    -- --------------------------------------------------------
    --  用户自助（用户侧）  /v1/user
    -- --------------------------------------------------------
    ('user.profile.get',            1, '', 'GET /v1/user/profile - 获取自己的资料'),
    ('user.profile.update',         1, '', 'PUT /v1/user/profile - 修改自己的资料（头像/性别/生日）'),
    ('user.profile.password.update', 1, '', 'POST /v1/user/profile/password - 修改密码'),
    ('user.profile.totp-seed.get',  1, '', 'GET /v1/user/profile/password/totp-seed - 获取 TOTP Seed'),
    ('user.profile.totp-seed.rotate', 1, '', 'POST /v1/user/profile/password/totp-seed/rotate - 轮换 TOTP Seed'),
    ('user.logout',                 1, '', 'POST /v1/user/logout - 退出登录'),
    ('user.unregister',             1, '', 'DELETE /v1/user/account - 注销账号（需密码二次确认）'),
    ('user.update-source.get',      1, '', 'GET /v1/user/update-source - 获取更新源 URL'),

    -- --------------------------------------------------------
    --  文件上传  /v1/upload
    -- --------------------------------------------------------
    ('upload.admin-avatar',         1, '', 'POST /v1/upload/admin-avatar - 上传管理员头像（OSS）'),
    ('upload.user-avatar',          1, '', 'POST /v1/upload/user-avatar - 上传用户头像（R2）'),

    -- --------------------------------------------------------
    --  壁纸市场（用户侧）  /v1/user/wallpapers
    -- --------------------------------------------------------
    ('user.wallpapers.upload',      1, '', 'POST /v1/user/wallpapers/upload - 上传壁纸'),
    ('user.wallpapers.list',        1, '', 'GET /v1/user/wallpapers/list - 获取已发布壁纸列表'),
    ('user.wallpapers.mine',        1, '', 'GET /v1/user/wallpapers/mine - 获取我的壁纸列表'),
    ('user.wallpapers.detail',      1, '', 'GET /v1/user/wallpapers/detail - 获取壁纸详情'),
    ('user.wallpapers.metadata',    1, '', 'PUT /v1/user/wallpapers/metadata - 更新我的壁纸元数据'),
    ('user.wallpapers.replace-source', 1, '', 'PUT /v1/user/wallpapers/replace-source - 替换我的壁纸源文件'),
    ('user.wallpapers.delete',      1, '', 'DELETE /v1/user/wallpapers/delete - 删除我的壁纸'),
    ('user.wallpapers.apply',       1, '', 'POST /v1/user/wallpapers/apply - 应用壁纸并计数'),
    ('user.wallpapers.rate',        1, '', 'POST /v1/user/wallpapers/rate - 壁纸评分'),
    ('user.wallpapers.report',      1, '', 'POST /v1/user/wallpapers/report - 提交壁纸举报'),

    -- --------------------------------------------------------
    --  壁纸市场（管理员侧）  /v1/admin/wallpapers
    -- --------------------------------------------------------
    ('admin.wallpapers.list',       1, '', 'GET /v1/admin/wallpapers/list - 管理端壁纸列表'),
    ('admin.wallpapers.metadata',   1, '', 'PUT /v1/admin/wallpapers/metadata - 管理端更新壁纸元数据'),
    ('admin.wallpapers.review',     1, '', 'PUT /v1/admin/wallpapers/review - 审核壁纸'),
    ('admin.wallpapers.reports',    1, '', 'GET /v1/admin/wallpapers/reports - 获取举报列表'),
    ('admin.wallpapers.reports.resolve', 1, '', 'PUT /v1/admin/wallpapers/reports/resolve - 处理举报'),
    ('admin.wallpapers.ratings',    1, '', 'GET /v1/admin/wallpapers/ratings - 获取评分列表'),
    ('admin.wallpapers.ratings.delete', 1, '', 'DELETE /v1/admin/wallpapers/ratings - 删除评分记录'),
    ('admin.wallpapers.delete',     1, '', 'DELETE /v1/admin/wallpapers/delete - 管理端删除壁纸'),

    -- --------------------------------------------------------
    --  标签管理（用户/管理员）
    -- --------------------------------------------------------
    ('user.tags.search',            1, '', 'GET /v1/user/tags/search - 搜索标签自动补全'),
    ('admin.tags.list',             1, '', 'GET /v1/admin/tags/list - 管理端标签列表'),
    ('admin.tags.update',           1, '', 'PUT /v1/admin/tags/update - 管理端更新标签名称'),
    ('admin.tags.enable',           1, '', 'PUT /v1/admin/tags/enable - 管理端启用/禁用标签'),
    ('admin.tags.delete',           1, '', 'DELETE /v1/admin/tags/delete - 管理端删除标签'),

    -- --------------------------------------------------------
    --  支付管理（管理员侧）  /v1/admin/payment
    -- --------------------------------------------------------
    ('admin.payment.orders.list',       1, '', 'GET /v1/admin/payment/orders - 管理端支付订单列表'),
    ('admin.payment.orders.refresh',    1, '', 'PUT /v1/admin/payment/orders/refresh - 管理端刷新订单状态'),
    ('admin.payment.orders.close',      1, '', 'PUT /v1/admin/payment/orders/close - 管理端关闭订单'),
    ('admin.payment.orders.test',       1, '', 'POST /v1/admin/payment/orders/test - 管理端创建测试支付单'),
    ('admin.payment.notify-dlq.list',   1, '', 'GET /v1/admin/payment/notify-dlq - 管理端通知死信列表'),
    ('admin.payment.config.get',        1, '', 'GET /v1/admin/payment/config - 管理端获取支付与定价权益配置'),
    ('admin.payment.config.update',     1, '', 'PUT /v1/admin/payment/config - 管理端更新支付与定价权益配置'),

    -- --------------------------------------------------------
    --  支付能力（用户侧）  /v1/user/payment
    -- --------------------------------------------------------
    ('user.payment.channels.get',            1, '', 'GET /v1/user/payment/channels - 获取可用支付渠道'),
    ('user.payment.orders.pro-month.create', 1, '', 'POST /v1/user/payment/orders/pro-month - 创建 Pro 月付订单'),
    ('user.payment.orders.agent-recharge.create', 1, '', 'POST /v1/user/payment/orders/agent-recharge - 创建余额充值订单'),
    ('user.payment.agent.balance.get',       1, '', 'GET /v1/user/payment/agent/balance - 查询 Agent 对话余额'),
    ('user.payment.pricing.pro-month.get',   1, '', 'GET /v1/user/payment/pricing/pro-month - 获取 Pro 月付定价与权益'),
    ('user.payment.orders.detail.get',       1, '', 'GET /v1/user/payment/orders/{outTradeNo} - 获取订单详情'),
    ('user.payment.orders.list',             1, '', 'GET /v1/user/payment/orders - 获取用户订单列表'),
    ('user.payment.orders.close',            1, '', 'POST /v1/user/payment/orders/{outTradeNo}/close - 关闭订单'),

    -- --------------------------------------------------------
    --  支付回调（三方通知）  /v1/payment
    -- --------------------------------------------------------
    ('payment.wechat.notify',                1, '', 'POST /v1/payment/wechat/notify - 微信支付回调通知'),
    ('payment.alipay.notify',                1, '', 'POST /v1/payment/alipay/notify - 支付宝支付回调通知'),

    -- --------------------------------------------------------
    --  接口状态管理  /v1/service-status
    -- --------------------------------------------------------
    ('service-status.get',          1, '', 'GET /v1/service-status - 获取单个接口状态'),
    ('service-status.list',         1, '', 'GET /v1/service-status/list - 获取所有接口状态'),
    ('service-status.update',       1, '', 'PUT /v1/service-status - 更新接口状态'),

    -- --------------------------------------------------------
    --  Agent 管理（管理员侧）  /v1/admin/agent
    -- --------------------------------------------------------
    ('admin.agent.model-pricing.list',       1, '', 'GET /v1/admin/agent/model-pricing - 查询全部模型定价'),
    ('admin.agent.model-pricing.upsert',     1, '', 'PUT /v1/admin/agent/model-pricing - 新增或更新模型定价'),
    ('admin.agent.model-pricing.delete',     1, '', 'DELETE /v1/admin/agent/model-pricing - 删除模型定价'),
    ('admin.agent.service-enabled.get',      1, '', 'GET /v1/admin/agent/service-enabled - 查询 Agent 服务开关状态'),
    ('admin.agent.service-enabled.update',   1, '', 'PUT /v1/admin/agent/service-enabled - 设置 Agent 服务开关'),
    ('admin.agent.billing-dlq.list',         1, '', 'GET /v1/admin/agent/billing-dlq - 查询计费 DLQ 异常记录'),
    ('admin.agent.billing-dlq.resolve',      1, '', 'PUT /v1/admin/agent/billing-dlq/{id}/resolve - 处理 DLQ 记录'),
    ('admin.agent.billing-dlq.pending-count', 1, '', 'GET /v1/admin/agent/billing-dlq/pending-count - 查询待处理 DLQ 数量'),

    -- --------------------------------------------------------
    --  Agent 对话（用户侧）  /v1/user/ai
    -- --------------------------------------------------------
    ('user.ai.agent.stream',                 1, '', 'POST /v1/user/ai/agent/stream - Agent 流式会话'),
    ('user.ai.agent.web-access.resolve',     1, '', 'POST /v1/user/ai/agent/web-access/resolve - Agent 网页访问授权确认'),
    ('user.ai.agent.local-tool.resolve',     1, '', 'POST /v1/user/ai/agent/local-tool/resolve - Agent 本地工具授权确认'),

    -- --------------------------------------------------------
    --  公告管理  /v1/announcement
    -- --------------------------------------------------------
    ('admin.announcement.get',      1, '', 'GET /v1/admin/announcement - 获取公告配置'),
    ('admin.announcement.update',   1, '', 'PUT /v1/admin/announcement - 更新公告配置'),
    ('announcement.current.get',    1, '', 'GET /v1/announcement/current - 获取当前公告'),

    -- --------------------------------------------------------
    --  问题反馈  /v1/user/feedback & /v1/admin/feedback
    -- --------------------------------------------------------
    ('user.feedback.submit',        1, '', 'POST /v1/user/feedback/submit - 提交问题反馈'),
    ('user.feedback.mine',          1, '', 'GET /v1/user/feedback/mine - 获取我的反馈列表'),
    ('admin.feedback.list',         1, '', 'GET /v1/admin/feedback - 管理端反馈列表'),
    ('admin.feedback.resolve',      1, '', 'PUT /v1/admin/feedback/resolve - 处理反馈'),

    -- --------------------------------------------------------
    --  天气服务  /v1/user/weather & /v1/admin/weather
    -- --------------------------------------------------------
    ('user.weather.daily-3d',       1, '', 'GET /v1/user/weather/daily-3d - 获取 3 天天气预报'),
    ('user.weather.alerts',         1, '', 'GET /v1/user/weather/alerts - 获取当前天气预警'),
    ('admin.weather.quota',         1, '', 'GET /v1/admin/weather/quota - 查询天气 API 月度配额'),

    -- --------------------------------------------------------
    --  邮件 DLQ（管理员侧）  /v1/admin/email
    -- --------------------------------------------------------
    ('admin.email.notify-dlq',      1, '', 'GET /v1/admin/email/notify-dlq - 管理端邮件死信列表'),

    -- --------------------------------------------------------
    --  Agent 服务总开关（非 API 端点，用于管理员开关 Agent 服务）
    -- --------------------------------------------------------
    ('agent-service',               1, '', 'Agent 服务总开关 - 关闭后所有用户 Agent 对话请求将被拒绝');
