# ${product_name} SSDLC compliance report

This report is available at
<https://d-9067613a84.awsapps.com/start/#/console?account_id=857654397073&role_name=Drivers.User&destination=https%3a%2f%2fus-west-1.console.aws.amazon.com%2fs3%2fobject%2fjava-driver-release-assets%3fregion%3dus-west-1%26bucketType%3dgeneral%26prefix%3d${product_name}%2f${product_version}%2fssdlc_compliance_report.md>.

<table>
  <tr>
    <th>Product name</th>
    <td><a href="https://github.com/mongodb/mongo-hibernate">${product_name}</a></td>
  </tr>
  <tr>
    <th>Product version</th>
    <td>${product_version}</td>
  </tr>
  <tr>
    <th>Release creator</th>
    <td>
        ${product_release_creator}
        <p>
            Refer to data in Papertrail for more details.
            There is currently no official way to serve that data.
        </p>
    </td>
  </tr>
  <tr>
    <th>Report date, UTC</th>
    <td>${report_date_utc}</td>
  </tr>
</table>

## Process document

Blocked on <https://jira.mongodb.org/browse/JAVA-5429>.

The MongoDB SSDLC policy is available at
<https://docs.google.com/document/d/1u0m4Kj2Ny30zU74KoEFCN4L6D_FbEYCaJ3CQdCYXTMc>.

## Third-party dependency information

This product depends on Hibernate ORM and the MongoDB Java driver, and its Spring Boot
starter modules depend on Spring Boot.
Our [SBOM](https://docs.devprod.prod.corp.mongodb.com/mms/python/src/sbom/silkbomb/docs/CYCLONEDX/) lite
is <https://github.com/mongodb/mongo-hibernate/blob/r${product_version}/sbom.json>.

## Static analysis findings

The static analysis findings are available at
<https://d-9067613a84.awsapps.com/start/#/console?account_id=857654397073&role_name=Drivers.User&destination=https%3a%2f%2fus-west-1.console.aws.amazon.com%2fs3%2fbuckets%2fjava-driver-release-assets%3fregion%3dus-west-1%26bucketType%3dgeneral%26prefix%3d${product_name}%2f${product_version}%2fstatic-analysis-reports%2f>.
They are the CodeQL SARIF exports for commit ${analysed_commit_sha}, one for the Java sources
and one for the GitHub Actions workflows.
Each export lists every alert CodeQL reported for that commit.
Alerts that are neither fixed nor open are dismissed in code scanning with a recorded reason,
which is visible at <https://github.com/mongodb/mongo-hibernate/security/code-scanning>.

## Security testing results

The testing results are available at
<${evergreen_build_url}>.

That build runs the unit, integration and smoke test suites against MongoDB replica sets on
every supported server version.

## Signature information

The product artifacts are signed.
The signatures can be verified by following instructions at
<https://github.com/mongodb/mongo-hibernate/releases/tag/r${product_version}>.
