CREATE TABLE IF NOT EXISTS resource_server
(
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID         NOT NULL,

    public_resource_server_id VARCHAR(128) NOT NULL,
    name                      VARCHAR(256) NOT NULL,
    description               VARCHAR(256),

    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at            TIMESTAMPTZ,

    UNIQUE (public_resource_server_id),

    CONSTRAINT fkey_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);
