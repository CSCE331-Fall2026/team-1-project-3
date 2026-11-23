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

**Script Prereqs:** To run the generation script, you will need openssl. To install, use the following:
```bash
sudo apt update
sudo apt install openssl libssl-dev
```
For windows, instead use winget:
```bash
winget install ShiningLight.OpenSSL.Dev
```

Also, for the password to hash the dummy data passwords, please get the magic numbers (ex: 123 213 132 ...) and put them in set-env.sh using the following line, replacing the ... with the numbers.
```bash
export GEN_KEY="..."
```

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

## Database Setup

Scripts are in `src/sql-scripts/sql/`. Run them with `psql` (`source set-env.sh` first):
```bash
export PGHOST=$DB_HOST PGPORT=$DB_PORT PGDATABASE=$DB_NAME PGUSER=$DB_USER PGPASSWORD=$DB_PASSWORD
```

**New, empty database.** `DesignSql.sql` **drops every table**, so never run it on a database whose data you want to keep.
```bash
psql -v ON_ERROR_STOP=1 -f src/sql-scripts/sql/DesignSql.sql
cd src/sql-scripts/generator && make && ./generate && cd ../../..   # writes sql/Script.sql (seed data)
psql -v ON_ERROR_STOP=1 -f src/sql-scripts/sql/Script.sql
```
Seeded receipts are history, so the generator writes them as `COMPLETED`; new checkouts get the column default, `PENDING`. `./regen-data.sh` runs these same steps (plus `AddReceiptStatus.sql`, a no-op here) and is just as destructive.

**Existing database.** Apply the upgrade if the database was created before the kitchen queue (issue #45), or with an older `DesignSql.sql` whose `receipt.status` defaulted to `COMPLETED` (then every checkout skips the kitchen queue). It keeps all data and makes new checkouts default to `PENDING`. A database without `receipt.status` gets it as an `order_status` enum column, with every existing receipt `COMPLETED`. A database that already has the earlier `VARCHAR` status column keeps it and its `chk_receipt_status` CHECK constraint; only the default changes. The kitchen API works with either column type. It runs in one transaction and is safe to rerun: a second run leaves every receipt unchanged.
```bash
psql -v ON_ERROR_STOP=1 -f src/sql-scripts/sql/AddReceiptStatus.sql
```

**Verify** (either path):
```bash
psql -c "\d receipt"   # status | order_status | not null | 'PENDING'::order_status; index idx_receipt_pending
                       # (earlier VARCHAR column: character varying(16), default 'PENDING', chk_receipt_status)
psql -c "SELECT status, COUNT(*) FROM receipt GROUP BY status"
```
Expected counts from the second query:
- Fresh database from `DesignSql.sql` only, with no seed data imported: no rows, because there are no receipts yet.
- After importing the generated historical receipts (`Script.sql`): only `COMPLETED`.
- Right after running `AddReceiptStatus.sql` on an existing populated database: only `COMPLETED`.
- Each new checkout after that adds a `PENDING` receipt.

**Then deploy.** Apply the upgrade *before* the app version with `/api/kitchen/orders` goes live (before merging if Render auto-deploys `main`), otherwise those endpoints return 500 until it runs. The previous app version keeps working on the upgraded schema. The app never changes the schema on startup.

**Database tests** (`KitchenOrdersTest`, `ReceiptStatusSchemaTest`, `DatabaseConnectionTest`) run only when `TEST_DB_URL` is set, and they **drop or wipe every table**, so point it at a throwaway local database (`localhost`/`127.0.0.1` only); otherwise they are skipped. Load the schema into it first, since some of these tests only truncate tables:
```bash
psql -v ON_ERROR_STOP=1 -h 127.0.0.1 -d scratch_db -f src/sql-scripts/sql/DesignSql.sql
TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/scratch_db TEST_DB_USER=... TEST_DB_PASSWORD=... ./mvnw test
```

## Deploying

Render **Web Service** → connect this repo → Runtime **Docker** (builds the root `Dockerfile`). Under Environment, set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`. Render sets `PORT` itself.

To test the image locally:
```bash
docker build -t pos . && docker run --env-file .env -e PORT=8080 -p 8080:8080 pos
```
`.env` takes plain `KEY=VALUE` lines (no `export`), unlike `set-env.sh`.

The Project 2 JavaFX app is preserved at the `sprint0` tag.
