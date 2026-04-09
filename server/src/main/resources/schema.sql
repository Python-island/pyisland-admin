CREATE DATABASE IF NOT EXISTS pyisland_admin_database DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pyisland_admin_database;

CREATE TABLE IF NOT EXISTS app_version (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_name    VARCHAR(100) NOT NULL UNIQUE,
    version     VARCHAR(50)  NOT NULL,
    description VARCHAR(500),
    updated_at  DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    avatar      LONGTEXT,
    created_at  DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE admin_user ADD COLUMN IF NOT EXISTS avatar LONGTEXT AFTER password;
ALTER TABLE admin_user MODIFY COLUMN avatar LONGTEXT;
-- ALTER TABLE admin_user ADD COLUMN session_token VARCHAR(500) AFTER avatar;
