#!/usr/bin/env bash

set -eu

# Supported/used environment variables:
# PRODUCT_NAME
# PRODUCT_VERSION
# EVERGREEN_VERSION_ID

if [ -z "${PRODUCT_NAME}" ]; then
    printf "\nPRODUCT_NAME must be set to a non-empty string\n"
    exit 1
fi
if [ -z "${PRODUCT_VERSION}" ]; then
    printf "\nPRODUCT_VERSION must be set to a non-empty string\n"
    exit 1
fi
if [ -z "${EVERGREEN_VERSION_ID}" ]; then
    printf "\nEVERGREEN_VERSION_ID must be set to a non-empty string\n"
    exit 1
fi

############################################
#            Main Program                  #
############################################
RELATIVE_DIR_PATH="$(dirname "${BASH_SOURCE[0]:-$0}")"

printf "\nCreating SSDLC reports\n"
printf "\nProduct name: %s\n" "${PRODUCT_NAME}"
printf "\nProduct version: %s\n" "${PRODUCT_VERSION}"

declare -r SSDLC_PATH="${RELATIVE_DIR_PATH}/../build/ssdlc"
mkdir -p "${SSDLC_PATH}"

declare -r EVERGREEN_PROJECT_NAME_PREFIX="${PRODUCT_NAME//-/_}"
declare -r EVERGREEN_BUILD_URL_PREFIX="https://spruce.mongodb.com/version"
declare -r GIT_TAG="r${PRODUCT_VERSION}"
GIT_COMMIT_HASH="$(git rev-list --ignore-missing -n 1 "${GIT_TAG}")"
set +e
    GIT_BRANCH_DEFAULT="$(git branch -a --contains "${GIT_TAG}" 2>/dev/null | grep 'main$')"
    GIT_BRANCH_PATCH="$(git branch -a --contains "${GIT_TAG}" 2>/dev/null | grep '\.x$')"
set -e
# On a snapshot PRODUCT_VERSION is `git describe` output, which git resolves as a rev, so the
# tag-based branches below cannot tell it apart from a release. Snapshots are matched first.
if [[ "${PRODUCT_NAME}" == *'-snapshot' ]]; then
    declare -r EVERGREEN_BUILD_URL="${EVERGREEN_BUILD_URL_PREFIX}/${EVERGREEN_VERSION_ID}"
elif [ -n "${GIT_BRANCH_DEFAULT}" ]; then
    declare -r EVERGREEN_BUILD_URL="${EVERGREEN_BUILD_URL_PREFIX}/${EVERGREEN_PROJECT_NAME_PREFIX}_${GIT_COMMIT_HASH}"
elif [ -n "${GIT_BRANCH_PATCH}" ]; then
    # A maintenance-branch project is named A.B, without the patch version.
    declare -r EVERGREEN_PROJECT_NAME_SUFFIX="${PRODUCT_VERSION%.*}"
    declare -r EVERGREEN_BUILD_URL="${EVERGREEN_BUILD_URL_PREFIX}/${EVERGREEN_PROJECT_NAME_PREFIX}_${EVERGREEN_PROJECT_NAME_SUFFIX}_${GIT_COMMIT_HASH}"
else
    printf "\nFailed to compute EVERGREEN_BUILD_URL\n"
    exit 1
fi
printf "\nEvergreen build URL: %s\n" "${EVERGREEN_BUILD_URL}"

ANALYSED_COMMIT_SHA="$(git rev-parse HEAD)"
printf "\nAnalysed commit: %s\n" "${ANALYSED_COMMIT_SHA}"

# HEAD is the commit being published, and on a release it is the tagged commit, so this is the
# author of the release commit.
PRODUCT_RELEASE_CREATOR="$(git log -1 --pretty='format:%aN' "${ANALYSED_COMMIT_SHA}")"
printf "\nProduct release creator: %s\n" "${PRODUCT_RELEASE_CREATOR}"
if [ -z "${PRODUCT_RELEASE_CREATOR}" ]; then
    printf "\nFailed to determine the product release creator\n"
    exit 1
fi

printf "\nCreating SSDLC compliance report\n"
declare -r TEMPLATE_SSDLC_REPORT_PATH="${RELATIVE_DIR_PATH}/template_ssdlc_compliance_report.md"
declare -r SSDLC_REPORT_PATH="${SSDLC_PATH}/ssdlc_compliance_report.md"
cp "${TEMPLATE_SSDLC_REPORT_PATH}" "${SSDLC_REPORT_PATH}"
declare -a SED_EDIT_IN_PLACE_OPTION
if [[ "$OSTYPE" == "darwin"* ]]; then
    SED_EDIT_IN_PLACE_OPTION=(-i '')
else
    SED_EDIT_IN_PLACE_OPTION=(-i)
fi
sed "${SED_EDIT_IN_PLACE_OPTION[@]}" \
    -e "s/\${product_name}/${PRODUCT_NAME}/g" \
    -e "s/\${product_version}/${PRODUCT_VERSION}/g" \
    -e "s/\${report_date_utc}/$(date -u +%Y-%m-%d)/g" \
    -e "s/\${product_release_creator}/${PRODUCT_RELEASE_CREATOR}/g" \
    -e "s/\${analysed_commit_sha}/${ANALYSED_COMMIT_SHA}/g" \
    -e "s>\${evergreen_build_url}>${EVERGREEN_BUILD_URL}>g" \
    "${SSDLC_REPORT_PATH}"
if grep -q '\${' "${SSDLC_REPORT_PATH}"; then
    printf "\nERROR: unsubstituted placeholders remain in %s\n" "${SSDLC_REPORT_PATH}"
    grep -n '\${' "${SSDLC_REPORT_PATH}"
    exit 1
fi
printf "%s\n" "${SSDLC_REPORT_PATH}"

printf "\n"
