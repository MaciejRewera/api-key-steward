CREATE TABLE IF NOT EXISTS api_key_template
(
    id                                INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    public_id                         VARCHAR(128) NOT NULL,
    api_key_expiry_period_max_seconds INTEGER      NOT NULL,

    created_at                        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (public_id)
);

CREATE TABLE IF NOT EXISTS scope_template
(
    id                  INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    api_key_template_id INTEGER      NOT NULL,
    value               VARCHAR(256) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    description         VARCHAR(256),

    UNIQUE (api_key_template_id, value),
    CONSTRAINT fk_template_id FOREIGN KEY (api_key_template_id) REFERENCES api_key_template (id)
);
