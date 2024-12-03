CREATE TABLE IF NOT EXISTS permission
(
    id                   UUID PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    resource_server_id   UUID         NOT NULL,

    public_permission_id VARCHAR(128) NOT NULL,
    name                 VARCHAR(280) NOT NULL,
    description          VARCHAR(500),

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (public_permission_id),
    UNIQUE (resource_server_id, name),

    CONSTRAINT fkey_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fkey_resource_server_id FOREIGN KEY (resource_server_id) REFERENCES resource_server (id)
);
