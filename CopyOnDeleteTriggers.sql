CREATE OR REPLACE FUNCTION copy_api_key_data() RETURNS TRIGGER AS
$BODY$
BEGIN
    INSERT INTO api_key_data_deleted (deleted_at, api_key_data_id, api_key_id, public_key_id, name, description, user_id, expires_at, created_at, updated_at)
    VALUES (
               now(),
               OLD.id,
               OLD.api_key_id,
               OLD.public_key_id,
               OLD.name,
               OLD.description,
               OLD.user_id,
               OLD.expires_at,
               OLD.created_at,
               OLD.updated_at
           );

    RETURN OLD;
END;
$BODY$
    LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION copy_api_key_data_scopes() RETURNS TRIGGER AS
$BODY$
BEGIN
    INSERT INTO api_key_data_scopes_deleted (deleted_at, api_key_data_id, scope_id, created_at, updated_at)
    VALUES (
               now(),
               OLD.api_key_data_id,
               OLD.scope_id,
               OLD.created_at,
               OLD.updated_at
           );

    RETURN OLD;
END;
$BODY$
    LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER copy_api_key_data_on_deletion BEFORE DELETE
    ON api_key_data
    FOR EACH ROW
EXECUTE PROCEDURE copy_api_key_data();

CREATE OR REPLACE TRIGGER copy_api_key_data_scopes_on_deletion BEFORE DELETE
    ON api_key_data_scopes
    FOR EACH ROW
EXECUTE PROCEDURE copy_api_key_data_scopes();
