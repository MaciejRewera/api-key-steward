CREATE TABLE IF NOT EXISTS scope
(
    id    INTEGER PRIMARY key generated always as identity,
    scope VARCHAR(256) NOT NULL,
    UNIQUE (scope)
);

CREATE TABLE IF NOT EXISTS api_key_data_scopes
(
    api_key_data_id INTEGER     NOT NULL,
    scope_id        INTEGER     NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (api_key_data_id, scope_id),
    CONSTRAINT fk_api_key_data_id FOREIGN KEY (api_key_data_id) REFERENCES api_key_data (id),
    CONSTRAINT fk_scope_id FOREIGN KEY (scope_id) REFERENCES scope (id)
);

CREATE TABLE IF NOT EXISTS api_key_data_scopes_deleted
(
    id              INTEGER primary key generated always as identity,
    deleted_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    api_key_data_id INTEGER     NOT NULL,
    scope_id        INTEGER     NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);
