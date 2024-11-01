CREATE TABLE IF NOT EXISTS api_key_template
(
    id                        INTEGER PRIMARY key generated always as identity,
    tenant_id                 INTEGER      NOT NULL,

    public_template_id        VARCHAR(128) NOT NULL,
    is_default                BOOLEAN      NOT NULL,
    name                      VARCHAR(280) NOT NULL,
    description               VARCHAR(500),
    api_key_max_expiry_period VARCHAR(128),

    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (public_template_id),
    constraint fk_tenant_id foreign key (tenant_id) references tenant (id)
);
