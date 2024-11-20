CREATE TABLE IF NOT EXISTS api_key_templates_users
(
    api_key_template_id INTEGER NOT NULL,
    user_id             INTEGER NOT NULL,

    UNIQUE (api_key_template_id, user_id),
    CONSTRAINT fk_api_key_template_id FOREIGN KEY (api_key_template_id) REFERENCES api_key_template (id),
    CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES tenant_user (id)
);
