# E-Invoice service

Independent Spring Boot 2.3 / Java 11 service registered with Config Server and Eureka.

## Endpoints

- `GET /api/v1/e-invoices/spool?date=2026-09-16` reads and stores that day's journal rows, then returns the spooled entries. It is idempotent by journal date and source row.
- `POST /api/v1/e-invoices/process?date=2026-09-16` runs the configured daily flow. Add `&ftReference=FT2625981PS5` to process only one FT for a controlled replay or investigation.
- `GET /api/v1/e-invoices?date=2026-09-16` lists persisted invoice requests and their processing status.

A scheduler runs the previous day's journal at `EINVOICE_SCHEDULE` (default 02:15 daily in `EINVOICE_TIME_ZONE`, default Africa/Lagos). The processor parses the headerless 24-column CSV, groups rows by `OUR.REF`, matches credit tax entries, pairs VAT with a related code 790 charge when the rounded 7.5% amount agrees, handles loan stamp duty separately, retrieves FT/customer details through TAFJ/OFS, builds the invoice structure from the supplied example, submits it, and stores request/response/status for retry and reconciliation. Multiple tax entries on one FT are combined into one invoice to preserve the FT invoice-number idempotency rule.

Included journal rules taken from the remittance workbook:

- Corporate VAT: account prefix `NGN16120`, transaction code `780`, 7.5% of the matched charge.
- Stamp duty on loans: account `1103824527`, transaction code `258`, 0.125%; original loan amount is taken from the FT enquiry and checked against the tax credit.
- Stamp duty on transactions: account prefix `NGN16048`, transaction code `158`, flat ₦50, with the ₦10,000 threshold checked against the FT enquiry.

## Required deployment configuration

- `EINVOICE_JOURNAL_DIRECTORY`: directory readable by the service. Default filename pattern is `JOURNAL_{date}.csv`, where `{date}` formats as `dd_MMM_yyyy` in uppercase, producing `JOURNAL_16_SEP_2026.csv` for 16 September 2026.
- `TAFJ_URL`, `TAFJ_HTTP_USERNAME`, `TAFJ_HTTP_PASSWORD`: TAFJ HTTP endpoint settings. `TAFJ_USERNAME` and `TAFJ_PASSWORD` are the OFS user credentials placed inside the OFS request. Credentials are not embedded in the service.
- `EINVOICE_API_URL`, `EINVOICE_API_TOKEN`: invoice submission endpoint and bearer token.
- `EINVOICE_SELF_BILLED_CODE`: FIRS code for Self-billed Invoice.
- `EINVOICE_DB_URL`, `EINVOICE_DB_USERNAME`, `EINVOICE_DB_PASSWORD`: database connection. Development uses MySQL with Hibernate `ddl-auto: update`; production should use the organization's migration policy.
- `EINVOICE_JOURNAL_DIRECTORY` and `EINVOICE_SCHEDULE` can also be moved to the Config Server repository for environment-specific values.

The exact FIRS submission URL/authentication/response contract and Self-billed Invoice code were not present in the attached files, so those are explicit configuration gates. The code uses the supplied sample invoice JSON structure and persists the submission response. The customer detail mapping uses T24 OFS fields available from the existing core model; fields absent from the response are left empty.

The share permission for host `192.168.1.52` cannot be granted from this repository. Operations must grant the host access to `\\192.168.1.39\t24 daily reports\JOURNAL` and mount it into the service environment. On Linux, set `EINVOICE_JOURNAL_DIRECTORY` to that mount path.
