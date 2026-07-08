-- PR-CATALOG-01: goods catalog, stock adjustment, and backend management.

CREATE TABLE goods_category (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_category_code (code),
    KEY idx_goods_category_status (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Goods category';

CREATE TABLE goods (
    id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NULL,
    cover_file_id VARCHAR(36) NULL,
    price DECIMAL(12,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_sku (sku),
    KEY idx_goods_status (status, category_id),
    KEY idx_goods_category (category_id),
    CONSTRAINT fk_goods_category FOREIGN KEY (category_id) REFERENCES goods_category (id),
    CONSTRAINT fk_goods_cover_file FOREIGN KEY (cover_file_id) REFERENCES file_object (id),
    CONSTRAINT chk_goods_price_non_negative CHECK (price >= 0),
    CONSTRAINT chk_goods_stock_non_negative CHECK (stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Goods master data';

CREATE TABLE goods_stock_adjustment (
    id VARCHAR(36) NOT NULL,
    goods_id VARCHAR(36) NOT NULL,
    delta_quantity INT NOT NULL,
    stock_before INT NOT NULL,
    stock_after INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    operator_id VARCHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_goods_stock_adjustment_goods (goods_id, created_at),
    KEY idx_goods_stock_adjustment_operator (operator_id, created_at),
    CONSTRAINT fk_goods_stock_adjustment_goods FOREIGN KEY (goods_id) REFERENCES goods (id),
    CONSTRAINT fk_goods_stock_adjustment_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_goods_stock_adjustment_after CHECK (stock_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Goods stock adjustment audit';

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES ('00000000-0000-0000-0000-000000000218', 'goods:manage', 'goods', 'manage', 'Manage goods catalog and stock');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000218');
