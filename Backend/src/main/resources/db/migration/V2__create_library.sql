CREATE TABLE resource_collections (
    id CHAR(36) NOT NULL PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT fk_collection_owner FOREIGN KEY (owner_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT uq_collection_name UNIQUE (owner_id, name)
);
CREATE TABLE library_resources (
    id CHAR(36) NOT NULL PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    collection_id CHAR(36),
    data_json MEDIUMTEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resource_owner FOREIGN KEY (owner_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_resource_collection FOREIGN KEY (collection_id) REFERENCES resource_collections(id) ON DELETE SET NULL,
    CONSTRAINT ck_resource_kind CHECK (kind IN ('DIAGRAM','WORKSHEET'))
);
CREATE INDEX ix_resource_owner ON library_resources(owner_id, updated_at);
