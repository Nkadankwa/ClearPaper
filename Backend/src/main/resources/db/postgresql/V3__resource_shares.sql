CREATE TABLE resource_shares (
 id CHAR(36) NOT NULL PRIMARY KEY,
 owner_id CHAR(36) NOT NULL,
 resource_id CHAR(36) NOT NULL,
 token_hash CHAR(64) NOT NULL UNIQUE,
 permission VARCHAR(10) NOT NULL,
 name VARCHAR(100) NOT NULL,
 kind VARCHAR(20) NOT NULL,
 data_json TEXT NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_share_owner FOREIGN KEY (owner_id) REFERENCES app_users(id) ON DELETE CASCADE,
 CONSTRAINT fk_share_resource FOREIGN KEY (resource_id) REFERENCES library_resources(id) ON DELETE CASCADE,
 CONSTRAINT ck_share_permission CHECK (permission IN ('VIEW','COPY'))
);
CREATE INDEX ix_share_owner_resource ON resource_shares(owner_id,resource_id);
