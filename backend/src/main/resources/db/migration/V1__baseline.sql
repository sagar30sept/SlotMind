-- Baseline: the schema Hibernate generated before Flyway owned it.
-- Databases created before this migration are baselined at V1 and skip this script.

CREATE TABLE providers (
    version BIGINT,
    id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    specialty ENUM ('GENERAL','PEDIATRIC','SPECIALIST') NOT NULL,
    status ENUM ('AVAILABLE','BUSY','OFFLINE') NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE slots (
    duration_minutes INTEGER NOT NULL,
    start_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT,
    id VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    status ENUM ('BOOKED','CANCELLED','OPEN') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_slots_provider FOREIGN KEY (provider_id) REFERENCES providers (id)
);

CREATE TABLE appointments (
    version BIGINT,
    id VARCHAR(255) NOT NULL,
    patient_name VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    slot_id VARCHAR(255) NOT NULL,
    appointment_type ENUM ('FOLLOW_UP','ROUTINE','URGENT') NOT NULL,
    status ENUM ('COMPLETED','CONFIRMED','RESCHEDULED','RESCHEDULE_PENDING') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_appointments_provider FOREIGN KEY (provider_id) REFERENCES providers (id),
    CONSTRAINT fk_appointments_slot FOREIGN KEY (slot_id) REFERENCES slots (id)
);

CREATE TABLE reschedule_suggestion (
    confidence FLOAT(53) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    reasoning VARCHAR(1000) NOT NULL,
    alternate_provider_id VARCHAR(255),
    appointment_id VARCHAR(255) NOT NULL,
    id VARCHAR(255) NOT NULL,
    recommended_slot_id VARCHAR(255) NOT NULL,
    status ENUM ('ACCEPTED','PENDING','REJECTED') NOT NULL,
    trigger_reason ENUM ('INITIAL','PROVIDER_OFFLINE','SLOT_CANCELLED') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_suggestion_alternate_provider FOREIGN KEY (alternate_provider_id) REFERENCES providers (id),
    CONSTRAINT fk_suggestion_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_suggestion_slot FOREIGN KEY (recommended_slot_id) REFERENCES slots (id)
);

CREATE TABLE waitlist_entries (
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    note VARCHAR(280),
    id VARCHAR(255) NOT NULL,
    patient_name VARCHAR(255) NOT NULL,
    preferred_provider_id VARCHAR(255),
    appointment_type ENUM ('FOLLOW_UP','ROUTINE','URGENT') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_waitlist_provider FOREIGN KEY (preferred_provider_id) REFERENCES providers (id)
);

CREATE TABLE outbox_job (
    attempts INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    locked_until TIMESTAMP(6) WITH TIME ZONE,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(128),
    dedupe_key VARCHAR(160) NOT NULL UNIQUE,
    last_error VARCHAR(1000),
    payload VARCHAR(4000) NOT NULL,
    id VARCHAR(255) NOT NULL,
    job_type ENUM ('FANOUT_PROVIDER_OFFLINE','FANOUT_SLOT_CANCELLED','SUGGEST') NOT NULL,
    status ENUM ('DONE','FAILED','NEW','PROCESSING') NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_claim ON outbox_job (status, next_attempt_at, created_at);
