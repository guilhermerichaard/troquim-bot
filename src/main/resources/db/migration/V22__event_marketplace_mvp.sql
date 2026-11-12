-- V22 - Marketplace de eventos: demanda, vagas e candidaturas.
CREATE TABLE marketplace_events (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    title VARCHAR(140) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    city VARCHAR(120) NOT NULL,
    venue VARCHAR(180),
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    budget_cents BIGINT,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_marketplace_event_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_marketplace_event_status CHECK (status IN ('DRAFT','OPEN','MATCHING','STAFFED','IN_PROGRESS','COMPLETED','CANCELED'))
);

CREATE TABLE marketplace_event_roles (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES marketplace_events(id) ON DELETE CASCADE,
    role_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    filled_quantity INTEGER NOT NULL DEFAULT 0,
    pay_cents BIGINT,
    notes VARCHAR(300),
    CONSTRAINT ck_marketplace_role_quantity CHECK (quantity > 0),
    CONSTRAINT ck_marketplace_role_filled CHECK (filled_quantity >= 0 AND filled_quantity <= quantity)
);

CREATE TABLE marketplace_applications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES marketplace_events(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES marketplace_event_roles(id) ON DELETE CASCADE,
    applicant_name VARCHAR(120) NOT NULL,
    applicant_phone VARCHAR(30) NOT NULL,
    message VARCHAR(400),
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_marketplace_application_status CHECK (status IN ('APPLIED','ACCEPTED','REJECTED','WITHDRAWN')),
    CONSTRAINT uq_marketplace_application UNIQUE (role_id, applicant_phone)
);

CREATE INDEX idx_marketplace_events_open ON marketplace_events(status, starts_at);
CREATE INDEX idx_marketplace_events_business ON marketplace_events(business_id, starts_at);
CREATE INDEX idx_marketplace_applications_event ON marketplace_applications(event_id, status);
