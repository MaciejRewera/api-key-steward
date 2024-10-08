CREATE TABLE IF NOT EXISTS application
(
    id                    INTEGER PRIMARY key generated always as identity,
    tenant_id             INTEGER      NOT NULL,

    public_application_id VARCHAR(128) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(256),

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at        TIMESTAMPTZ,

    UNIQUE (public_application_id),
    constraint fk_tenant_id foreign key (tenant_id) references tenant (id)
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
