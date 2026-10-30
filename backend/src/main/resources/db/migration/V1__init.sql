-- Portable between Postgres (prod) and H2 in PostgreSQL mode (local dev, tests).
-- Page content (Tiptap JSON) is stored as text: the app reads and writes it whole,
-- never queries into it; plain_text holds its searchable text.

-- Names of Keycloak users this app has seen (own login or found by the user search),
-- for showing owners, members, editors and comment authors.
create table app_user (
    -- Keycloak user id (JWT "sub")
    id varchar(255) primary key,
    username varchar(255) not null,
    display_name varchar(500) not null,
    email varchar(500),
    updated_at timestamp with time zone not null
);

create table stored_file (
    id uuid primary key,
    -- No foreign key (wiki.logo_file_id points the other way); WikiService deletes a wiki's files.
    wiki_id char(8) not null,
    uploaded_by varchar(255) not null,
    media_type varchar(100) not null,
    size_bytes bigint not null,
    width integer,
    height integer,
    storage_path varchar(1000) not null,
    created_at timestamp with time zone not null
);
create index stored_file_wiki_idx on stored_file (wiki_id);

create table wiki (
    -- 8 random characters [a-z0-9], the first segment of every URL.
    id char(8) primary key,
    owner_id varchar(255) not null,
    name varchar(200) not null,
    description varchar(2000) not null,
    logo_file_id uuid references stored_file (id) on delete set null,
    -- PRIVATE, PUBLIC
    visibility varchar(16) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
create index wiki_owner_idx on wiki (owner_id);

create table wiki_member (
    wiki_id char(8) not null references wiki (id) on delete cascade,
    user_id varchar(255) not null,
    -- EDITOR, VIEWER
    role varchar(16) not null,
    added_at timestamp with time zone not null,
    primary key (wiki_id, user_id)
);
create index wiki_member_user_idx on wiki_member (user_id);

-- Public wikis a user follows; only listed while the wiki is public.
create table wiki_subscription (
    wiki_id char(8) not null references wiki (id) on delete cascade,
    user_id varchar(255) not null,
    created_at timestamp with time zone not null,
    primary key (wiki_id, user_id)
);
create index wiki_subscription_user_idx on wiki_subscription (user_id);

-- Categories and pages. Depth rules (NodeService): level 0 categories only,
-- level 1 pages or sub-categories, level 2 pages under a sub-category.
create table node (
    id uuid primary key,
    wiki_id char(8) not null references wiki (id) on delete cascade,
    parent_id uuid references node (id) on delete cascade,
    -- parent_id, or the zero UUID on the top level: makes the unique slug index
    -- below work on the top level too (NULLs never collide in a unique index).
    parent_key uuid not null,
    -- CATEGORY, PAGE
    kind varchar(16) not null,
    title varchar(200) not null,
    slug varchar(200) not null,
    position integer not null,
    content text not null,
    plain_text text not null,
    -- Optimistic locking: saving an outdated version answers 409.
    version integer not null,
    created_by varchar(255) not null,
    updated_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint node_slug_unique unique (wiki_id, parent_key, slug)
);
create index node_wiki_idx on node (wiki_id);

create table comment (
    id uuid primary key,
    node_id uuid not null references node (id) on delete cascade,
    -- Null for a top-level comment; replies always point at a top-level comment.
    parent_id uuid references comment (id) on delete cascade,
    author_id varchar(255) not null,
    body text not null,
    created_at timestamp with time zone not null,
    edited_at timestamp with time zone,
    -- Deleted comments with replies stay as "deleted" so the thread keeps its place.
    deleted boolean not null
);
create index comment_node_idx on comment (node_id);
