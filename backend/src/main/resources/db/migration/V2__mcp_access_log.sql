-- What an AI assistant read through the MCP server, for the user to audit.
create table mcp_access_log (
    id uuid primary key,
    user_id varchar(255) not null,
    -- Null for tool calls not scoped to one wiki (e.g. list_wikis).
    wiki_id char(8),
    tool varchar(40) not null,
    query varchar(500),
    created_at timestamp with time zone not null
);
create index mcp_access_log_user_idx on mcp_access_log (user_id, created_at desc);
