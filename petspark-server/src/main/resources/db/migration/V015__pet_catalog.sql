-- PR-PET-01: pet breeds, pets, pet images, and backend permissions.

CREATE TABLE pet_breed (
    id VARCHAR(36) NOT NULL,
    species VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_breed_species_name (species, name, deleted_at),
    KEY idx_breed_status (species, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pet breed master data';

CREATE TABLE pet (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(64) NOT NULL,
    species VARCHAR(32) NOT NULL,
    breed_id VARCHAR(36) NULL,
    sex VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    birth_date DATE NULL,
    description VARCHAR(1000) NULL,
    ownership_type VARCHAR(16) NOT NULL DEFAULT 'USER',
    owner_user_id VARCHAR(36) NULL,
    adoption_status VARCHAR(32) NOT NULL DEFAULT 'NOT_FOR_ADOPTION',
    boarding_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    public_status VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    info_updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_pet_public (public_status, adoption_status),
    KEY idx_pet_owner (owner_user_id),
    KEY idx_pet_breed (breed_id),
    CONSTRAINT fk_pet_breed FOREIGN KEY (breed_id) REFERENCES pet_breed (id),
    CONSTRAINT fk_pet_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pet catalog and owned pets';

CREATE TABLE pet_image (
    id VARCHAR(36) NOT NULL,
    pet_id VARCHAR(36) NOT NULL,
    file_id VARCHAR(36) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    cover_flag TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_pet_image (pet_id, file_id),
    KEY idx_pet_image_file (file_id),
    CONSTRAINT fk_pet_image_pet FOREIGN KEY (pet_id) REFERENCES pet (id),
    CONSTRAINT fk_pet_image_file FOREIGN KEY (file_id) REFERENCES file_object (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pet image links';

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000213', 'pet:manage', 'pet', 'manage', 'Manage pet catalog records'),
    ('00000000-0000-0000-0000-000000000214', 'pet:status', 'pet', 'status', 'Change pet lifecycle status'),
    ('00000000-0000-0000-0000-000000000215', 'breed:manage', 'breed', 'manage', 'Manage pet breeds');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id
FROM sys_permission
WHERE code IN ('pet:manage', 'pet:status', 'breed:manage');
