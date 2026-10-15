-- Application data is accessed only through the Spring Boot ownership boundary.
REVOKE ALL ON SCHEMA clearpaper FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA clearpaper FROM PUBLIC;
DO $$
DECLARE browser_role text;
BEGIN
  FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
      EXECUTE format('REVOKE ALL ON SCHEMA clearpaper FROM %I',browser_role);
      EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA clearpaper FROM %I',browser_role);
    END IF;
  END LOOP;
END $$;
