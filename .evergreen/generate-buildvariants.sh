#!/bin/bash

set -o errexit  # Exit the script with error if any of the commands fail

os=rhel80-small
declare -a versions=("6.0" "7.0" "8.0")
declare -a topologies=("server" "replica_set" "sharded_cluster")

cat <<HEAD > buildvariants.yml
buildvariants:

  - name: static-checker
    tags:
      - ci
    display_name: Static Check
    run_on: $os
    tasks:
      - name: .static-check

HEAD

for version in "${versions[@]}"; do
  for topology in "${topologies[@]}"; do
    case $topology in
    server)
      topology_name=standalone
      ;;
    replica_set)
      topology_name=replicaset
      ;;
    sharded_cluster)
      topology_name=sharded-cluster
      ;;
    *)
      echo -n "unexpected topology: $topology"
      ;;
    esac
cat <<VARIANT >> buildvariants.yml
  - name: mongodb_v${version}_${topology_name}
    tags:
      - ci
      - "$version"
      - $topology_name
    display_name: MongoDB v$version ($topology_name)
    expansions:
      MONGODB_VERSION: $version
      TOPOLOGY: $topology
    run_on: $os
    tasks:
      - name: .test

VARIANT
  done
done
