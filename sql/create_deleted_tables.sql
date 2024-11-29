
CREATE TABLE IF NOT EXISTS tenant_deleted
(
    id               INTEGER PRIMARY key generated always as identity,
    deleted_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    tenant_id        INTEGER      NOT NULL,
    public_tenant_id VARCHAR(128) NOT NULL,
    name             VARCHAR(256) NOT NULL,
    description      VARCHAR(256),

    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    deactivated_at   TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS application_deleted
(
    id                    INTEGER PRIMARY key generated always as identity,
    deleted_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    application_id        INTEGER      NOT NULL,
    tenant_id             INTEGER      NOT NULL,
    public_application_id VARCHAR(128) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(256),

    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    deactivated_at        TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS api_key_data_deleted
(
    id INTEGER primary key generated always as identity,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    api_key_data_id INTEGER NOT NULL,

    api_key_id INTEGER NOT NULL,
    public_key_id VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description VARCHAR(256),
    user_id VARCHAR(256) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
