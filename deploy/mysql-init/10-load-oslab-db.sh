#!/bin/sh
set -eu

# The supplied dump does not contain USE oslab_db. MySQL's entrypoint creates
# MYSQL_DATABASE first, then this script imports the dump into that database.
mysql --protocol=socket -uroot "-p${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < /seed/oslab_db.sql
