\set ON_ERROR_STOP on
DO $$
BEGIN
    IF current_setting('app.allow_destructive', true) IS DISTINCT FROM 'true' THEN
        RAISE EXCEPTION 'Destructive script requires SET app.allow_destructive = true';
    END IF;
END
$$;
DROP TABLE IF EXISTS receipt_item;
DROP TABLE IF EXISTS receipt;
DROP TABLE IF EXISTS ingredient_list;
DROP TABLE IF EXISTS item;
DROP TABLE IF EXISTS inventory;
DROP TABLE IF EXISTS user_timetable;
DROP TABLE IF EXISTS app_user;
DROP TYPE IF EXISTS user_perm;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS location;
