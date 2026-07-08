-- PR-ORDER-01: direct orders, stock deduction, cancellation, and fulfillment.

CREATE TABLE order_header (
    id VARCHAR(36) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    total_amount DECIMAL(12,2) NOT NULL,
    recipient_name VARCHAR(64) NOT NULL,
    recipient_phone_ciphertext VARCHAR(255) NOT NULL,
    address_ciphertext TEXT NOT NULL,
    cancel_reason VARCHAR(255) NULL,
    cancelled_at DATETIME(3) NULL,
    idempotency_key VARCHAR(64) NULL,
    fulfilled_at DATETIME(3) NULL,
    transition_note VARCHAR(255) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_idem (user_id, idempotency_key),
    KEY idx_order_user_status (user_id, status),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_order_total_non_negative CHECK (total_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order header with idempotency and status';

CREATE TABLE order_item (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    goods_id VARCHAR(36) NOT NULL,
    sku_snapshot VARCHAR(64) NOT NULL,
    name_snapshot VARCHAR(120) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(12,2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    KEY idx_order_item_goods (goods_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES order_header (id),
    CONSTRAINT fk_order_item_goods FOREIGN KEY (goods_id) REFERENCES goods (id),
    CONSTRAINT chk_order_item_qty_positive CHECK (quantity > 0),
    CONSTRAINT chk_order_item_line_amount CHECK (line_amount = unit_price * quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order line items with price snapshot';

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES ('00000000-0000-0000-0000-000000000225', 'order:manage', 'order', 'manage', 'Manage and fulfill orders');

-- OP role (...103) gets order:manage; ADMIN (...102) explicitly bound (V009 blanket SELECT did not capture this perm).
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000225');

-- ADMIN role (...102) explicitly bound to order:manage introduced by this migration.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000225');
