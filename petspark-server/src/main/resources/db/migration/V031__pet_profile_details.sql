-- PR-PET-02: richer pet profile details used by the product detail page.
ALTER TABLE pet
    ADD COLUMN color VARCHAR(64) NULL AFTER description,
    ADD COLUMN behavior_traits VARCHAR(500) NULL AFTER color,
    ADD COLUMN sterilization_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' AFTER behavior_traits,
    ADD COLUMN training_level VARCHAR(16) NOT NULL DEFAULT 'UNASSESSED' AFTER sterilization_status,
    ADD COLUMN special_needs VARCHAR(1000) NULL AFTER training_level,
    ADD COLUMN registered_at DATE NULL AFTER special_needs;
