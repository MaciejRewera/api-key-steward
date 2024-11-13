CREATE TABLE IF NOT EXISTS api_key_templates_permissions
(
    api_key_template_id INTEGER     NOT NULL,
    permission_id       INTEGER     NOT NULL,

    UNIQUE (api_key_template_id, permission_id),
    CONSTRAINT fk_api_key_template_id FOREIGN KEY (api_key_template_id) REFERENCES api_key_template (id),
    CONSTRAINT fk_permission_id FOREIGN KEY (permission_id) REFERENCES permission (id)
);
