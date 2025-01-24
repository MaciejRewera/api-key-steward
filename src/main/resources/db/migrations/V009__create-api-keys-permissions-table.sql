CREATE TABLE IF NOT EXISTS api_keys_permissions
(
    tenant_id       UUID NOT NULL,

    api_key_data_id UUID NOT NULL,
    permission_id   UUID NOT NULL,

    UNIQUE (api_key_data_id, permission_id),

    CONSTRAINT fkey_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fkey_api_key_data_id FOREIGN KEY (api_key_data_id) REFERENCES api_key_data (id),
    CONSTRAINT fkey_permission_id FOREIGN KEY (permission_id) REFERENCES permission (id)
);
