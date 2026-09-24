CREATE TABLE service_upsell_rules (
    business_id UUID NOT NULL,
    base_service_id UUID NOT NULL,
    addon_service_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT pk_service_upsell_rules PRIMARY KEY (business_id, base_service_id),
    CONSTRAINT fk_service_upsell_rules_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),
    CONSTRAINT fk_service_upsell_rules_base
        FOREIGN KEY (business_id, base_service_id)
        REFERENCES services(business_id, id),
    CONSTRAINT fk_service_upsell_rules_addon
        FOREIGN KEY (business_id, addon_service_id)
        REFERENCES services(business_id, id),
    CONSTRAINT ck_service_upsell_rules_distinct
        CHECK (base_service_id <> addon_service_id)
);
