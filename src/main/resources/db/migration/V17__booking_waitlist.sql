-- Waitlist persistida: pedido de aviso, nunca reserva implícita.
-- A confirmação do cliente continua passando pelo caso de uso canônico de booking.

CREATE TABLE booking_waitlist (
    id               UUID         NOT NULL PRIMARY KEY,
    business_id      UUID         NOT NULL,
    phone_e164       VARCHAR(20)  NOT NULL,
    service_id       UUID         NOT NULL,
    professional_id  UUID         NOT NULL,
    requested_date   DATE,
    earliest_time    TIME,
    latest_time      TIME,
    status            VARCHAR(16)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    notified_at       TIMESTAMP
);

CREATE INDEX idx_booking_waitlist_active
    ON booking_waitlist (business_id, status, created_at);

CREATE INDEX idx_booking_waitlist_match
    ON booking_waitlist (business_id, service_id, professional_id, requested_date, status);
