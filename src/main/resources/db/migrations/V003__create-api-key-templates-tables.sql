

CREATE TABLE IF NOT EXISTS scope_template
(
    id                  INTEGER PRIMARY key generated always as identity,
    api_key_template_id INTEGER      NOT NULL,
    value               VARCHAR(256) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    description         VARCHAR(256),
    UNIQUE (api_key_template_id, value)
--     CONSTRAINT fk_template_id FOREIGN KEY (api_key_template_id) REFERENCES api_key_template (id)
);
