echo "Backup Started"
export PGPASSWORD="$PG_PASSWORD"
/usr/bin/pg_dump -U "$POSTGRES_USERNAME" -Fc -h "$DB_PORT_5432_TCP_ADDR" -f "/shared/pg-backup-`date +%Y.%m.%d-%T.backup`"
echo "Backup Performed"
