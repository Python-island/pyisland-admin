-- 身份认证记录表
CREATE TABLE IF NOT EXISTS `identity_verification` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT,
    `username`              VARCHAR(64)     NOT NULL COMMENT '用户名',
    `outer_order_no`        VARCHAR(64)     NOT NULL COMMENT '商户唯一订单号',
    `certify_id`            VARCHAR(64)     DEFAULT NULL COMMENT '支付宝 certify_id',
    `cert_name_ciphertext`  VARCHAR(512)    DEFAULT NULL COMMENT '姓名 AES-GCM 密文（Base64）',
    `cert_no_ciphertext`    VARCHAR(512)    DEFAULT NULL COMMENT '身份证号 AES-GCM 密文（Base64）',
    `status`                VARCHAR(20)     NOT NULL DEFAULT 'INIT' COMMENT 'INIT/CERTIFYING/PASSED/FAILED',
    `material_info_url`     VARCHAR(1024)   DEFAULT NULL COMMENT '人脸素材对象存储 URL',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outer_order_no` (`outer_order_no`),
    UNIQUE KEY `uk_certify_id` (`certify_id`),
    KEY `idx_username` (`username`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付宝身份认证记录';
