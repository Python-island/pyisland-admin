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
-- ALTER TABLE admin_user ADD COLUMN session_token VARCHAR(500) AFTER avatar;
