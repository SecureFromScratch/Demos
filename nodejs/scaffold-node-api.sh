#!/usr/bin/env bash
set -euo pipefail

NAME="${1:-node-api}"
PKG="${PKG:-npm}" # set PKG=yarn or PKG=pnpm if you prefer

mkdir -p "$NAME"
cd "$NAME"

# --- Files & folders ---
mkdir -p src/{routes,controllers,services,middlewares,config,utils} test
cat > .gitignore <<'EOF'
node_modules
.env
.DS_Store
coverage
EOF

cat > .env.example <<'EOF'
PORT=3000
EOF

# package.json
cat > package.json <<'EOF'
{
  "name": "node-api",
  "version": "0.1.0",
  "type": "module",
  "main": "src/server.js",
  "scripts": {
    "dev": "nodemon src/server.js",
    "start": "node src/server.js",
    "test": "echo \"(add your test runner)\" && exit 0"
  },
  "engines": { "node": ">=18.0.0" }
}
EOF

# src/app.js
cat > src/app.js <<'EOF'
import express from "express";
import helmet from "helmet";
import cors from "cors";
import morgan from "morgan";
import routes from "./routes/index.js";
import errorHandler from "./middlewares/errorHandler.js";

const app = express();
app.use(helmet());
app.use(cors());
app.use(express.json({ limit: "1mb" }));
app.use(morgan("dev"));

app.get("/health", (_, res) => res.json({ status: "ok" }));
app.use("/api/v1", routes);
app.use(errorHandler);

export default app;
EOF

# src/server.js
cat > src/server.js <<'EOF'
import app from "./app.js";
import { PORT } from "./config/env.js";

app.listen(PORT, () => {
  console.log(`API on http://localhost:${PORT}`);
});
EOF

# routes
cat > src/routes/index.js <<'EOF'
import { Router } from "express";
import items from "./items.routes.js";
import users from "./users.routes.js";

const router = Router();
router.use("/items", items);
router.use("/users", users);
export default router;
EOF

cat > src/routes/items.routes.js <<'EOF'
import { Router } from "express";
import * as ctrl from "../controllers/items.controller.js";
import asyncHandler from "../middlewares/asyncHandler.js";

const r = Router();
r.get("/", asyncHandler(ctrl.list));
r.post("/", asyncHandler(ctrl.create));
export default r;
EOF

cat > src/routes/users.routes.js <<'EOF'
import { Router } from "express";
import * as ctrl from "../controllers/users.controller.js";
import asyncHandler from "../middlewares/asyncHandler.js";

const r = Router();
r.get("/", asyncHandler(ctrl.list));
r.post("/", asyncHandler(ctrl.create));
export default r;
EOF

# controllers
cat > src/controllers/items.controller.js <<'EOF'
import * as svc from "../services/items.service.js";

export async function list(req, res) {
  const data = await svc.listItems();
  res.json(data);
}

export async function create(req, res) {
  const item = await svc.createItem(req.body);
  res.status(201).json(item);
}
EOF

cat > src/controllers/users.controller.js <<'EOF'
import * as svc from "../services/users.service.js";

export async function list(req, res) {
  const data = await svc.listUsers();
  res.json(data);
}

export async function create(req, res) {
  const user = await svc.createUser(req.body);
  res.status(201).json(user);
}
EOF

# services (in-memory demo)
cat > src/services/items.service.js <<'EOF'
const db = [];

export async function listItems() {
  return db;
}

export async function createItem(payload) {
  const item = { id: db.length + 1, ...payload };
  db.push(item);
  return item;
}
EOF

cat > src/services/users.service.js <<'EOF'
const db = [];

export async function listUsers() {
  return db;
}

export async function createUser(payload) {
  const user = { id: db.length + 1, ...payload };
  db.push(user);
  return user;
}
EOF

# middlewares
cat > src/middlewares/asyncHandler.js <<'EOF'
export default (fn) => (req, res, next) =>
  Promise.resolve(fn(req, res, next)).catch(next);
EOF

cat > src/middlewares/errorHandler.js <<'EOF'
export default (err, req, res, _next) => {
  const status = err.status || 500;
  const message = err.message || "Internal Error";
  if (status >= 500) console.error(err);
  res.status(status).json({ error: message });
};
EOF

# config
cat > src/config/env.js <<'EOF'
export const PORT = process.env.PORT || 3000;
EOF

# utils (placeholder)
cat > src/utils/logger.js <<'EOF'
export const logger = {
  info: (...a) => console.log(...a),
  error: (...a) => console.error(...a),
};
EOF

# README quickstart
cat > README.md <<'EOF'
# Node API Boilerplate

## Quickstart
1) Copy `.env.example` to `.env` and adjust.
2) Install deps: `npm i` (or `yarn`, `pnpm i`)
3) Dev: `npm run dev`  |  Prod: `npm start`

## Endpoints
- `GET /health`
- `GET /api/v1/items` | `POST /api/v1/items`
- `GET /api/v1/users` | `POST /api/v1/users`
EOF

# --- Install deps ---
if [[ "$PKG" == "yarn" ]]; then
  yarn add express helmet cors morgan dotenv
  yarn add -D nodemon
elif [[ "$PKG" == "pnpm" ]]; then
  pnpm add express helmet cors morgan dotenv
  pnpm add -D nodemon
else
  npm i express helmet cors morgan dotenv
  npm i -D nodemon
fi

echo "Done. Next:
1) cp .env.example .env
2) $PKG run dev
"
