# URL shortener

A basic Java 21 / Spring Boot application covering all seven milestones.
H2 is the database. Redis is optional. There is no UI or extra infrastructure.

## Run

Requires JDK 21. The Maven wrapper downloads Maven on its first run.

```powershell
.\mvnw.cmd spring-boot:run
```

The server listens on `http://localhost:8080`. H2 stores mappings in the
`data` directory, so they survive restarts. Run from the project root to keep
using the same database. Only one application instance should use this H2 file.

If the public address or server port changes, set `APP_BASE_URL` to the address
clients should use. It defaults to `http://localhost:8080`.

### JWT signing key

For a quick local start, no configuration is needed: a random signing key is
generated and a warning is logged. Tokens then stop working when the app restarts.
Users and links still persist; log in again to get a new token.

For tokens that survive restarts, generate a key once and set it before starting:

```powershell
$key = [byte[]]::new(32)
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($key)
$random.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($key)
.\mvnw.cmd spring-boot:run
```

Keep the same key in a private environment setting; never commit it. The key must
be Base64-encoded and contain at least 32 random bytes. Tokens last two hours.

### Optional Redis

The default run does not connect to Redis and performs database lookups directly.
To enable caching, start your own Redis server and use:

```powershell
$env:SPRING_PROFILES_ACTIVE = "redis"
# Defaults: localhost:6379. Optional overrides:
# $env:REDIS_HOST = "localhost"
# $env:REDIS_PORT = "6379"
# $env:REDIS_PASSWORD = "your-password"
.\mvnw.cmd spring-boot:run
```

The Redis profile verifies the connection at startup and reports errors instead
of silently disabling caching. Cache entries have a one-minute TTL. Updates,
deletes, scheduled cleanup, and attempted expired redirects evict entries.
Every redirect checks the cached expiry time, so a cache hit cannot extend expiry.
Do not enable Redis if you do not have a running Redis server.

## API

All `/api/links` and `/api/shorten` calls require a bearer token. Redirects stay
public. Registration and login are public.

| Method | Endpoint | Behavior |
|--------|----------|----------|
| POST | `/api/auth/register` | Create user and return token (201) |
| POST | `/api/auth/login` | Return token (200) |
| POST | `/api/shorten` | Create owned link (201) |
| GET | `/{code}` | Public 302 redirect |
| GET | `/api/links?page=0&size=20` | List your links, newest first |
| GET | `/api/links/{code}` | Get your link |
| PUT | `/api/links/{code}` | Replace URL and expiry; code stays unchanged |
| DELETE | `/api/links/{code}` | Delete link and its clicks (204) |
| GET | `/api/links/{code}/stats` | Total clicks and counts per UTC day |

Usernames are case-insensitive, 3-30 letters, digits, or underscores. Passwords
must be at least 8 characters and at most 72 UTF-8 bytes; they are BCrypt-hashed.

### Register and shorten

```powershell
$credentials = @{ username = "alice"; password = "a-long-local-password" } | ConvertTo-Json
$auth = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/register" `
    -ContentType "application/json" -Body $credentials
# Later, send the same body to /api/auth/login.
$headers = @{ Authorization = "Bearer $($auth.accessToken)" }

$body = @{ url = "https://example.com/a/long/path" } | ConvertTo-Json
$link = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/shorten" `
    -Headers $headers -ContentType "application/json" -Body $body
$link
```

Returns **201 Created** with a `Location` header pointing to the short link.
Generated codes are Base62 encodings of database IDs, using
`0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`:

```json
{
  "code": "1",
  "originalUrl": "https://example.com/a/long/path",
  "shortUrl": "http://localhost:8080/1",
  "expiresAt": null
}
```

Each request creates a new mapping, even for a previously shortened URL. Codes
are case-sensitive and database-unique.

For a custom alias and expiry, use:

```json
{
  "url": "https://example.com",
  "alias": "my-blog",
  "expiresAt": "2030-01-01T00:00:00Z"
}
```

Aliases are 3-40 characters, contain a hyphen, and otherwise use letters/digits.
They must start and end with a letter/digit. This keeps aliases separate from
generated Base62 codes. An existing alias returns **409 Conflict**.
Expiry must be a future ISO-8601 instant. Omit it or use `null` for no expiry.
An expired redirect returns **410 Gone** and does not record a click.

### Follow a short link

```powershell
curl.exe -i $link.shortUrl
```

Returns **302 Found**, an empty body, and
`Location: https://example.com/a/long/path`. Unknown codes return **404 Not Found**.
The optional `Referer` header is saved with the click (up to 2048 characters).
Analytics writes run asynchronously, so a stats request immediately after a
redirect may not yet include that click.

### Manage a link

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/links" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8080/api/links/$($link.code)/stats" -Headers $headers

$update = @{ url = "https://example.com/new"; expiresAt = $null } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "http://localhost:8080/api/links/$($link.code)" `
    -Headers $headers -ContentType "application/json" -Body $update

Invoke-RestMethod -Method Delete -Uri "http://localhost:8080/api/links/$($link.code)" `
    -Headers $headers
```

PUT requires the URL; omitted/null `expiresAt` removes any existing expiry.
Alias changes are not supported. Another user's link returns 404 from management
and stats endpoints, not its details. List page sizes are limited to 1-100.

Stats response:

```json
{
  "totalClicks": 3,
  "clicksPerDay": {
    "2026-10-02": 3
  }
}
```

## Validation, throttling, and cleanup

- Create and update require an absolute HTTP(S) URL, at most 2048 characters,
  without embedded credentials. Missing, malformed, or invalid inputs return 400.
- Hostnames must resolve. All resolved addresses are checked; localhost,
  private, loopback, link-local, multicast, and shared/internal ranges are blocked.
- DNS validation happens when creating/updating, not on every redirect. The server
  never fetches the destination URL. This is basic protection, not a guarantee
  against later DNS changes or DNS rebinding.
- Bucket4j allows 10 create attempts per user, refilling over one minute. Exhaustion
  returns 429 with `Retry-After`. Buckets are in memory, reset on restart, and are
  not shared across instances. Idle buckets are removed periodically.
- Cleanup runs hourly and removes up to 100 links that expired over seven days
  ago, along with their clicks. Keeping them temporarily allows 410 responses;
  after removal redirects return 404. An alias may be reused after deletion.
- Async analytics is best-effort, not a durable queue. Shutdown, overload, or
  database failures can lose clicks; these conditions are logged.
- Application errors use problem-detail responses; authentication failures use
  standard Spring Security 401 responses.

## Structure and milestones

1. `url`: controller/service/repository layers, shorten, and 302 redirects.
2. `Base62`: ID-driven encoding; `UrlMapping` has a unique code constraint.
3. `url`: custom aliases, expiry, owned CRUD/list, and scheduled cleanup.
4. `analytics`: Click entity, `@OneToMany`, async recording, SQL aggregation, and maps.
5. `config/CacheConfig`: Spring Cache with an optional Redis profile and eviction.
6. `UrlValidator` and `CreateRateLimiter`: URL checks and Bucket4j throttling.
7. `auth` and `config/SecurityConfig`: users, BCrypt, JWT, and ownership.

The code column is nullable only during the insert needed to obtain a generated
ID; the service sets it before the transaction commits. Ownership is nullable
for compatibility with links created before users were introduced. Those old
links remain publicly redirectable but are not listed or manageable by new users.
Their decimal codes are preserved; creation skips IDs whose Base62 code would
collide with an old decimal code.

## Build

```powershell
.\mvnw.cmd clean package -Dmaven.test.skip=true
```

No test files or test-only dependencies are included, as requested.

This is a single-instance learning application, not a production deployment.
Redis eviction happens after database commit; concurrent in-flight lookups can
briefly cache an older URL until TTL expiry. Registration/login throttling,
distributed limits, refresh tokens, production schema migrations, and a durable
analytics queue are intentionally outside this basic implementation. Use HTTPS,
a persistent private JWT key, and additional operational protection before
exposing it publicly.
