create table room_abuse_lock_stripes (
    stripe_id integer primary key
);

insert into room_abuse_lock_stripes (stripe_id) values
    (0), (1), (2), (3), (4), (5), (6), (7),
    (8), (9), (10), (11), (12), (13), (14), (15),
    (16), (17), (18), (19), (20), (21), (22), (23),
    (24), (25), (26), (27), (28), (29), (30), (31),
    (32), (33), (34), (35), (36), (37), (38), (39),
    (40), (41), (42), (43), (44), (45), (46), (47),
    (48), (49), (50), (51), (52), (53), (54), (55),
    (56), (57), (58), (59), (60), (61), (62), (63);

create table room_abuse_attempts (
    id varchar(36) primary key,
    subject_hash varchar(64) not null,
    action varchar(16) not null,
    attempted_at_ms bigint not null
);

create index room_abuse_attempts_lookup_idx
    on room_abuse_attempts (subject_hash, action, attempted_at_ms);

create index room_abuse_attempts_expiry_idx
    on room_abuse_attempts (attempted_at_ms);
