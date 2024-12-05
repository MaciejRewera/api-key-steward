CREATE TABLE IF NOT EXISTS api_key_templates_users
(
    tenant_id           UUID NOT NULL,

    api_key_template_id UUID NOT NULL,
    user_id             UUID NOT NULL,

    UNIQUE (api_key_template_id, user_id),

    CONSTRAINT fkey_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fkey_api_key_template_id FOREIGN KEY (api_key_template_id) REFERENCES api_key_template (id),
    CONSTRAINT fkey_user_id FOREIGN KEY (user_id) REFERENCES tenant_user (id)
);
