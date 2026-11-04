-- The wiki's settings list its entries, newest first; the daily cleanup goes by age.
create index mcp_access_log_wiki_idx on mcp_access_log (wiki_id, created_at desc);
create index mcp_access_log_created_idx on mcp_access_log (created_at);
