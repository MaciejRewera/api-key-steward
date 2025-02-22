-- This file contains SQL commands to create functions and triggers which copy rows to a dedicated table just before they are deleted.

CREATE OR REPLACE FUNCTION copy_tenant() RETURNS TRIGGER AS
$BODY$
BEGIN
    INSERT INTO tenant_deleted (deleted_at, tenant_id, public_tenant_id, name, description, created_at, updated_at,
                                deactivated_at)
    VALUES (now(),
            OLD.id,
            OLD.public_tenant_id,
            OLD.name,
            OLD.description,
            OLD.created_at,
            OLD.updated_at,
            OLD.deactivated_at);

    RETURN OLD;
END;
$BODY$
    LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION copy_resourceServer() RETURNS TRIGGER AS
$BODY$
BEGIN
    INSERT INTO resourceServer_deleted (deleted_at, resourceServer_id, tenant_id, public_resourceServer_id, name, description,
                                     created_at, updated_at, deactivated_at)
    VALUES (now(),
            OLD.id,
            OLD.tenant_id,
            OLD.public_resourceServer_id,
            OLD.name,
            OLD.description,
            OLD.created_at,
            OLD.updated_at,
            OLD.deactivated_at);

    RETURN OLD;
END;
$BODY$
    LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION copy_api_key_data() RETURNS TRIGGER AS
$BODY$
BEGIN
    INSERT INTO api_key_data_deleted (deleted_at, api_key_data_id, api_key_id, public_key_id, name, description,
                                      user_id, expires_at, created_at, updated_at)
    VALUES (now(),
            OLD.id,
            OLD.api_key_id,
            OLD.public_key_id,
            OLD.name,
            OLD.description,
            OLD.user_id,
            OLD.expires_at,
            OLD.created_at,
            OLD.updated_at);

    RETURN OLD;
END;
$BODY$
    LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER copy_tenant_on_deletion
    BEFORE DELETE
    ON tenant
    FOR EACH ROW
EXECUTE PROCEDURE copy_tenant();

CREATE OR REPLACE TRIGGER copy_resourceServer_on_deletion
    BEFORE DELETE
    ON resourceServer
    FOR EACH ROW
EXECUTE PROCEDURE copy_resourceServer();

CREATE OR REPLACE TRIGGER copy_api_key_data_on_deletion
    BEFORE DELETE
    ON api_key_data
    FOR EACH ROW
EXECUTE PROCEDURE copy_api_key_data();
