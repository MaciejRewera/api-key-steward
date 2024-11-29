CREATE TABLE IF NOT EXISTS api_key_template
(
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID         NOT NULL,

    public_template_id        VARCHAR(128) NOT NULL,
    name                      VARCHAR(280) NOT NULL,
    description               VARCHAR(500),
    is_default                BOOLEAN      NOT NULL,
    api_key_max_expiry_period VARCHAR(128) NOT NULL,
    api_key_prefix            VARCHAR(128) NOT NULL,

    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (public_template_id),
    CONSTRAINT fkey_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);
