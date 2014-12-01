/* SOLO en este primer script hay que hacer los "IF NOT EXISTS" porque esta primera tabla e indice venia de cuando
   no teniamos migraciones.
  */
CREATE TABLE IF NOT EXISTS optaxml (
id serial PRIMARY KEY,
xml text,
headers text,
created_at timestamp with time zone,
name text,
feed_type text,
game_id text,
competition_id text,
season_id text,
last_updated timestamp with time zone);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM  pg_class c
    JOIN  pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'created_at_index'
    AND   n.nspname = 'public'
  ) THEN CREATE INDEX created_at_index ON public.optaxml (created_at);
  END IF;
END$$;