CREATE TABLE IF NOT EXISTS tenant
(
    id               INTEGER PRIMARY key generated always as identity,
    public_tenant_id VARCHAR(128) NOT NULL,
    name             VARCHAR(256) NOT NULL,

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at   TIMESTAMPTZ,
    UNIQUE (public_tenant_id)
);
