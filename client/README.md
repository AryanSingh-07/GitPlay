# GitPlay frontend

Next.js 16, React 19, and Tailwind CSS frontend for browsing and chatting with indexed GitHub repositories.

See the [project setup guide](../README.md) for backend services, database setup, and required GitHub and AI credentials.

```powershell
npm ci
Copy-Item .env.example .env.local
npm run dev
```

Open http://localhost:3000. `NEXT_PUBLIC_API_BASE_URL` defaults to `http://localhost:8080`; update `.env.local` when the backend uses a different origin, then restart Next.js.

```powershell
npm run lint
npm run typecheck
npm test
npm run build
npm start
```

`npm start` serves the production build. Node.js 20.9 or newer is required. Google fonts are downloaded when compiling, so the initial build needs network access.
