# Frontend and Backend Layout

The JavaFX application is split into two top-level parts:

```text
src/frontend/  JavaFX app, controllers, components, models, FXML, and CSS
src/backend/   database configuration, connection handling, and database models
```

The frontend owns JavaFX screens and user interaction. The backend owns the PostgreSQL connection boundary and database-facing records. `Run-Gui.sh` compiles both parts and launches `frontend.app.DatabaseApp`.

### Database configuration

Set these environment variables before launching the application. The values are required; the application does not provide database defaults.

```bash
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="your_database"
export DB_USER="your_user"
export DB_PASSWORD="your_password"
./Run-Gui.sh
```

Do not commit local `.env` files or passwords. The launcher does not load `.env` files automatically.

### GUI Design
- Both cashier and manager GUI have buttons to switch view to the other.
- Cashier GUI allows the user to add/remove items to the shopping cart and checkout.
- Checking out the shopping cart updates the database.
- Manager GUI allows the user to view a bar graph of sales and most recent receipts.
- Manager GUI allows the user to increase/decrease the stock of inventory items which updates the database.
- Manager GUI allows the user to increase/decrease the price of menu items as well as add/remove menu items which also updates the database.
- From Phase 0, GUI designs have changed slightly which includes adding buttons and a new tab for manager. More details in CHANGELOG.md.

### Running the Code

From the project root, set the required variables and run:

```bash
JAVAFX_LIB=/path/to/javafx-sdk-25/lib ./Run-Gui.sh
```

The launcher compiles every Java source under `src/`, copies the FXML/CSS
resources, and starts `frontend.app.DatabaseApp`. The PostgreSQL driver is
loaded from `JavaFXGUI/postgresql-42.2.8.jar`.

The legacy Swing GUI can be compiled from the project root with the same
driver and backend connection class:

```bash
mkdir -p /tmp/csce331-legacy-classes
javac -cp JavaFXGUI/postgresql-42.2.8.jar -d /tmp/csce331-legacy-classes \
  src/backend/config/DbSetup.java \
  src/backend/database/DatabaseConnection.java \
  GUI/jdbcGUI.java
java -cp /tmp/csce331-legacy-classes:JavaFXGUI/postgresql-42.2.8.jar JdbcGUI
```

The legacy GUI also requires the five database environment variables above.
