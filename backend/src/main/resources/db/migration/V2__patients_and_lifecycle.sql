-- Patients become their own record, and appointments gain cancel / check-in / no-show states.

CREATE TABLE patients (
    version BIGINT,
    id VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    -- Nullable only for patients backfilled from free-text names; the API requires a phone.
    phone VARCHAR(32),
    email VARCHAR(255),
    date_of_birth DATE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_patients_phone UNIQUE (phone)
);

CREATE INDEX idx_patients_name ON patients (full_name);

-- One patient per distinct free-text name already on the desk.
INSERT INTO patients (id, full_name, created_at, version)
SELECT 'PAT-M' || LPAD(CAST(ROW_NUMBER() OVER (ORDER BY name) AS VARCHAR), 4, '0'), name, CURRENT_TIMESTAMP, 0
FROM (
    SELECT patient_name AS name FROM appointments
    UNION
    SELECT patient_name AS name FROM waitlist_entries
) names;

ALTER TABLE appointments ADD COLUMN patient_id VARCHAR(255);
UPDATE appointments a SET patient_id = (SELECT p.id FROM patients p WHERE p.full_name = a.patient_name);
ALTER TABLE appointments ALTER COLUMN patient_id SET NOT NULL;
ALTER TABLE appointments DROP COLUMN patient_name;
ALTER TABLE appointments ADD CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES patients (id);
CREATE INDEX idx_appointments_patient ON appointments (patient_id);

ALTER TABLE waitlist_entries ADD COLUMN patient_id VARCHAR(255);
UPDATE waitlist_entries w SET patient_id = (SELECT p.id FROM patients p WHERE p.full_name = w.patient_name);
ALTER TABLE waitlist_entries ALTER COLUMN patient_id SET NOT NULL;
ALTER TABLE waitlist_entries DROP COLUMN patient_name;
ALTER TABLE waitlist_entries ADD CONSTRAINT fk_waitlist_patient FOREIGN KEY (patient_id) REFERENCES patients (id);

ALTER TABLE appointments ALTER COLUMN status SET DATA TYPE
    ENUM ('CANCELLED','CHECKED_IN','COMPLETED','CONFIRMED','NO_SHOW','RESCHEDULED','RESCHEDULE_PENDING');
ALTER TABLE appointments ADD COLUMN cancel_reason VARCHAR(280);
ALTER TABLE appointments ADD COLUMN checked_in_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE appointments ADD COLUMN status_changed_at TIMESTAMP(6) WITH TIME ZONE;
