create table authentication_attempts (
    id varchar(36) primary key,
    email varchar(320) not null,
    attempt_type varchar(32) not null,
    attempted_at_ms bigint not null
);

create index authentication_attempts_lookup_idx
    on authentication_attempts (email, attempt_type, attempted_at_ms);

create index authentication_attempts_expiry_idx
    on authentication_attempts (attempted_at_ms);
