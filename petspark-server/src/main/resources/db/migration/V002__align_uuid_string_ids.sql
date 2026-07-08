-- Align common infrastructure tables with the application-generated UUID identifiers.
-- V001 was already applied on local developer databases, so this correction is kept
-- as an additive migration instead of mutating V001 and breaking Flyway checksums.

ALTER TABLE audit_log
    MODIFY COLUMN id VARCHAR(36) NOT NULL COMMENT 'Application-generated UUID primary key',
    MODIFY COLUMN actor_id VARCHAR(64) NULL COMMENT 'Actor user id; null for system actions',
    MODIFY COLUMN object_id VARCHAR(64) NULL COMMENT 'Target object id';

ALTER TABLE outbox_event
    MODIFY COLUMN id VARCHAR(36) NOT NULL COMMENT 'Application-generated UUID primary key',
    MODIFY COLUMN aggregate_id VARCHAR(64) NOT NULL COMMENT 'Aggregate id';
