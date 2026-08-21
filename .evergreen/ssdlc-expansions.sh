#!/usr/bin/env bash

set -o xtrace   # Write all commands first to stderr
set -o errexit  # Exit the script with error if any of the commands fail

############################################
#            Main Program                  #
############################################

PRODUCT_NAME="${PRODUCT_NAME:?must be passed in from the calling function}"

# Release tags are `r<version>`, stripped below.
PRODUCT_VERSION="$(git describe --tags --always --dirty)"

cat <<EOT >ssdlc-expansions.yml
product_name: "${PRODUCT_NAME}"
product_version: "${PRODUCT_VERSION#r}"
EOT

cat ssdlc-expansions.yml
