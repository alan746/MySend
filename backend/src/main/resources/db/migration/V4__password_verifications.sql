create table password_verifications (
    id varchar(36) primary key,
    account_id varchar(36) not null references accounts(id) on delete cascade,
    email varchar(320) not null,
    purpose varchar(16) not null,
    code_hash varchar(64) not null,
    expires_at_ms bigint not null,
    consumed_at_ms bigint,
    created_at_ms bigint not null
);

create index password_verifications_lookup_idx
    on password_verifications (email, purpose, created_at_ms);

create index password_verifications_expiry_idx
    on password_verifications (expires_at_ms);
