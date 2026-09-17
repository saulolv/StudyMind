create table content (
    content_id       uuid         primary key,
    user_id          uuid         not null,
    type             varchar(16)  not null,
    status           varchar(16)  not null,
    file_name        varchar(512),
    storage_path     varchar(1024),
    source_url       varchar(2048),
    title            varchar(512),
    duration_seconds integer,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    constraint content_type_check check (type in ('PDF', 'VIDEO')),
    constraint content_status_check check (status in ('PENDING', 'PROCESSING', 'INDEXED', 'FAILED')),
    -- The discriminator carries its own integrity: a PDF has bytes, a video has a URL.
    constraint content_source_check check (
        (type = 'PDF' and storage_path is not null and file_name is not null)
        or (type = 'VIDEO' and source_url is not null)
    )
);

create index idx_content_user_created on content (user_id, created_at desc);
