CREATE TABLE app_users (
    id CHAR(36) NOT NULL PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    email VARCHAR(254) UNIQUE,
    password_hash VARCHAR(255),
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_app_users_role CHECK (role IN ('TEACHER', 'STUDENT'))
);
