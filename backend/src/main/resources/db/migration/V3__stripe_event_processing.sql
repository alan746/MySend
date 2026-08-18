alter table accounts add column stripe_updated_at_ms bigint;

create table stripe_events (
    event_id varchar(100) primary key,
    event_type varchar(100) not null,
    event_created_at_ms bigint not null,
    processed_at_ms bigint not null
);
