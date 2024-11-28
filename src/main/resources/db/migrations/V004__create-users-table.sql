CREATE TABLE IF NOT EXISTS tenant_user
(
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL,

    public_user_id VARCHAR(256) NOT NULL,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (tenant_id, public_user_id),
    CONSTRAINT fkey_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);
