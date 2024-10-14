-- This file contains SQL commands to create trigram index for permission.name column.
-- This index greatly improves performance of SELECT queries with LIKE or ILIKE.

-- There are 2 trigram indexes available: GIN and GIST. Their differences are well described here: https://stackoverflow.com/questions/28975517/difference-between-gist-and-gin-index/28976555#28976555
-- Simply use one or the other command below (GIST index creation is commented out just in case you execute this file as is).


CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create GIN index
CREATE INDEX IF NOT EXISTS tbl_col_gin_trgm_idx ON permission USING gin (name gin_trgm_ops);

-- Create GIST index
-- CREATE INDEX IF NOT EXISTS tbl_col_gist_trgm_idx ON permission USING gist (name gist_trgm_ops);
