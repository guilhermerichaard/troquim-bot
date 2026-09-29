CREATE TABLE owner_phone_credentials (
    owner_id UUID NOT NULL,
    business_id UUID NOT NULL,
    phone_e164 VARCHAR(15) NOT NULL,
    verified_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_owner_phone_credentials PRIMARY KEY (owner_id),
    CONSTRAINT fk_owner_phone_credentials_owner FOREIGN KEY (owner_id) REFERENCES owner_users(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_owner_phone_credentials_phone ON owner_phone_credentials(phone_e164);
CREATE INDEX idx_owner_phone_credentials_business ON owner_phone_credentials(business_id);

CREATE TABLE owner_otp_challenges (
    id UUID NOT NULL,
    owner_id UUID NOT NULL,
    business_id UUID NOT NULL,
    phone_e164 VARCHAR(15) NOT NULL,
    phone_hash VARCHAR(64) NOT NULL,
    code_mac VARCHAR(64) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts_remaining INTEGER NOT NULL,
    consumed_at TIMESTAMP NULL,
    CONSTRAINT pk_owner_otp_challenges PRIMARY KEY (id),
    CONSTRAINT fk_owner_otp_challenges_owner FOREIGN KEY (owner_id) REFERENCES owner_users(id) ON DELETE CASCADE
);
CREATE INDEX idx_owner_otp_phone_recent ON owner_otp_challenges(phone_hash, created_at);
CREATE INDEX idx_owner_otp_owner_purpose ON owner_otp_challenges(owner_id, business_id, purpose);
