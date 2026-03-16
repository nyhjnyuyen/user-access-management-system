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

INSERT INTO users (username, password, role, email, full_name, enabled)
VALUES (
           'admin',
           '$2a$10$ZkWB/OMtVeXZvM7VAUcXEOquJpMDXZqAZB5wLwF9qTXVh4HnWjFpK',
           'ROLE_ADMIN',
           'dongnghi0905@gmail.com',
           'System Admin',
           TRUE
       )
    ON CONFLICT (username) DO NOTHING;

\connect "user-service";

CREATE TABLE IF NOT EXISTS users (
                                     id BIGSERIAL PRIMARY KEY,
                                     username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255),
    enabled BOOLEAN NOT NULL
    );

INSERT INTO users (username, password, role, email, full_name, enabled)
VALUES (
           'admin',
           '$2a$10$ZkWB/OMtVeXZvM7VAUcXEOquJpMDXZqAZB5wLwF9qTXVh4HnWjFpK',
           'ROLE_ADMIN',
           'dongnghi0905@gmail.com',
           'System Admin',
           TRUE
       )
    ON CONFLICT (username) DO NOTHING;