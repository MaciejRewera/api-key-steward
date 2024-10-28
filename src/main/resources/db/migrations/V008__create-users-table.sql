CREATE TABLE IF NOT EXISTS tenant_user
(
    id             INTEGER PRIMARY key generated always as identity,
    tenant_id      INTEGER      NOT NULL,

    public_user_id VARCHAR(256) NOT NULL,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (tenant_id, public_user_id),
    constraint fk_tenant_id foreign key (tenant_id) references tenant (id)
);
