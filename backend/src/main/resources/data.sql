INSERT INTO providers (id, name, specialty, status, version) VALUES
('PRV-001', 'Dr. Sarah Chen', 'GENERAL', 'BUSY', 0),
('PRV-002', 'Dr. James Okonkwo', 'SPECIALIST', 'AVAILABLE', 0),
('PRV-003', 'Dr. Priya Sharma', 'PEDIATRIC', 'BUSY', 0),
('PRV-004', 'Dr. Rahul Verma', 'GENERAL', 'AVAILABLE', 0),
('PRV-005', 'Dr. Ananya Iyer', 'SPECIALIST', 'BUSY', 0);

INSERT INTO slots (id, provider_id, start_time, duration_minutes, status, version) VALUES
('SLT-001', 'PRV-001', DATEADD('MINUTE', 60, CURRENT_TIMESTAMP), 30, 'BOOKED', 0),
('SLT-002', 'PRV-001', DATEADD('MINUTE', 120, CURRENT_TIMESTAMP), 30, 'BOOKED', 0),
('SLT-003', 'PRV-003', DATEADD('MINUTE', 90, CURRENT_TIMESTAMP), 45, 'BOOKED', 0),
('SLT-004', 'PRV-002', DATEADD('MINUTE', 180, CURRENT_TIMESTAMP), 30, 'OPEN', 0),
('SLT-005', 'PRV-004', DATEADD('MINUTE', 150, CURRENT_TIMESTAMP), 30, 'OPEN', 0),
('SLT-006', 'PRV-005', DATEADD('MINUTE', 240, CURRENT_TIMESTAMP), 60, 'BOOKED', 0);

INSERT INTO appointments (id, patient_name, appointment_type, provider_id, slot_id, status, version) VALUES
('APT-001', 'John Martinez', 'ROUTINE', 'PRV-001', 'SLT-001', 'CONFIRMED', 0),
('APT-002', 'Emily Watson', 'FOLLOW_UP', 'PRV-001', 'SLT-002', 'CONFIRMED', 0),
('APT-003', 'Aisha Patel', 'URGENT', 'PRV-003', 'SLT-003', 'CONFIRMED', 0),
('APT-004', 'Robert Kim', 'ROUTINE', 'PRV-005', 'SLT-006', 'CONFIRMED', 0);
