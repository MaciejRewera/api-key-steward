CREATE TABLE IF NOT EXISTS api_key_templates_permissions
(
    tenant_id           UUID NOT NULL,

    api_key_template_id UUID NOT NULL,
    permission_id       UUID NOT NULL,

    UNIQUE (api_key_template_id, permission_id),

    CONSTRAINT fkey_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fkey_api_key_template_id FOREIGN KEY (api_key_template_id) REFERENCES api_key_template (id),
    CONSTRAINT fkey_permission_id FOREIGN KEY (permission_id) REFERENCES permission (id)
);
