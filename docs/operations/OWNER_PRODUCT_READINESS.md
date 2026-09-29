# Owner product — verified state, 2026-09-29

## Production identity

- Backend main inspected at `d34d82aec9405207e49e96cc314c6fe864282901`.
- Vercel alias `studiomalumota.vercel.app` belongs to project `troquim`
  (`prj_6mQyG8nw1EL8Sfmk633Lp5QdY8wb`), framework Vite, repository
  `guilhermerichaard/troquim`, production branch `main`.
- The Vercel deployment lookup reported `dpl_ETNdnkjBBoNPcteMaHSS6evFSBNG`, commit
  `bb7e8821865eaaa93c6c11b8e9b3b53bb8019c83`, production/READY.
- That repository's `/admin` renders `src/TroquimAdmin.jsx`: appointments, customers,
  services and revenue are local fixtures. This is not evidence of backend integration.
- `troquim-bot/console` is a separate existing Next.js BFF consuming owner endpoints.
  It already delegates create/reschedule/cancel to the existing Java application/domain.
  It is the reusable authenticated implementation; no new booking domain was introduced.
- Do not delete or redirect the public Vite site until the canonical console deployment
  has passed authenticated checks. Keep the salon landing separate from the owner product.

## Implemented on this branch

- Resolve sessions against the current owner status and business membership; suspended
  owners and sessions from a previous tenant no longer authenticate.
- Password login performs a hash comparison for missing accounts too.
- Browser origin checks protect owner mutations in the backend and BFF. Webhooks and
  authenticated server-to-server BFF requests remain separate from browser CSRF checks.
- List and revoke owner sessions using the existing session store; public identifiers
  expose neither raw session tokens nor the stored token hashes. Revocation is scoped
  by both owner and tenant. The current session survives revoke-others.
- Security page and navigation, including explicit logout failure handling.
- Agenda day/week views, date navigation, professional/status filters, loading/error
  states and accessible native reschedule dialog.
- Cancel obsolete availability requests, clear incompatible selections and reuse
  idempotency keys for retries of an unchanged booking/reschedule request.
- Node request-boundary tests, Java regression tests, reproducible npm lockfile and CI
  frontend test step. A pre-existing day-dependent test now compares the parser's
  canonical unaccented weekday, preserving the context assertion.

## Configuration

Set `TROQUIM_PUBLIC_ORIGIN=https://app.troquim.app` in the console deployment.
This pins BFF browser requests to the canonical origin independently of internal proxy
hostnames. Local development can explicitly set `http://localhost:3000`.

The backend accepts browser origins `https://app.troquim.app` and
`https://api.troquim.app` by default. To use another trusted origin explicitly configure
`troquim.owner.browser-origins` (Spring environment equivalent:
`TROQUIM_OWNER_BROWSER_ORIGINS`). Never use wildcards. This is an origin check, not CORS
permission: browsers continue to use the same-origin BFF. The BFF forwards only the
owner cookie to the fixed backend origin, with no browser-supplied tenant or admin key.

## Verification so far

- Backend suite: 1,255 tests discovered, 0 failures, 0 errors, 65 skipped because Docker
  is unavailable here. PostgreSQL/Testcontainers gates MUST still execute in CI.
- Focused owner/origin/booking suite: 30 tests, 0 failures/errors/skips.
- Frontend request-boundary suite: 5 tests passed; clean production build passed,
  including the Security page (16 routes).
- Browser execution was attempted with agent-browser and Playwright; local Chrome
  crashes before rendering. No successful visual or browser E2E verification claimed.
- `GET https://api.troquim.app/actuator/health` returned `status: UP` on 2026-09-28.
  A HEAD request returns 401 because the public security rule permits GET specifically.
- `app.troquim.app/login` returned 502 via the execution network. This alone does not
  establish whether DNS, proxy routing or the console service is the root cause.

## GitHub publication gate

The branch `fix/owner-session-and-booking-safety` was created at the original main
commit. Uploading modified source/tests with `github_create_tree` was rejected twice
by automatic approval review. Read-only verification confirmed public repository
ownership and linked-account admin/push access, but the reviewer still requires a
user-authored in-chat approval to publish the new local code to this public repository.
No new remote commit or PR exists. Do not try another upload channel to bypass this gate.

Required confirmation: authorize publishing these changes and opening a PR in the
public repository `guilhermerichaard/troquim-bot`.

## External blockers and unimplemented scope

- Vercel project details tool fails argument validation; protected deployment fetch
  denies access. Explicit team requests return 403 requiring reauthentication for
  `guilhermerichaards-projects` (`team_2Ydh9WyRJ8lEhkpn8YmEBX1q`). No deployment,
  environment-variable or DNS mutation has been made.
- GitHub run `36033704972` is overall success, but its `deploy` job is **skipped**.
  This does not prove release publication. Inspect/finish the existing AWS OIDC/SSM
  setup before arming production; do not bypass the documented deployment gates.
- WhatsApp OTP and passkeys are NOT implemented by this branch. Phone ownership
  enrollment, OTP persistence/rate limiting, approved Meta authentication template,
  WebAuthn ceremonies and credential storage remain work, not just configuration.
- Official Meta COPY_CODE template documentation was checked. Templates must be in
  AUTHENTICATION and approved before enabling outbound codes; do not claim WhatsApp
  codes can be read directly by a browser. No Meta template was submitted.
- No live WhatsApp→panel appointment E2E, biometrics E2E, production screenshots or
  canonical domain migration has been completed.

Required access to finish publishing: reconnect Vercel with the project/team above;
provide the existing authorized DNS and AWS deployment paths. Do not paste secrets
into chat. The inaccessible `chatgpt-export.zip` is not required for code validation.
