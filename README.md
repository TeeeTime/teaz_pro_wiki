# teaz_pro_wiki
Teaz Wiki, at `https://wiki.teaz.pro`: Confluence-like wikis.

- Every wiki belongs to one user; a user can own any number of them.
- Wikis are **private** by default. The owner can make one **public**:
  then anyone with the link reads it, **also without login**.
- In the wiki's settings the owner adds people (user search over the
  Keycloak realm) as **editor** (write, create, organize) or **viewer**
  (read; only matters while the wiki is private).
- Signed-in users **subscribe** to public wikis to have them in their wiki
  list (the switcher at the top left).
- Every category and page has **comments**, open to everyone signed in who
  can read the wiki; replies are shown one level indented.
- Keyword **search** over a wiki's titles and texts.
- Links shared in Discord, Slack, WhatsApp & co. show a **preview**.
- An **MCP server** lets an AI assistant search wikis read-only, under the
  same permissions as the signed-in user (see "AI access (MCP)" below).

Monorepo: `frontend/` (Vue 3 + TypeScript) and `backend/` (Spring Boot, `pro.teaz:wiki`).

Stack, conventions, style guide and color tokens shared by every Teaz app
live in **`teaz_pro`**: start with `docs/TECH_STACK.md` there. This
README only covers what is specific to this app.

## Structure and URLs

```
wiki.teaz.pro/{wiki id}/{category}/{sub-category or page}/{sub-category or page}/{page}
```

- The wiki id is 8 random characters `[a-z0-9]`.
- Level 0 holds only **categories**; levels 1 and 2 pages or
  **sub-categories**; level 3 only pages. Nothing deeper (`NodeService`).
- Categories have content of their own (context for their pages) and list
  what's in them; a link to a category opens it.
- URL segments are slugs of the names (`Über uns` → `ueber-uns`). Names must
  be unique by slug among siblings: top-level categories in the wiki,
  deeper levels within their category or sub-category.
- A link to the wiki alone opens an overview built from the structure (not
  editable).
- App pages of a wiki use the segment `-`, which is never a slug:
  `/{id}/-/settings`, `/{id}/-/search?q=`, `/{id}/-/new?parent=`.

## Permissions

| | Guest | Signed in | Viewer | Editor | Owner |
| --- | --- | --- | --- | --- | --- |
| Read a public wiki | yes | yes | yes | yes | yes |
| Read a private wiki | – | – | yes | yes | yes |
| Comment, subscribe (public) | – | yes | yes | yes | yes |
| Create, edit, move, delete categories and pages; upload images | – | – | – | yes | yes |
| Settings, members, visibility, delete the wiki; delete any comment | – | – | – | – | yes |

A wiki someone can't read answers 404, the same as one that doesn't exist
(`wiki/WikiAccess.java`). Saving a page with an outdated version answers 409.

## Editor

Tiptap 3 (`frontend/src/editor/`), only opened with **Edit** or for a new
page; reading renders the same document read-only, so nothing changes by
accident. One extension list for both (`extensions.ts`).

- Text styles (text, heading 1–3, quote, code block), bold, italic,
  underline, strikethrough, inline code, links (web or a page of the wiki).
- Bulleted and numbered lists with indent/outdent; align left/center/right.
- **Text and background colors**: stored as names (`red`), shown with the
  theme's `--color-content-*` tokens, readable in light, dark and dark-blue
  (`teaz_pro/docs/COLOR_SCHEMES.md`, rule 6).
- **Tables**: size picker, rows/columns before/after, delete, merge/split,
  header row/column; column widths and row heights by dragging the cell
  edges (`tableRowHeight.ts` for rows).
- **Images** in fixed layouts (`images.ts`): one image (25–100 % wide,
  left/center/right), image left + text right, text left + image right
  (1/3 or 1/2), two or three side by side. Per image: aspect ratio
  (original, 16:9, 4:3, 1:1, 3:4), crop, caption, description. Crops never
  change the file; they are stored as fractions and applied when shown.
  Paste or drop image files into the editor works too.
- **LaTeX** formulas (KaTeX), in the text or on their own line.
- Undo/redo, dividers.

The backend checks the document (`node/ContentText.java`): valid JSON, at
most 2 MB, link targets only `http(s)://`, `mailto:`, `/` or `#`; and keeps
its plain text for search and previews. A new node type that carries text
needs to be handled there too.

## Link previews

Crawlers (Discordbot, Slackbot, WhatsApp, Telegram, ...) don't run the SPA.
`frontend/nginx.conf` recognizes them by user agent and hands them
`/api/preview/<path>` from the backend (`preview/PreviewController.java`):
a small page with Open Graph tags (page and wiki name, start of the text,
wiki logo). Only public wikis show anything; for everything else the
preview is the same as for a wiki that doesn't exist.

Try it locally: `curl -A Discordbot http://localhost:8080/api/preview/<wiki id>/<category>/<page>`.

## Running locally

```sh
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev
```

Or use the IntelliJ run configs in `.idea/runConfigurations/`. Locally:

- **No Docker, no database install:** file-based H2 in PostgreSQL mode
  (`backend/data/`, gitignored, also holds uploaded images). Delete that
  folder for a fresh start.
- **Several users without Keycloak:** the account menu (top right) switches
  between four mock users (Jordan Lee, Sam Rivera, Alex Kim, Maria Novak)
  or signs out to a guest. The frontend sends the chosen user's id as
  `X-Mock-User`; the backend's `MockSecurityConfig` accepts only those ids.
  The user search finds the same four (`MockUserDirectory`).
- Backend port 8080 is also other apps' default: only one of them can run
  locally at a time.

## Shared UI

`TopBar`, `Icon`, `useTheme()` and the theme tokens come from
**`teaz_pro_ui`** (pinned in `frontend/package.json`), see
`teaz_pro/docs/UI_LIBRARY.md`. Wiki needs v0.7.0 or newer (editor icons,
content color tokens).

## Auth

- Frontend `frontend/src/auth/`: Keycloak (`.env.production`: `auth.teaz.pro`,
  realm `teazpro`, client `wiki`) in builds, mock users in `npm run dev`.
  Unlike other Teaz apps, login is **optional**: `check-sso` (silently, via
  `public/silent-check-sso.html`), the app mounts for guests too, and
  "Sign in" in the account menu logs in and comes back to the same page.
- Backend `backend/src/main/java/pro/teaz/wiki/security/`, with the `prod`
  profile: `GET` on wikis, files and previews is let through without a
  token (`WikiAccess` decides per wiki); everything else needs a Keycloak
  Bearer JWT. Any realm user may use Wiki, no extra role. `UserSync` keeps
  the names of signed-in users in `app_user` for display.

Keycloak setup (realm `teazpro`):

1. Client `wiki`, as in the Auth section of `teaz_pro/docs/TECH_STACK.md`
   (public, standard flow, PKCE S256, redirect and web origin
   `https://wiki.teaz.pro`).
2. Client `wiki-admin` for the user search: **Client authentication on**,
   only **Service accounts roles** enabled (no standard flow). Under
   **Service accounts roles → Assign role → Filter by clients**, assign
   `realm-management` **view-users**. Its secret (Credentials tab) goes into
   `.env` as `WIKI_KEYCLOAK_ADMIN_CLIENT_SECRET`.
3. Client `wiki-mcp`, for AI access below: public, standard flow, PKCE S256,
   **Offline access** client scope added, redirect URI(s) of whichever MCP
   client the user connects (e.g. Claude's). Add a client scope `wiki:read`
   (realm-level, type default) and assign it to `wiki-mcp`.

## AI access (MCP)

A read-only [MCP](https://modelcontextprotocol.io) server at
`https://wiki.teaz.pro/api/mcp` (`pro.teaz.wiki.mcp`) lets an AI assistant
search wikis: `list_wikis`, `search_wiki`, `get_page`. There is no tool to
create, edit or delete anything.

- **No new access model.** The MCP endpoint is just another entry point
  into the existing Keycloak JWT + `WikiAccess` chokepoint
  (`security/SecurityConfig.java`): `list_wikis` only ever returns public
  wikis plus the caller's own/shared ones, and `search_wiki`/`get_page`
  still go through `access.read(wikiId)`, so a private wiki a caller
  shouldn't see 404s there exactly as it would in the browser, even if its
  id leaked some other way.
- **No login needed for public wikis.** `/api/mcp` lets anonymous callers
  through, same as the rest of the API. A Bearer token is only trusted as
  that signed-in user if it carries the `wiki:read` scope; any other token
  (a bad signature, the wrong issuer, or a real but unrelated-scope Keycloak
  token, e.g. from the regular `wiki` client) is rejected or silently
  treated as anonymous, never as that person
  (`SecurityConfig.RequireMcpReadScope`) — someone's browser session can
  never leak into AI access just because a token happened to be lying
  around.
- **Connecting** for private wikis too: in an MCP-capable AI client, add
  `https://wiki.teaz.pro/api/mcp` as a remote server. The client discovers
  (RFC 9728, `/.well-known/oauth-protected-resource`) that it may log in via
  the `wiki-mcp` client for the `wiki:read` scope, and opens that
  login/consent in a browser.
- **Revoking access**: Keycloak's own Account Console → Applications →
  `wiki-mcp` → revoke. There's deliberately no separate token to manage on
  the wiki side (OAuth, not a static secret; see the security concept this
  was planned with).
- **Auditing**: `GET /api/my/mcp-access-log` (authenticated) lists the
  caller's recent MCP tool calls (`mcp/McpAccessLog.java`). No UI shows this
  yet in this repo — it's meant for the future `account.teaz.pro`, which
  will host account-level settings across every Teaz app; until then this
  section of the README is the documentation.

## Database

Wiki uses the shared Postgres server on the VPS (`/opt/teazpro/postgres`),
reached over the external Docker network `db`, with its **own database
`wiki` and own user `wiki`** (owner of that database, no superuser
rights). It never reads another app's database.

- Schema: Flyway migrations in `backend/src/main/resources/db/migration`,
  applied on startup. Hibernate never changes the schema (`ddl-auto=none`).
- Connection only from the environment: `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` in `.env`.
- Locally: H2 instead (see Running locally).

## Deployment

Once, on the Postgres server: create the user and database (as in
`teaz_pro_study`'s README, "First deploy", with `wiki` instead of `study`).
Then `.env` next to `compose.yml` (gitignored):

```sh
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/wiki
SPRING_DATASOURCE_USERNAME=wiki
SPRING_DATASOURCE_PASSWORD=<the password given to the wiki role>
WIKI_KEYCLOAK_ADMIN_CLIENT_SECRET=<secret of the wiki-admin client>
```

Then, as for every app:

```sh
git pull && docker compose up -d --build
```

`compose.yml` routes `wiki.teaz.pro` to the frontend (nginx) and
`wiki.teaz.pro/api/*` to the backend, via the shared Traefik (network
`proxy`, cert resolver `le`). The backend also joins `db` for Postgres, and
both join the project's own network `internal`, where nginx reaches the
backend as `wiki-backend` for link previews. Uploaded images live in the
volume `files`; back it up together with the `wiki` database.
