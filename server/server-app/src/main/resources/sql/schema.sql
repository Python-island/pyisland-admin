CREATE DATABASE IF NOT EXISTS pyisland_admin_database DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pyisland_admin_database;

CREATE TABLE IF NOT EXISTS app_version (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_name    VARCHAR(100) NOT NULL UNIQUE,
    version     VARCHAR(50)  NOT NULL,
    description VARCHAR(500),
    update_count BIGINT      NOT NULL DEFAULT 0,
    updated_at  DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @app_version_update_count_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_version'
      AND COLUMN_NAME = 'update_count'
);
SET @app_version_update_count_sql := IF(
    @app_version_update_count_exists = 0,
    'ALTER TABLE app_version ADD COLUMN update_count BIGINT NOT NULL DEFAULT 0 AFTER description',
    'SELECT 1'
);
PREPARE app_version_update_count_stmt FROM @app_version_update_count_sql;
EXECUTE app_version_update_count_stmt;
DEALLOCATE PREPARE app_version_update_count_stmt;

CREATE TABLE IF NOT EXISTS admin_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    avatar      LONGTEXT,
    created_at  DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(100) NOT NULL UNIQUE,
    email        VARCHAR(150) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    avatar       LONGTEXT,
    gender       VARCHAR(20) NOT NULL DEFAULT 'undisclosed',
    gender_custom VARCHAR(64),
    birthday     DATE,
    session_token VARCHAR(500),
    created_at   DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @admin_user_avatar_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'admin_user'
      AND COLUMN_NAME = 'avatar'
);
SET @admin_user_avatar_sql := IF(
    @admin_user_avatar_exists = 0,
    'ALTER TABLE admin_user ADD COLUMN avatar LONGTEXT AFTER password',
    'SELECT 1'
);
PREPARE admin_user_avatar_stmt FROM @admin_user_avatar_sql;
EXECUTE admin_user_avatar_stmt;
DEALLOCATE PREPARE admin_user_avatar_stmt;
ALTER TABLE admin_user MODIFY COLUMN avatar LONGTEXT;

SET @admin_user_session_token_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'admin_user'
      AND COLUMN_NAME = 'session_token'
);
SET @admin_user_session_token_sql := IF(
    @admin_user_session_token_exists = 0,
    'ALTER TABLE admin_user ADD COLUMN session_token VARCHAR(500) AFTER avatar',
    'SELECT 1'
);
PREPARE admin_user_session_token_stmt FROM @admin_user_session_token_sql;
EXECUTE admin_user_session_token_stmt;
DEALLOCATE PREPARE admin_user_session_token_stmt;

SET @app_user_avatar_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND COLUMN_NAME = 'avatar'
);
SET @app_user_avatar_sql := IF(
    @app_user_avatar_exists = 0,
    'ALTER TABLE app_user ADD COLUMN avatar LONGTEXT AFTER password',
    'SELECT 1'
);
PREPARE app_user_avatar_stmt FROM @app_user_avatar_sql;
EXECUTE app_user_avatar_stmt;
DEALLOCATE PREPARE app_user_avatar_stmt;
ALTER TABLE app_user MODIFY COLUMN avatar LONGTEXT;

SET @app_user_email_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND COLUMN_NAME = 'email'
);
SET @app_user_email_sql := IF(
    @app_user_email_exists = 0,
    'ALTER TABLE app_user ADD COLUMN email VARCHAR(150) NULL AFTER username',
    'SELECT 1'
);
PREPARE app_user_email_stmt FROM @app_user_email_sql;
EXECUTE app_user_email_stmt;
DEALLOCATE PREPARE app_user_email_stmt;

UPDATE app_user
SET email = CONCAT(username, '@placeholder.local')
WHERE email IS NULL OR email = '';

ALTER TABLE app_user MODIFY COLUMN email VARCHAR(150) NOT NULL;

SET @app_user_email_unique_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND INDEX_NAME = 'uk_app_user_email'
);
SET @app_user_email_unique_sql := IF(
    @app_user_email_unique_exists = 0,
    'ALTER TABLE app_user ADD CONSTRAINT uk_app_user_email UNIQUE (email)',
    'SELECT 1'
);
PREPARE app_user_email_unique_stmt FROM @app_user_email_unique_sql;
EXECUTE app_user_email_unique_stmt;
DEALLOCATE PREPARE app_user_email_unique_stmt;

SET @app_user_session_token_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND COLUMN_NAME = 'session_token'
);
SET @app_user_session_token_sql := IF(
    @app_user_session_token_exists = 0,
    'ALTER TABLE app_user ADD COLUMN session_token VARCHAR(500) AFTER avatar',
    'SELECT 1'
);
PREPARE app_user_session_token_stmt FROM @app_user_session_token_sql;
EXECUTE app_user_session_token_stmt;
DEALLOCATE PREPARE app_user_session_token_stmt;

SET @app_user_gender_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND COLUMN_NAME = 'gender'
);
SET @app_user_gender_sql := IF(
    @app_user_gender_exists = 0,
    'ALTER TABLE app_user ADD COLUMN gender VARCHAR(20) NOT NULL DEFAULT ''undisclosed'' AFTER avatar',
    'SELECT 1'
);
PREPARE app_user_gender_stmt FROM @app_user_gender_sql;
EXECUTE app_user_gender_stmt;
DEALLOCATE PREPARE app_user_gender_stmt;

SET @app_user_gender_custom_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND COLUMN_NAME = 'gender_custom'
);
SET @app_user_gender_custom_sql := IF(
    @app_user_gender_custom_exists = 0,
    'ALTER TABLE app_user ADD COLUMN gender_custom VARCHAR(64) AFTER gender',
    'SELECT 1'
);
PREPARE app_user_gender_custom_stmt FROM @app_user_gender_custom_sql;
EXECUTE app_user_gender_custom_stmt;
DEALLOCATE PREPARE app_user_gender_custom_stmt;

SET @app_user_birthday_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
      AND COLUMN_NAME = 'birthday'
);
SET @app_user_birthday_sql := IF(
    @app_user_birthday_exists = 0,
    'ALTER TABLE app_user ADD COLUMN birthday DATE AFTER gender_custom',
    'SELECT 1'
);
PREPARE app_user_birthday_stmt FROM @app_user_birthday_sql;
EXECUTE app_user_birthday_stmt;
DEALLOCATE PREPARE app_user_birthday_stmt;

-- ========================================================================
-- 2026-04 统一用户表迁移：合并 admin_user + app_user 到 user_account
-- 旧表暂不删除，便于回滚；确认稳定运行 30 天后可手动 DROP。
-- ========================================================================

CREATE TABLE IF NOT EXISTS user_account (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'user',
    avatar        LONGTEXT,
    gender        VARCHAR(20)  NOT NULL DEFAULT 'undisclosed',
    gender_custom VARCHAR(64),
    birthday      DATE,
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    session_token VARCHAR(500),
    totp_secret_ciphertext LONGTEXT,
    totp_secret_updated_at DATETIME,
    created_at    DATETIME,
    UNIQUE KEY uk_user_account_username (username),
    UNIQUE KEY uk_user_account_email (email),
    KEY idx_user_account_role (role),
    KEY idx_user_account_totp_secret_updated_at (totp_secret_updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @user_account_totp_secret_ciphertext_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_account'
      AND COLUMN_NAME = 'totp_secret_ciphertext'
);
SET @user_account_totp_secret_ciphertext_sql := IF(
    @user_account_totp_secret_ciphertext_exists = 0,
    'ALTER TABLE user_account ADD COLUMN totp_secret_ciphertext LONGTEXT AFTER session_token',
    'SELECT 1'
);
PREPARE user_account_totp_secret_ciphertext_stmt FROM @user_account_totp_secret_ciphertext_sql;
EXECUTE user_account_totp_secret_ciphertext_stmt;
DEALLOCATE PREPARE user_account_totp_secret_ciphertext_stmt;

SET @user_account_totp_secret_updated_at_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_account'
      AND COLUMN_NAME = 'totp_secret_updated_at'
);
SET @user_account_totp_secret_updated_at_sql := IF(
    @user_account_totp_secret_updated_at_exists = 0,
    'ALTER TABLE user_account ADD COLUMN totp_secret_updated_at DATETIME AFTER totp_secret_ciphertext',
    'SELECT 1'
);
PREPARE user_account_totp_secret_updated_at_stmt FROM @user_account_totp_secret_updated_at_sql;
EXECUTE user_account_totp_secret_updated_at_stmt;
DEALLOCATE PREPARE user_account_totp_secret_updated_at_stmt;

SET @user_account_totp_secret_updated_at_idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_account'
      AND INDEX_NAME = 'idx_user_account_totp_secret_updated_at'
);
SET @user_account_totp_secret_updated_at_idx_sql := IF(
    @user_account_totp_secret_updated_at_idx_exists = 0,
    'ALTER TABLE user_account ADD INDEX idx_user_account_totp_secret_updated_at (totp_secret_updated_at)',
    'SELECT 1'
);
PREPARE user_account_totp_secret_updated_at_idx_stmt FROM @user_account_totp_secret_updated_at_idx_sql;
EXECUTE user_account_totp_secret_updated_at_idx_stmt;
DEALLOCATE PREPARE user_account_totp_secret_updated_at_idx_stmt;

-- 仅当 user_account 为空且源表存在时才执行数据拷贝。
SET @user_account_count := (SELECT COUNT(*) FROM user_account);

SET @legacy_admin_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'admin_user'
);

SET @legacy_app_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_user'
);

-- admin 先迁入：admin_user 没有 email，用 {username}@admin.local 作为占位符。
SET @migrate_admin_sql := IF(
    @user_account_count = 0 AND @legacy_admin_exists = 1,
    'INSERT IGNORE INTO user_account (username, email, password, role, avatar, gender, gender_custom, birthday, enabled, session_token, created_at)
     SELECT username, CONCAT(username, ''@admin.local''), password, ''admin'', avatar, ''undisclosed'', NULL, NULL, 1, session_token, created_at
     FROM admin_user',
    'SELECT 1'
);
PREPARE migrate_admin_stmt FROM @migrate_admin_sql;
EXECUTE migrate_admin_stmt;
DEALLOCATE PREPARE migrate_admin_stmt;

-- 再迁 app_user；与 admin_user 重名的账号通过 INSERT IGNORE 保留 admin 版本。
SET @migrate_app_sql := IF(
    @user_account_count = 0 AND @legacy_app_exists = 1,
    'INSERT IGNORE INTO user_account (username, email, password, role, avatar, gender, gender_custom, birthday, enabled, session_token, created_at)
     SELECT username, email, password, ''user'', avatar, COALESCE(gender, ''undisclosed''), gender_custom, birthday, 1, session_token, created_at
     FROM app_user',
    'SELECT 1'
);
PREPARE migrate_app_stmt FROM @migrate_app_sql;
EXECUTE migrate_app_stmt;
DEALLOCATE PREPARE migrate_app_stmt;

-- ========================================================================
-- 2026-04 壁纸市场：资源、审核、评分、统计与举报
-- ========================================================================

CREATE TABLE IF NOT EXISTS wallpaper_asset (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_username     VARCHAR(100) NOT NULL,
    title              VARCHAR(120) NOT NULL,
    description        TEXT,
    type               VARCHAR(20) NOT NULL DEFAULT 'image',
    status             VARCHAR(20) NOT NULL DEFAULT 'draft',
    original_url       LONGTEXT NOT NULL,
    thumb_320_url      LONGTEXT,
    thumb_720_url      LONGTEXT,
    thumb_1280_url     LONGTEXT,
    width              INT,
    height             INT,
    file_size          BIGINT,
    tags_text          TEXT,
    copyright_declared TINYINT(1) NOT NULL DEFAULT 0,
    copyright_info     TEXT,
    rating_avg         DECIMAL(4,2) NOT NULL DEFAULT 0,
    rating_count       BIGINT NOT NULL DEFAULT 0,
    download_count     BIGINT NOT NULL DEFAULT 0,
    apply_count        BIGINT NOT NULL DEFAULT 0,
    current_version    INT NOT NULL DEFAULT 1,
    deleted            TINYINT(1) NOT NULL DEFAULT 0,
    created_at         DATETIME NOT NULL,
    updated_at         DATETIME NOT NULL,
    published_at       DATETIME,
    KEY idx_wallpaper_asset_owner (owner_username),
    KEY idx_wallpaper_asset_status (status),
    KEY idx_wallpaper_asset_type (type),
    KEY idx_wallpaper_asset_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @wallpaper_asset_copyright_info_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wallpaper_asset'
      AND COLUMN_NAME = 'copyright_info'
);
SET @wallpaper_asset_copyright_info_sql := IF(
    @wallpaper_asset_copyright_info_exists = 0,
    'ALTER TABLE wallpaper_asset ADD COLUMN copyright_info TEXT AFTER copyright_declared',
    'SELECT 1'
);
PREPARE wallpaper_asset_copyright_info_stmt FROM @wallpaper_asset_copyright_info_sql;
EXECUTE wallpaper_asset_copyright_info_stmt;
DEALLOCATE PREPARE wallpaper_asset_copyright_info_stmt;

CREATE TABLE IF NOT EXISTS wallpaper_version (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallpaper_id   BIGINT NOT NULL,
    version_no     INT NOT NULL,
    original_url   LONGTEXT NOT NULL,
    thumb_320_url  LONGTEXT,
    thumb_720_url  LONGTEXT,
    thumb_1280_url LONGTEXT,
    file_size      BIGINT,
    width          INT,
    height         INT,
    checksum       VARCHAR(128),
    operator_name  VARCHAR(100) NOT NULL,
    reason         VARCHAR(300),
    created_at     DATETIME NOT NULL,
    UNIQUE KEY uk_wallpaper_version_no (wallpaper_id, version_no),
    KEY idx_wallpaper_version_wallpaper (wallpaper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallpaper_video_meta (
    wallpaper_id   BIGINT PRIMARY KEY,
    duration_ms    BIGINT,
    frame_rate     DECIMAL(6,3),
    created_at     DATETIME NOT NULL,
    updated_at     DATETIME NOT NULL,
    KEY idx_wallpaper_video_meta_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallpaper_review_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallpaper_id    BIGINT NOT NULL,
    action          VARCHAR(30) NOT NULL,
    reviewer_name   VARCHAR(100) NOT NULL,
    reviewer_reason VARCHAR(500),
    created_at      DATETIME NOT NULL,
    KEY idx_wallpaper_review_wallpaper (wallpaper_id),
    KEY idx_wallpaper_review_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallpaper_rating (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallpaper_id    BIGINT NOT NULL,
    username        VARCHAR(100) NOT NULL,
    score           TINYINT NOT NULL,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    UNIQUE KEY uk_wallpaper_rating_user (wallpaper_id, username),
    KEY idx_wallpaper_rating_wallpaper (wallpaper_id),
    KEY idx_wallpaper_rating_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallpaper_stat_daily (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallpaper_id   BIGINT NOT NULL,
    stat_date      DATE NOT NULL,
    download_count BIGINT NOT NULL DEFAULT 0,
    apply_count    BIGINT NOT NULL DEFAULT 0,
    view_count     BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_wallpaper_stat_daily (wallpaper_id, stat_date),
    KEY idx_wallpaper_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallpaper_apply_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallpaper_id   BIGINT NOT NULL,
    username       VARCHAR(100),
    ip_hash        VARCHAR(128),
    user_agent     VARCHAR(500),
    action         VARCHAR(20) NOT NULL,
    created_at     DATETIME NOT NULL,
    KEY idx_wallpaper_apply_wallpaper (wallpaper_id),
    KEY idx_wallpaper_apply_username (username),
    KEY idx_wallpaper_apply_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallpaper_report (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallpaper_id      BIGINT NOT NULL,
    reporter_username VARCHAR(100) NOT NULL,
    reason_type       VARCHAR(40) NOT NULL,
    reason_detail     VARCHAR(500),
    status            VARCHAR(20) NOT NULL DEFAULT 'pending',
    resolver_name     VARCHAR(100),
    resolution_note   VARCHAR(500),
    created_at        DATETIME NOT NULL,
    resolved_at       DATETIME,
    KEY idx_wallpaper_report_wallpaper (wallpaper_id),
    KEY idx_wallpaper_report_status (status),
    KEY idx_wallpaper_report_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallpaper_tag (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(60) NOT NULL,
    slug              VARCHAR(60) NOT NULL,
    creator_username  VARCHAR(100),
    enabled           TINYINT(1) NOT NULL DEFAULT 1,
    usage_count       INT NOT NULL DEFAULT 0,
    created_at        DATETIME NOT NULL,
    updated_at        DATETIME NOT NULL,
    UNIQUE KEY uk_wallpaper_tag_slug (slug),
    KEY idx_wallpaper_tag_enabled (enabled),
    KEY idx_wallpaper_tag_usage (usage_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallpaper_tag_ref (
    wallpaper_id BIGINT NOT NULL,
    tag_id       BIGINT NOT NULL,
    created_at   DATETIME NOT NULL,
    PRIMARY KEY (wallpaper_id, tag_id),
    KEY idx_wallpaper_tag_ref_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS issue_feedback (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(100) NOT NULL,
    feedback_type  VARCHAR(40) NOT NULL,
    title          VARCHAR(120) NOT NULL,
    content        TEXT NOT NULL,
    contact        VARCHAR(150),
    feedback_log_url VARCHAR(500),
    feedback_screenshot_url VARCHAR(500),
    client_version VARCHAR(50),
    status         VARCHAR(20) NOT NULL DEFAULT 'pending',
    admin_reply    VARCHAR(1000),
    created_at     DATETIME NOT NULL,
    updated_at     DATETIME NOT NULL,
    resolved_at    DATETIME,
    KEY idx_issue_feedback_user_created (username, created_at),
    KEY idx_issue_feedback_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @issue_feedback_log_url_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'issue_feedback'
      AND COLUMN_NAME = 'feedback_log_url'
);
SET @issue_feedback_log_url_sql := IF(
    @issue_feedback_log_url_exists = 0,
    'ALTER TABLE issue_feedback ADD COLUMN feedback_log_url VARCHAR(500) AFTER contact',
    'SELECT 1'
);
PREPARE issue_feedback_log_url_stmt FROM @issue_feedback_log_url_sql;
EXECUTE issue_feedback_log_url_stmt;
DEALLOCATE PREPARE issue_feedback_log_url_stmt;

SET @issue_feedback_screenshot_url_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'issue_feedback'
      AND COLUMN_NAME = 'feedback_screenshot_url'
);
SET @issue_feedback_screenshot_url_sql := IF(
    @issue_feedback_screenshot_url_exists = 0,
    'ALTER TABLE issue_feedback ADD COLUMN feedback_screenshot_url VARCHAR(500) AFTER feedback_log_url',
    'SELECT 1'
);
PREPARE issue_feedback_screenshot_url_stmt FROM @issue_feedback_screenshot_url_sql;
EXECUTE issue_feedback_screenshot_url_stmt;
DEALLOCATE PREPARE issue_feedback_screenshot_url_stmt;

CREATE TABLE IF NOT EXISTS announcement_config (
    id          BIGINT PRIMARY KEY,
    title       VARCHAR(200),
    content     TEXT,
    enabled     TINYINT(1) NOT NULL DEFAULT 0,
    start_at    DATETIME,
    end_at      DATETIME,
    updated_by  VARCHAR(100),
    updated_at  DATETIME,
    KEY idx_announcement_enabled (enabled),
    KEY idx_announcement_window (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_active_daily (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(100) NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'user',
    active_date DATE NOT NULL,
    active_at   DATETIME NOT NULL,
    UNIQUE KEY uk_user_active_daily_user_role_date (username, role, active_date),
    KEY idx_user_active_daily_date_role (active_date, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @user_account_pro_expire_at_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_account'
      AND COLUMN_NAME = 'pro_expire_at'
);
SET @user_account_pro_expire_at_sql := IF(
    @user_account_pro_expire_at_exists = 0,
    'ALTER TABLE user_account ADD COLUMN pro_expire_at DATETIME AFTER role',
    'SELECT 1'
);
PREPARE user_account_pro_expire_at_stmt FROM @user_account_pro_expire_at_sql;
EXECUTE user_account_pro_expire_at_stmt;
DEALLOCATE PREPARE user_account_pro_expire_at_stmt;

SET @user_account_pro_expire_at_idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_account'
      AND INDEX_NAME = 'idx_user_account_pro_expire_at'
);
SET @user_account_pro_expire_at_idx_sql := IF(
    @user_account_pro_expire_at_idx_exists = 0,
    'ALTER TABLE user_account ADD INDEX idx_user_account_pro_expire_at (pro_expire_at)',
    'SELECT 1'
);
PREPARE user_account_pro_expire_at_idx_stmt FROM @user_account_pro_expire_at_idx_sql;
EXECUTE user_account_pro_expire_at_idx_stmt;
DEALLOCATE PREPARE user_account_pro_expire_at_idx_stmt;

CREATE TABLE IF NOT EXISTS payment_order (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    out_trade_no     VARCHAR(64) NOT NULL,
    username         VARCHAR(100) NOT NULL,
    product_code     VARCHAR(40) NOT NULL,
    amount_fen       INT NOT NULL,
    currency         VARCHAR(12) NOT NULL DEFAULT 'CNY',
    status           VARCHAR(20) NOT NULL,
    wx_prepay_id     VARCHAR(100),
    wx_code_url      LONGTEXT,
    wx_transaction_id VARCHAR(100),
    expire_at        DATETIME NOT NULL,
    paid_at          DATETIME,
    closed_at        DATETIME,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    UNIQUE KEY uk_payment_order_out_trade_no (out_trade_no),
    KEY idx_payment_order_username_created (username, created_at),
    KEY idx_payment_order_status_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_transaction (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    out_trade_no     VARCHAR(64) NOT NULL,
    wx_transaction_id VARCHAR(100) NOT NULL,
    trade_state      VARCHAR(30) NOT NULL,
    payer_openid     VARCHAR(128),
    success_time     DATETIME,
    raw_json         LONGTEXT,
    created_at       DATETIME NOT NULL,
    UNIQUE KEY uk_payment_tx_wx_transaction_id (wx_transaction_id),
    KEY idx_payment_tx_out_trade_no (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_notify_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    notify_id     VARCHAR(100),
    out_trade_no  VARCHAR(64),
    event_type    VARCHAR(50),
    verify_ok     TINYINT(1) NOT NULL DEFAULT 0,
    process_status VARCHAR(20) NOT NULL,
    raw_body      LONGTEXT,
    created_at    DATETIME NOT NULL,
    UNIQUE KEY uk_payment_notify_id (notify_id),
    KEY idx_payment_notify_out_trade_no (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_reconcile_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_date       DATE NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    download_status VARCHAR(20) NOT NULL,
    local_total     INT NOT NULL DEFAULT 0,
    wechat_total    INT NOT NULL DEFAULT 0,
    diff_count      INT NOT NULL DEFAULT 0,
    report_path     VARCHAR(500),
    created_at      DATETIME NOT NULL,
    UNIQUE KEY uk_payment_reconcile_bill_channel (bill_date, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_dlq_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    notify_id      VARCHAR(100),
    out_trade_no   VARCHAR(64),
    trade_state    VARCHAR(30),
    retry_count    INT NOT NULL DEFAULT 0,
    error_message  VARCHAR(500),
    raw_body       LONGTEXT,
    created_at     DATETIME NOT NULL,
    KEY idx_payment_dlq_notify_id (notify_id),
    KEY idx_payment_dlq_out_trade_no (out_trade_no),
    KEY idx_payment_dlq_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_pricing_config (
    id                  BIGINT PRIMARY KEY,
    pro_month_amount_fen INT NOT NULL DEFAULT 1500,
    free_desc           VARCHAR(500),
    free_features_text  TEXT,
    pro_desc            VARCHAR(500),
    pro_features_text   TEXT,
    updated_at          DATETIME,
    KEY idx_payment_pricing_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_dispatch_dlq_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id       VARCHAR(100),
    email          VARCHAR(150),
    scene          VARCHAR(50),
    retry_count    INT NOT NULL DEFAULT 0,
    error_message  VARCHAR(500),
    created_at     DATETIME NOT NULL,
    KEY idx_email_dispatch_dlq_trace_id (trace_id),
    KEY idx_email_dispatch_dlq_email (email),
    KEY idx_email_dispatch_dlq_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS object_replication_task (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_key         VARCHAR(255) NOT NULL,
    biz_type         VARCHAR(40) NOT NULL,
    biz_id           BIGINT,
    biz_key          VARCHAR(150),
    field_name       VARCHAR(80) NOT NULL,
    object_key       VARCHAR(500) NOT NULL,
    source_provider  VARCHAR(20) NOT NULL,
    target_provider  VARCHAR(20) NOT NULL,
    source_url       LONGTEXT,
    target_url       LONGTEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'pending',
    priority         INT NOT NULL DEFAULT 5,
    retry_count      INT NOT NULL DEFAULT 0,
    max_retries      INT NOT NULL DEFAULT 6,
    next_retry_at    DATETIME,
    last_error       VARCHAR(500),
    done_at          DATETIME,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    UNIQUE KEY uk_object_replication_task_key (task_key),
    KEY idx_object_replication_status_retry (status, next_retry_at),
    KEY idx_object_replication_priority_status (priority, status),
    KEY idx_object_replication_biz (biz_type, biz_id),
    KEY idx_object_replication_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS object_replication_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT NOT NULL,
    trace_id        VARCHAR(100),
    attempt_no      INT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    duration_ms     INT,
    error_message   VARCHAR(500),
    created_at      DATETIME NOT NULL,
    KEY idx_object_replication_log_task (task_id),
    KEY idx_object_replication_log_trace (trace_id),
    KEY idx_object_replication_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS object_outbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(80) NOT NULL,
    event_key       VARCHAR(255) NOT NULL,
    payload_json    LONGTEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    retry_count     INT NOT NULL DEFAULT 0,
    next_retry_at   DATETIME,
    last_error      VARCHAR(500),
    published_at    DATETIME,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    UNIQUE KEY uk_object_outbox_event_key (event_key),
    KEY idx_object_outbox_status_retry (status, next_retry_at),
    KEY idx_object_outbox_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
