-- PR-BANNER-01: operation banner and home promotion management MVP.

CREATE TABLE operation_banner (
    id VARCHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    subtitle VARCHAR(255) NULL,
    image_url VARCHAR(500) NOT NULL,
    target_type VARCHAR(32) NULL,
    target_url VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    sort_order INT NOT NULL DEFAULT 0,
    starts_at DATETIME(3) NULL,
    ends_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_operation_banner_public (status, sort_order, starts_at, ends_at),
    KEY idx_operation_banner_admin (deleted_at, status, sort_order),
    CONSTRAINT chk_operation_banner_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_operation_banner_target_type CHECK (target_type IS NULL OR target_type IN ('GOODS', 'SERVICE', 'ADOPTION', 'COMMUNITY', 'EXTERNAL')),
    CONSTRAINT chk_operation_banner_time_window CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Operation home banners';

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES ('00000000-0000-0000-0000-000000000250', 'banner:manage', 'banner', 'manage', 'Manage operation banners');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE code = 'banner:manage';

INSERT IGNORE INTO operation_banner
    (id, title, subtitle, image_url, target_type, target_url, status, sort_order)
VALUES
    ('00000000-0000-0000-0000-000000000281', 'PetSpark 夏日服务季', '寄养、美容、医疗预约一站式守护爱宠', 'https://placehold.co/1200x360?text=PetSpark+Banner', 'SERVICE', '/services', 'ACTIVE', 1);
