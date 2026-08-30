FROM postgres:17-alpine

RUN apk add --no-cache aws-cli

COPY backup.sh /usr/local/bin/mysend-backup
COPY restore-drill.sh /usr/local/bin/mysend-restore-drill
RUN chmod +x /usr/local/bin/mysend-backup /usr/local/bin/mysend-restore-drill

CMD ["/usr/local/bin/mysend-backup"]
