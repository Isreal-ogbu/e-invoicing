# E-Invoice service

Independent Spring Boot 2.3 / Java 11 microservice registered with Config Server and Eureka (`omnix-e-invoice`).

It reads the daily T24 journal export, identifies included tax remittance credits, builds FIRS self-billed invoice payloads, stores them, optionally submits them, and exposes GET APIs to spool the saved invoices.

## Journal source

- UNC share: `\\192.168.1.39\t24 daily reports\JOURNAL`
- Service reads the share over **SMB** with configured credentials (no OS mount required)
- Config keys / env vars:
  - `e-invoice.journal.smb-host` / `EINVOICE_SMB_HOST` (default `192.168.1.39`)
  - `e-invoice.journal.smb-share` / `EINVOICE_SMB_SHARE` (default `t24 daily reports`)
  - `e-invoice.journal.smb-path` / `EINVOICE_SMB_PATH` (default `JOURNAL`)
  - `e-invoice.journal.smb-username` / `EINVOICE_SMB_USERNAME`
  - `e-invoice.journal.smb-password` / `EINVOICE_SMB_PASSWORD`
  - optional `EINVOICE_SMB_DOMAIN`
- Resolution order: **SMB → local/mounted directory → classpath sample**


## Included tax rules

From *E-Invoicing integration remittance line.xlsx* (only rows marked included):

| Kind | Account / category | Txn code | Rate | Invoice suffix |
|------|--------------------|----------|------|----------------|
| VAT corporate | category `16120` (`NGN16120...`) | `780` credit | 7.5% of matched charge (ROUND_UP to kobo) | `ORI` |
| Stamp duty on loans | account `1103824527` | `258` credit | 0.125% | `LSD` |
| Stamp duty on EMT | category `16048` | `158` credit | flat ₦50, transfer ≥ ₦10,000 | `TSD` |

Matching logic (from the implementation transcript):

1. Find **credit** tax legs on the included account/category + transaction code
2. Group sibling journal rows by normalized FT (`OUR.REF` / `TRANS.REF`, strip trailing `BNK`)
3. For VAT, find the related income/charge (usually code `790`) where `charge * 7.5%` rounds **up** to the VAT amount
4. Build one invoice per tax credit (`FT…ORI`, `FT…LSD`, `FT…TSD`)

## Sample payload shape

Saved request payload matches the agreed FIRS structure, for example:

- `invoiceNumber`: `FT2625981PS5ORI`
- `invoiceTypeCode`: `Self-billed Invoice` (override with FIRS code via `EINVOICE_SELF_BILLED_CODE` when confirmed)
- supplier = Accion MFB static party
- customer = debit customer (T24 when available, journal name fallback)
- `taxTotals` / `legalMonetaryTotal` / `invoiceLines` populated from the tax + taxable charge

## Endpoints

- `GET /api/v1/e-invoices/spool?date=2026-09-16`  
  Read and persist that day's journal rows (idempotent).

- `POST /api/v1/e-invoices/process?date=2026-09-16`  
  Build invoices for the day. Optional:
  - `&ftReference=FT2625981PS5` process one FT only
  - `&dryRun=true` build/save payloads without calling the FIRS API

- `GET /api/v1/e-invoices?date=2026-09-16`  
  List saved invoices for the date. Returns metadata plus parsed `invoice` JSON body.  
  Optional filters: `&status=BUILT` / `SUBMITTED` / `FAILED`, `&ftReference=FT…`

- `GET /api/v1/e-invoices/{invoiceNumber}`  
  Fetch one saved invoice, e.g. `/api/v1/e-invoices/FT2625981PS5ORI`

Scheduler: previous business day at `EINVOICE_SCHEDULE` (default `0 15 2 * * *` Africa/Lagos).

## Configuration

| Variable | Purpose |
|----------|---------|
| `EINVOICE_JOURNAL_DIRECTORY` | Mounted journal folder |
| `TAFJ_URL`, `TAFJ_HTTP_USERNAME`, `TAFJ_HTTP_PASSWORD` | TAFJ HTTP endpoint |
| `TAFJ_USERNAME`, `TAFJ_PASSWORD` | OFS user inside the request body |
| `EINVOICE_API_URL`, `EINVOICE_API_TOKEN` | FIRS/submit endpoint |
| `EINVOICE_SELF_BILLED_CODE` | Invoice type code/description |
| `EINVOICE_SUBMIT_ENABLED` | `false` to only build/store |
| `EINVOICE_T24_ENABLED` | `false` to skip OFS enrichment (local dry run) |
| `EINVOICE_DB_*` | MySQL connection |
| `CONFIG_SERVER_URI` / `EUREKA_URI` | Shared platform config + discovery |

## Local dry-run example

```bash
# with sample journal on classpath or mounted directory
curl -X POST 'http://localhost:2010/api/v1/e-invoices/process?date=2026-09-16&ftReference=FT2625981PS5&dryRun=true'
curl 'http://localhost:2010/api/v1/e-invoices?date=2026-09-16&ftReference=FT2625981PS5'
curl 'http://localhost:2010/api/v1/e-invoices/FT2625981PS5ORI'
```
