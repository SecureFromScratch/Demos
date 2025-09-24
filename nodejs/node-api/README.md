# Node API

## Quickstart
1) Copy `.env.example` to `.env` and adjust values (especially `DATABASE_URL` if not using Docker defaults).
2) Install dependencies: `npm i` (or `yarn`, `pnpm i`).
3) Start the database: `npm run db:up`.
4) Run migrations (first time): `npx prisma migrate dev`  
   *(or later: `npm run prisma:migrate`)*.
5) Start the app:  
   - Dev: `npm run dev`  
   - Prod: `npm start`

## Endpoints
- `GET /health`
- `GET /api/v1/items`  
- `POST /api/v1/items`  
- `GET /api/v1/users`  
- `POST /api/v1/users`
- `GET /api/v1/items/search?...` (search by criteria)
