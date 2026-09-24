CREATE TABLE business_booking_automation (
    business_id UUID NOT NULL,
    reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_hours_before INTEGER NOT NULL DEFAULT 24,
    cancellation_min_hours INTEGER NOT NULL DEFAULT 0,
    upsell_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT pk_business_booking_automation PRIMARY KEY (business_id),
    CONSTRAINT fk_business_booking_automation_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),
    CONSTRAINT ck_business_booking_automation_reminder_hours
        CHECK (reminder_hours_before BETWEEN 1 AND 168),
    CONSTRAINT ck_business_booking_automation_cancel_hours
        CHECK (cancellation_min_hours BETWEEN 0 AND 720)
);

CREATE TABLE appointment_reminder_receipts (
    appointment_id UUID NOT NULL,
    business_id UUID NOT NULL,
    reminder_kind VARCHAR(30) NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT pk_appointment_reminder_receipts
        PRIMARY KEY (appointment_id, reminder_kind),
    CONSTRAINT fk_appointment_reminder_receipts_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_appointment_reminder_receipts_business
        FOREIGN KEY (business_id) REFERENCES businesses(id)
);

CREATE INDEX idx_appointment_reminder_receipts_business
    ON appointment_reminder_receipts(business_id);
