SET TIME ZONE 'UTC';

CREATE TABLE IF NOT EXISTS tenant
(
    id               UUID PRIMARY KEY,
    public_tenant_id VARCHAR(128) NOT NULL,
    name             VARCHAR(256) NOT NULL,
    description      VARCHAR(256),

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at   TIMESTAMPTZ,

    UNIQUE (public_tenant_id)
);
