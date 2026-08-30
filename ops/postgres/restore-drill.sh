#!/bin/sh
set -eu

[ -n "${RESTORE_DATABASE_URL:-}" ] || { printf '%s\n' 'RESTORE_DATABASE_URL is required' >&2; exit 2; }
restore_database_url=${RESTORE_DATABASE_URL#jdbc:}

database_name=$(psql "$restore_database_url" \
    --no-psqlrc \
    --tuples-only \
    --no-align \
    --command='select current_database()')
database_name=$(printf '%s' "$database_name" | tr -d '[:space:]')
database_prefix=${RESTORE_DATABASE_PREFIX:-mysend_restore_drill_}

case "$database_name" in
    "$database_prefix"*) ;;
    *)
        printf 'Refusing to restore into database %s\n' "$database_name" >&2
        exit 2
        ;;
esac

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT INT TERM
dump_path="$work_dir/restore.dump"

if [ -n "${BACKUP_FILE:-}" ]; then
    [ -f "$BACKUP_FILE" ] || { printf '%s\n' 'BACKUP_FILE does not exist' >&2; exit 2; }
    dump_path=$BACKUP_FILE
elif [ -n "${BACKUP_URI:-}" ]; then
    [ -n "${AWS_ENDPOINT_URL:-}" ] || { printf '%s\n' 'AWS_ENDPOINT_URL is required' >&2; exit 2; }
    [ -n "${AWS_ACCESS_KEY_ID:-}" ] || { printf '%s\n' 'AWS_ACCESS_KEY_ID is required' >&2; exit 2; }
    [ -n "${AWS_SECRET_ACCESS_KEY:-}" ] || { printf '%s\n' 'AWS_SECRET_ACCESS_KEY is required' >&2; exit 2; }
    export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-auto}
    aws --endpoint-url "$AWS_ENDPOINT_URL" s3 cp "$BACKUP_URI" "$dump_path" --only-show-errors
elif [ -n "${SOURCE_DATABASE_URL:-}" ]; then
    source_database_url=${SOURCE_DATABASE_URL#jdbc:}
    pg_dump \
        --dbname="$source_database_url" \
        --format=custom \
        --compress=9 \
        --no-owner \
        --no-acl \
        --file="$dump_path"
else
    printf '%s\n' 'BACKUP_FILE, BACKUP_URI, or SOURCE_DATABASE_URL is required' >&2
    exit 2
fi

pg_restore \
    --exit-on-error \
    --clean \
    --if-exists \
    --no-owner \
    --no-acl \
    --dbname="$restore_database_url" \
    "$dump_path"

required_tables=${RESTORE_REQUIRED_TABLES:-'accounts rooms room_files storage_deletions'}
for table_name in $required_tables; do
    case "$table_name" in
        *[!a-zA-Z0-9_]*)
            printf 'Invalid required table name %s\n' "$table_name" >&2
            exit 2
            ;;
    esac

    table_exists=$(psql "$restore_database_url" \
        --no-psqlrc \
        --tuples-only \
        --no-align \
        --command="select case when to_regclass('public.$table_name') is null then 0 else 1 end")
    table_exists=$(printf '%s' "$table_exists" | tr -d '[:space:]')
    [ "$table_exists" = 1 ] || { printf 'Required table %s was not restored\n' "$table_name" >&2; exit 1; }
done

printf 'Restore drill completed for %s\n' "$database_name"
