#!/bin/sh
set -eu

[ -n "${DATABASE_URL:-}" ] || { printf '%s\n' 'DATABASE_URL is required' >&2; exit 2; }
[ -n "${AWS_ENDPOINT_URL:-}" ] || { printf '%s\n' 'AWS_ENDPOINT_URL is required' >&2; exit 2; }
[ -n "${AWS_ACCESS_KEY_ID:-}" ] || { printf '%s\n' 'AWS_ACCESS_KEY_ID is required' >&2; exit 2; }
[ -n "${AWS_SECRET_ACCESS_KEY:-}" ] || { printf '%s\n' 'AWS_SECRET_ACCESS_KEY is required' >&2; exit 2; }
[ -n "${AWS_S3_BUCKET_NAME:-}" ] || { printf '%s\n' 'AWS_S3_BUCKET_NAME is required' >&2; exit 2; }

export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-auto}
database_url=${DATABASE_URL#jdbc:}
backup_prefix=${BACKUP_PREFIX:-database}
backup_prefix=${backup_prefix#/}
backup_prefix=${backup_prefix%/}
[ -n "$backup_prefix" ] || { printf '%s\n' 'BACKUP_PREFIX cannot be empty' >&2; exit 2; }

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT INT TERM

dump_path="$work_dir/mysend-$timestamp.dump"
object_uri="s3://$AWS_S3_BUCKET_NAME/$backup_prefix/mysend-$timestamp.dump"

pg_dump \
    --dbname="$database_url" \
    --format=custom \
    --compress=9 \
    --no-owner \
    --no-acl \
    --file="$dump_path"

aws --endpoint-url "$AWS_ENDPOINT_URL" s3 cp "$dump_path" "$object_uri" --only-show-errors
printf '%s\n' "$object_uri"
