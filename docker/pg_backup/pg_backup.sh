echo "Backup Started"
/usr/bin/pg_dump -U "$POSTGRES_USERNAME" -Fc -d postgres -h "$DB_ADDR" -f "/shared/pg-backup-`date +%Y.%m.%d-%T.backup`"
echo "Backup Performed"
