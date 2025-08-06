CREATE TABLE IF NOT EXISTS auth0_login_token
(
    id           UUID PRIMARY KEY,

    audience     VARCHAR(512)  NOT NULL,
    access_token VARCHAR(1024) NOT NULL,
    scope        VARCHAR(1024) NOT NULL,
    expires_in   INT           NOT NULL,
    token_type   VARCHAR(128)  NOT NULL,

    created_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (audience)
);
