create table storage_deletions (
    storage_key varchar(512) primary key,
    size_bytes bigint not null,
    queued_at_ms bigint not null,
    attempts integer not null default 0,
    next_attempt_at_ms bigint not null,
    last_error varchar(500)
);

create index storage_deletions_due_idx
    on storage_deletions (next_attempt_at_ms, queued_at_ms);
