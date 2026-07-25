create table accounts (
    id varchar(36) primary key,
    email varchar(320) not null unique,
    password_hash varchar(100) not null,
    plan varchar(16) not null,
    stripe_customer_id varchar(100),
    stripe_subscription_id varchar(100),
    created_at_ms bigint not null,
    updated_at_ms bigint not null
);

create table email_verifications (
    id varchar(36) primary key,
    email varchar(320) not null,
    password_hash varchar(100) not null,
    code_hash varchar(64) not null,
    expires_at_ms bigint not null,
    consumed_at_ms bigint,
    created_at_ms bigint not null
);

create index email_verifications_email_idx
    on email_verifications (email, created_at_ms);

create table app_sessions (
    id varchar(36) primary key,
    account_id varchar(36) not null references accounts(id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at_ms bigint not null,
    created_at_ms bigint not null
);

create index app_sessions_account_idx on app_sessions(account_id);

create table rooms (
    id varchar(36) primary key,
    access_code varchar(5) not null unique,
    owner_key varchar(100) not null,
    owner_account_id varchar(36) references accounts(id) on delete set null,
    plan varchar(16) not null,
    visibility varchar(16) not null,
    password_hash varchar(100),
    access_limit integer not null,
    access_count integer not null default 0,
    clipboard_text text not null,
    file_bytes bigint not null default 0,
    created_at_ms bigint not null,
    expires_at_ms bigint not null,
    closed_at_ms bigint,
    version bigint not null default 0
);

create index rooms_owner_idx on rooms(owner_key, expires_at_ms);
create index rooms_expiry_idx on rooms(expires_at_ms, closed_at_ms);

create table room_access_tokens (
    id varchar(36) primary key,
    room_id varchar(36) not null references rooms(id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at_ms bigint not null,
    created_at_ms bigint not null
);

create index room_access_tokens_room_idx on room_access_tokens(room_id);

create table room_files (
    id varchar(36) primary key,
    room_id varchar(36) not null references rooms(id) on delete cascade,
    storage_key varchar(160) not null unique,
    original_name varchar(255) not null,
    content_type varchar(120) not null,
    size_bytes bigint not null,
    uploaded_at_ms bigint not null
);

create index room_files_room_idx on room_files(room_id, uploaded_at_ms);
