psql -v ON_ERROR_STOP=1 -f src/sql-scripts/sql/DesignSql.sql
cd src/sql-scripts/generator && make && ./generate
psql -v ON_ERROR_STOP=1 -f ../sql/Script.sql
make clean
cd ../../..