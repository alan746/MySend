#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT INT TERM

fake_bin="$work_dir/bin"
command_log="$work_dir/commands.log"
mkdir -p "$fake_bin"

cat > "$fake_bin/pg_dump" <<'EOF'
#!/bin/sh
set -eu
printf 'pg_dump %s\n' "$*" >> "$COMMAND_LOG"
output=
for argument in "$@"; do
    case "$argument" in
        --file=*) output=${argument#--file=} ;;
    esac
done
[ -n "$output" ] || exit 3
printf 'portable-dump' > "$output"
EOF

cat > "$fake_bin/pg_restore" <<'EOF'
#!/bin/sh
set -eu
printf 'pg_restore %s\n' "$*" >> "$COMMAND_LOG"
EOF

cat > "$fake_bin/psql" <<'EOF'
#!/bin/sh
set -eu
printf 'psql %s\n' "$*" >> "$COMMAND_LOG"
case "$*" in
    *current_database*) printf '%s\n' "${PSQL_DATABASE_NAME:-mysend_restore_drill_ci}" ;;
    *to_regclass*) printf '1\n' ;;
esac
EOF

cat > "$fake_bin/aws" <<'EOF'
#!/bin/sh
set -eu
printf 'aws %s\n' "$*" >> "$COMMAND_LOG"
case "$*" in
    *' s3 cp s3://'*) printf 'portable-dump' > "${@: -1}" ;;
esac
EOF

chmod +x "$fake_bin/pg_dump" "$fake_bin/pg_restore" "$fake_bin/psql" "$fake_bin/aws"

export COMMAND_LOG="$command_log"
export PATH="$fake_bin:$PATH"

DATABASE_URL=jdbc:postgresql://source \
AWS_ENDPOINT_URL=https://storage.example \
AWS_ACCESS_KEY_ID=access \
AWS_SECRET_ACCESS_KEY=secret \
AWS_S3_BUCKET_NAME=backup-bucket \
BACKUP_PREFIX=database \
    "$script_dir/backup.sh"

grep -q 'pg_dump .*--format=custom' "$command_log"
grep -q -- '--dbname=postgresql://source' "$command_log"
grep -q 'aws --endpoint-url https://storage.example s3 cp .* s3://backup-bucket/database/mysend-' "$command_log"

: > "$command_log"
SOURCE_DATABASE_URL=jdbc:postgresql://source \
RESTORE_DATABASE_URL=jdbc:postgresql://restore \
    "$script_dir/restore-drill.sh"

grep -q 'pg_restore .*--exit-on-error.*--dbname=postgresql://restore' "$command_log"
grep -q 'to_regclass' "$command_log"

: > "$command_log"
if PSQL_DATABASE_NAME=mysend \
    SOURCE_DATABASE_URL=jdbc:postgresql://source \
    RESTORE_DATABASE_URL=jdbc:postgresql://production \
    "$script_dir/restore-drill.sh"; then
    exit 1
fi

if grep -q '^pg_restore ' "$command_log"; then
    exit 1
fi
