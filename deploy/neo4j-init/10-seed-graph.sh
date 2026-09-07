#!/bin/sh
# Seed the checked-in Neo4j 4.4 dump exactly once into the named /data volume.
# This script intentionally runs before the Neo4j server process starts.
set -eu

data_dir=/data
marker="$data_dir/.oslab-graph-seeded"
dump=/seed/graph.db.dump
database_dir="$data_dir/databases"
transaction_dir="$data_dir/transactions"

log() {
  printf '%s\n' "[neo4j-seed] $*"
}

if [ -e "$marker" ]; then
  log "persistent marker exists; preserving the current graph"
  exit 0
fi

if [ ! -r "$dump" ]; then
  log "seed dump is missing or unreadable: $dump"
  exit 1
fi

# A prior Neo4j instance may have created system, user, or transaction stores.
# Never let --force replace any of them without an explicit adoption decision.
if { [ -d "$database_dir" ] && [ -n "$(find "$database_dir" -mindepth 1 -maxdepth 1 -type d -print -quit)" ]; } \
  || { [ -d "$transaction_dir" ] && [ -n "$(find "$transaction_dir" -mindepth 1 -maxdepth 1 -type d -print -quit)" ]; }; then
  if [ "${NEO4J_SEED_ACCEPT_EXISTING_DATA:-false}" = "true" ]; then
    log "existing Neo4j data accepted explicitly; writing marker without importing the dump"
    touch "$marker"
    chown 7474:7474 "$marker"
    exit 0
  fi

  log "existing Neo4j data found without a seed marker; refusing to overwrite it"
  log "back it up and set NEO4J_SEED_ACCEPT_EXISTING_DATA=true only to adopt it"
  exit 1
fi

# Docker creates a fresh named volume as root. Prepare it for the account used
# by neo4j:4.4.39-community, then run the admin tool as that account so the
# server can read and write the loaded stores after it starts.
mkdir -p "$data_dir"
chown -R 7474:7474 "$data_dir"

log "loading graph.db.dump into database 'neo4j'"
su -s /bin/sh neo4j -c '/var/lib/neo4j/bin/neo4j-admin load --from=/seed/graph.db.dump --database=neo4j --force'

touch "$marker"
chown 7474:7474 "$marker"
log "graph seed completed; future starts will preserve this volume"
