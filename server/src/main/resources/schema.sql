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
