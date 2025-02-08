CREATE TABLE IF NOT EXISTS api_key
(
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,

    api_key    VARCHAR(256) NOT NULL,

    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (api_key),

    CONSTRAINT fkey_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE IF NOT EXISTS api_key_data
(
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,

    api_key_id    UUID         NOT NULL,
    user_id       UUID         NOT NULL,
    template_id   UUID,

    public_key_id VARCHAR(128) NOT NULL,
    name          VARCHAR(256) NOT NULL,
    description   VARCHAR(256),
    expires_at    TIMESTAMPTZ  NOT NULL,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (api_key_id),
    UNIQUE (public_key_id),

    CONSTRAINT fkey_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fkey_api_key_id FOREIGN KEY (api_key_id) REFERENCES api_key (id),
    CONSTRAINT fkey_user_id FOREIGN KEY (user_id) REFERENCES tenant_user (id),
    CONSTRAINT fkey_template_id FOREIGN KEY (template_id) REFERENCES api_key_template (id) ON DELETE SET NULL
);

CREATE INDEX idx_user_id ON api_key_data (user_id);
CREATE INDEX idx_template_id ON api_key_data (template_id);
