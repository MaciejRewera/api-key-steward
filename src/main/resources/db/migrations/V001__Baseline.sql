
CREATE TABLE IF NOT EXISTS api_key (
    id INTEGER PRIMARY key generated always as identity,
    api_key VARCHAR(256) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (api_key)
);

CREATE TABLE IF NOT EXISTS api_key_data (
    id INTEGER primary key generated always as identity,
    api_key_id INTEGER NOT NULL,
    public_key_id VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description VARCHAR(256),
    user_id VARCHAR(256) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (api_key_id),
    UNIQUE (public_key_id),
    constraint fk_api_key_id foreign key (api_key_id) references api_key (id)
);

CREATE TABLE IF NOT EXISTS client_users(
    id INTEGER primary key generated always as identity,
    client_id VARCHAR(256) NOT NULL,
    user_id VARCHAR(256) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (client_id, user_id)
);
