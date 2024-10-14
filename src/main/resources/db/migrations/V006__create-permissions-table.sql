CREATE TABLE IF NOT EXISTS permission
(
    id                   INTEGER PRIMARY key generated always as identity,
    application_id       INTEGER      NOT NULL,

    public_permission_id VARCHAR(128) NOT NULL,
    name                 VARCHAR(280) NOT NULL,
    description          VARCHAR(500),

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (public_permission_id),
    UNIQUE (application_id, name),
    constraint fk_application_id foreign key (application_id) references application (id)
);
