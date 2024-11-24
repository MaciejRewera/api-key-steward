ALTER TABLE application RENAME COLUMN public_application_id TO public_resource_server_id;
ALTER TABLE application RENAME TO resource_server;

ALTER TABLE application_deleted RENAME COLUMN application_id TO resource_server_id;
ALTER TABLE application_deleted RENAME COLUMN public_application_id TO public_resource_server_id;
ALTER TABLE application_deleted RENAME TO resource_server_deleted;

ALTER TABLE permission RENAME COLUMN application_id TO resource_server_id;
ALTER TABLE permission DROP CONSTRAINT fk_application_id;
ALTER TABLE permission ADD CONSTRAINT fk_resource_server_id FOREIGN KEY (resource_server_id) REFERENCES resource_server (id);
