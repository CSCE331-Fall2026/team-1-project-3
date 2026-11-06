# Team 1 — Panda Express POS Web App

A publicly hosted web application for Panda Express that allows customers to order from the menu, kitchen staff to see live inventory, servers to efficiently record and view customer orders, and managers to view reports.

## Live Links

- **Live Deployed app:** https://team-1-pos.onrender.com/
- **Local Deployed app:** http://localhost:3000/
- **Documentation:** https://team-1-project-3.ivin-98.workers.dev/
- **Project board (backlog + burn-up):** https://github.com/orgs/CSCE331-Fall2026/projects/6/views/1

## Roles
| Team Member    | Role                           |
| -------------- | ------------------------------ |
| Ashley Hoang   | Front-end Lead                 |
| Sharaf Mehmood | Front-end Lead                 |
| Pablo Almanza  | Back-end Lead                  |
| Colby Ideker   | Back-end Lead                  |
| Samuel Just    | Project Manager & Full-stack   |

## Novel Feature
**What it is**: Recommended items based off popularity (for customers), recommended ingredients to stock more/less based off sales (for manager)
**Motivation**: Sometimes customers may not know what to order, a recommendation could assist them, for the customer side of things. On the other hand, for ingredient stocking recommendations, managers may lose money by buying too much of ingredients that aren’t used, or not enough of ingredients in high demand, so reports telling them what to stock more/less can be beneficial to optimizing profits. [1]

## Tech Stack
- **Front-end:** React
- **Back-end:** Java/JDBC
- **Database:** PostgreSQL on AWS
- **Hosting service:** Render (app), Cloudflare (docs)
- **Authentication:** Google Authenticator



## Running Locally

**Prerequisites:** JDK 17+ and Node.js 18+. Maven is not needed; use the included `./mvnw` wrapper.

1. Create `set-env.sh` in the repo root (it's gitignored, so never commit credentials):
   ```bash
   export DB_HOST=...
   export DB_PORT=5432
   export DB_NAME=...
   export DB_USER=...
   export DB_PASSWORD=...
   ```
2. Run the whole app (API + site) at http://localhost:3001. This also builds the React client:
   ```bash
   source set-env.sh && ./mvnw spring-boot:run
   ```

**Optional, for UI work:** while step 2 is running, start the React dev server in a second terminal for hot reload at http://localhost:5173 (`/api` is proxied to :3001):
```bash
cd client && npm run dev
```

## Deploying

Render **Web Service** → connect this repo → Runtime **Docker** (builds the root `Dockerfile`). Under Environment, set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`. Render sets `PORT` itself.

To test the image locally:
```bash
docker build -t pos . && docker run --env-file .env -e PORT=8080 -p 8080:8080 pos
```
`.env` takes plain `KEY=VALUE` lines (no `export`), unlike `set-env.sh`.
