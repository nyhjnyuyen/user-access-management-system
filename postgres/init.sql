CREATE DATABASE "user-service";

\connect "auth-service";

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255),
    enabled BOOLEAN NOT NULL
    );

CREATE TABLE IF NOT EXISTS activation_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(128) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
                          );
\connect "user-service";


