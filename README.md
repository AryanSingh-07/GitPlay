# GitPlay

GitPlay signs in with GitHub, indexes repository source code into PostgreSQL/pgvector,
and answers questions about that code using streamed AI responses.

Repository lists refresh automatically while the dashboard or overview is open.
The backend checks GitHub about once a minute and hides repositories that are no
longer accessible after a complete, successful sync. Saved repository records and
chat history are retained, so restored access reuses the same records. Failed
GitHub requests leave the last saved list visible. The Sync button checks immediately.

## Requirements

- Node.js 22 and npm (the frontend requires Node.js 20.9 or newer).
- JDK 21. Maven is supplied by `backend/mvnw` and `backend/mvnw.cmd`.
- Docker Desktop running with its Linux engine, or PostgreSQL 16 with the `vector`,
  `hstore`, and `uuid-ossp` extensions installed.
- A GitHub OAuth app. Set its homepage to `http://localhost:3000` and its authorization
  callback URL to `http://localhost:8080/login/oauth2/code/github`.
- A Google AI Studio Gemini API key with access to `gemini-3.1-flash-lite` and
  `gemini-embedding-2`. Both models offer a free quota; availability and limits
  depend on your project. See [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing).
- Internet access for initial package downloads, GitHub, and Gemini requests.

## Configure

Copy `backend/.env.example` to `backend/.env` and fill in the API key, GitHub client ID,
GitHub client secret, token encryption password, and hexadecimal salt. The backend loads
this file automatically when launched from the root or backend directory. Shell
environment variables take precedence. Keep the encryption password and salt stable
after users sign in, because stored GitHub tokens depend on them.

Existing credentials migrated during the project repair are preserved in the ignored
`backend/.env`. Replace previously hardcoded API credentials with newly issued keys.
Never commit `.env` files.

Create a Gemini key at [Google AI Studio](https://aistudio.google.com/api-keys) and
set `GEMINI_API_KEY` in `backend/.env`. `GOOGLE_API_KEY` is also accepted when
`GEMINI_API_KEY` is absent. The old `OPENAI_API_KEY` is no longer used. Keep keys
on the backend; do not put them in `NEXT_PUBLIC_*` variables.

Gemini chat and embeddings use the same key. The Gemini vectors are written to
`vector_store_gemini` with 1536 dimensions; the old `vector_store` is preserved.
Reindex repositories after switching providers. An existing index produced by a
different embedding model cannot be used with Gemini, even at the same dimensions.
If migrating a database with repositories already marked READY, reset those statuses
to PENDING before serving chats, then reindex them. Existing chat history can remain.

Indexing sends batches of at most 16 chunks. Gemini counts each chunk toward the
embedding request quota, so a shared gate spaces batches by their chunk count
(750 ms per chunk by default, approximately 80 chunks per minute). This reduces
indexing bursts; query embeddings and token/day quotas still share your project
limits. Adjust `GEMINI_EMBEDDING_BATCH_SIZE` and
`GEMINI_EMBEDDING_DELAY_MS` to the quota shown for your project in Google AI Studio.
Free quotas can still be exhausted; a failed request leaves the repository FAILED
so you can retry after the quota resets. Google's free tier may use submitted code
and prompts to improve its products; see the pricing page above.

The frontend uses `http://localhost:8080` by default. To override it, copy
`client/.env.example` to `client/.env.local` and set `NEXT_PUBLIC_API_BASE_URL`.
Use `localhost` consistently for both services so session cookies work.

## Run locally

From the project root, start PostgreSQL:

```powershell
docker compose up -d --wait
```

Start the backend in one terminal:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Start the frontend in another terminal:

```powershell
cd client
npm ci
npm run dev
```

Open http://localhost:3000. The backend listens on http://localhost:8080 and PostgreSQL
on localhost:5433. Sign in with GitHub, choose a repository, index it, then open its chat.
Indexing and chat call Gemini and use your Google AI Studio project's quota.

On macOS/Linux, use `./mvnw spring-boot:run` instead of `mvnw.cmd`.

## Check the project

```powershell
cd client
npm run lint
npm run typecheck
npm test
npm run build
cd ..\backend
.\mvnw.cmd clean test
```

## Troubleshooting

- If Docker cannot connect to `dockerDesktopLinuxEngine`, start Docker Desktop and wait
  until its Linux engine is ready. Check `docker info` before starting PostgreSQL.
- A database connection failure usually means PostgreSQL is stopped or `DB_URL` does
  not match its port. Docker maps host port 5433 to PostgreSQL port 5432.
- A GitHub callback error usually means the OAuth app callback URL, client ID, or
  client secret does not match `backend/.env`.
- If GitHub says `redirect_uri is not associated with this application`, open GitHub
  Settings > Developer settings > OAuth apps, select the app whose Client ID matches
  `GITHUB_CLIENT_ID` in `backend/.env`, and set its Authorization callback URL to
  `http://localhost:8080/login/oauth2/code/github`. Click Update application, then
  start sign-in again from `http://localhost:3000/login`. The frontend's
  `/auth/callback` page is the destination after the backend completes OAuth; it is
  not the callback URL registered with GitHub. Keep `localhost` consistent rather
  than switching to `127.0.0.1`.
- A Gemini authentication error requires a valid `GEMINI_API_KEY` in `backend/.env`
  and a backend restart. The key needs access to both configured models.
- A Gemini 429 quota error may mean the free quota is exhausted or indexing is too
  fast. Check your project limits in Google AI Studio, increase
  `GEMINI_EMBEDDING_DELAY_MS`, and retry after quota becomes available.
- Use `docker compose stop` to stop the database without deleting its data. Stop the
  frontend and backend with Ctrl+C in their terminals.
- VS Code's Java auto-build is disabled for this workspace to prevent the editor
  from overwriting Maven's compiled classes. Use `mvnw.cmd clean package` for an
  executable backend jar, then run `java -jar target/backend-0.0.1-SNAPSHOT.jar` from
  the backend directory.

## Dependency audit

The production dependency audit (`npm audit --omit=dev` in `client`) reports zero
vulnerabilities. The full audit reports nine high-severity development-tooling
findings propagated from the unpatched `braces` advisory GHSA-vfj7-8cjw-p6xm. Forced
dependency downgrades were not applied. Recheck the full audit when dependency fixes
become available.

Local startup, page rendering, authentication gating, CORS, logout, and the automated
regression suites have been checked. Live GitHub sign-in, repository indexing, and
Gemini chat still require a successful sign-in and valid external credentials.
