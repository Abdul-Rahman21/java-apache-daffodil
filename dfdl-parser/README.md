# dfdl-parser — Technical Documentation

Spring Boot REST service that accepts an **IBM ACE / EDIFACT binary** (`.bin`), parses it with **Apache Daffodil** using DFDL XSD schemas, maps the result into a **seat-map request JSON**, and returns that JSON in the API response. It also **unparses** seat-map **response** JSON back to an SMPRES `.bin`.

Use **`POST /process`** for the full pipeline: parse request `.bin` → call external seat-map API → unparse response → SMPRES `.bin`.

For a full design of parse/unparse (classes, schemas, field maps, edge cases, sample files), see **[TECHNICAL_DESIGN.md](./TECHNICAL_DESIGN.md)**.

---

## 1. End-to-end request flow

```
Client (Postman / curl / upstream system)
        │
        │  POST /parse
        │  Content-Type: multipart/form-data
        │  field "file" = Request_SMPREQ_1.bin
        ▼
┌───────────────────────────────────────────────────────────┐
│ 1. INCOMING REQUEST                                       │
│    ParserController receives MultipartFile                │
└─────────────────────────────┬─────────────────────────────┘
                              ▼
┌───────────────────────────────────────────────────────────┐
│ 2. PARSE WITH APACHE DAFFODIL                             │
│    DaffodilParserService uses compiled DataProcessor      │
│    Schema: /app/schema/CYO_SMPREQ.xsd (+ includes)        │
│    Encoding: IBM037 (EBCDIC)                              │
│    Output: XML Infoset (UNB, UNH, ORG, TVL, UNT, UNZ…)    │
└─────────────────────────────┬─────────────────────────────┘
                              ▼
┌───────────────────────────────────────────────────────────┐
│ 3. XML → JSON INFOSET                                     │
│    XmlJsonConverter (Jackson) converts infoset to JSON    │
└─────────────────────────────┬─────────────────────────────┘
                              ▼
┌───────────────────────────────────────────────────────────┐
│ 4. BUSINESS MAPPING                                       │
│    SeatMapRequestMapper maps EDIFACT fields → seat-map    │
│    JSON (same shape as Request_seatmaprequest_2.txt)      │
└─────────────────────────────┬─────────────────────────────┘
                              ▼
┌───────────────────────────────────────────────────────────┐
│ 5. API RESPONSE                                           │
│    HTTP 200 + ParseResponse JSON                          │
│    json = mapped seat-map payload                         │
└───────────────────────────────────────────────────────────┘
```

---

## 2. How the incoming request comes in

### Endpoint

| Item | Value |
|------|--------|
| **URL** | `http://localhost:8080/parse` |
| **Method** | `POST` |
| **Content-Type** | `multipart/form-data` |
| **Form field** | `file` (required) — the binary payload |

### Example (curl)

```bash
curl -X POST http://localhost:8080/parse \
  -F "file=@./samples/Request_SMPREQ_1.bin;type=application/octet-stream"
```

### Example (Postman)

1. Method: **POST**
2. URL: `http://localhost:8080/parse`
3. Body → **form-data**
4. Key: `file` (type **File**)
5. Value: select your `.bin` file
6. Send

### Alternate: parse a file already on the server

```bash
curl -X POST http://localhost:8080/parse/sample/Request_SMPREQ_1.bin
```

This reads the file from `/app/samples` (Docker volume `./samples`).

### What the controller does

Class: `ParserController`

1. Logs request received (filename, size).
2. Rejects empty uploads with HTTP 400.
3. Passes raw bytes to `DaffodilParserService.parse(byte[])`.

---

## 3. What the application does next (processing)

### Step A — Startup (once)

On application start (`DaffodilParserService`):

1. Reads schema path from config: `daffodil.schema` → `/app/schema/CYO_SMPREQ.xsd`
2. Compiles the DFDL schema with Apache Daffodil Java API
3. Stores a singleton `DataProcessor` (thread-safe, reused for all requests)
4. `/health` reports `schemaCompiled: true` when this succeeds

Schemas are **not** inside the JAR. They are loaded from the mounted `./schemas` folder.

### Step B — Parse binary with Daffodil

For each `/parse` call:

1. Reuses the compiled `DataProcessor` (no recompile per request)
2. Feeds the binary into Daffodil
3. Produces an **XML infoset** describing the EDIFACT SMPREQ message, for example:
   - `UNB` — interchange header
   - `UNH` — message header (`SMPREQ`)
   - `ORG` — organization / channel / currency
   - `TVL` — travel segment (airports, date, flight)
   - `UNT` / `UNZ` — trailers

### Step C — Convert XML infoset to JSON

`XmlJsonConverter` uses Jackson XML → JSON so the infoset can be mapped in code.

### Step D — Map to seat-map request JSON

`SeatMapRequestMapper` transforms DFDL fields into the downstream format
(same structure as `samples/Request_seatmaprequest_2.txt`):

| Seat-map JSON field | Source (DFDL / config) |
|---------------------|-------------------------|
| `ChannelId` | Config default (`4101`) |
| `ChannelName` | `ORG.C336a_13.1.EL9906_CompanyId` (e.g. `1A`) |
| `CurrencyCode` | `ORG.C354.EL6345_CurrentcyCode` (e.g. `USD`) |
| `RecordLocator` | Empty string (reserved) |
| `ProductCode` | Empty string (reserved) |
| `FlightSegments[].DepartureAirport.IataCode` | `TVL.C328a.EL3225_LocationId` |
| `FlightSegments[].ArrivalAirport.IataCode` | `TVL.C328b.EL3225_LocationId` |
| `FlightSegments[].DepartureDateTime` | `TVL.C310.EL9916_FirstDate` (DDMMYY → `yyyy-MM-dd`) |
| `FlightSegments[].MarketingAirlineCode` | `TVL.C306.EL9906_CompanyId` |
| `FlightSegments[].OperatingAirlineCode` | Same as marketing (when not separate) |
| `FlightSegments[].FlightNumber` | `TVL.C308.EL9908_ProductId` (number) |
| `FlightSegments[].OperatingFlightNumber` | Same as flight number |
| `FlightSegments[].ClassOfService` | Config default (`Y`) |
| `FlightSegments[].Pricing` | Config default (`true`) |

---

## 4. Full pipeline — bin → seatmap → bin (`POST /process`)

```
Client
  │  POST /process  (multipart field "file" = Request_SMPREQ_1.bin)
  ▼
Parse (internal)          → seat-map request JSON
  ▼
POST {seat-map-api-url}   → seat-map response JSON
  (default http://localhost:9000/api/seatmap)
  ▼
Unparse (internal)        → SMPRES.bin (EBCDIC IBM037)
  ▼
HTTP 200  application/octet-stream
```

`/parse` and `/unparse` stay available as separate steps. `/process` runs them plus the seat-map call in one request.

### Call

```bash
curl -X POST http://localhost:8080/process \
  -F "file=@./samples/Request_SMPREQ_1.bin;type=application/octet-stream" \
  --output SMPRES.bin
```

Or from a sample already in the container:

```bash
curl -X POST http://localhost:8080/process/sample/Request_SMPREQ_1.bin \
  --output SMPRES.bin
```

### Config

| Property / env | Default | Notes |
|----------------|---------|--------|
| `daffodil.seat-map-api-url` / `SEATMAP_API_URL` | `http://localhost:9000/api/seatmap` | In Docker Compose set to `http://host.docker.internal:9000/api/seatmap` so the container can reach the API on your host |

Require the seat-map service on port **9000** to be running before calling `/process`.

---

## 5. Reverse flow — JSON → binary (`POST /unparse`)

```
Client
  │  POST /unparse
  │  Content-Type: application/json
  │  body = Respone_seatmapresponse_3.txt shape
  ▼
SeatMapResponseMapper  →  SMPRES XML infoset
  ▼
Apache Daffodil unparse (CYO_SMPRES.xsd, **EBCDIC IBM037**)
  ▼
HTTP 200  application/octet-stream  (SMPRES.bin)
```

### Call

```bash
curl -X POST http://localhost:8080/unparse \
  -H "Content-Type: application/json" \
  --data-binary @./samples/Respone_seatmapresponse_3.txt \
  --output SMPRES.bin
```

### Mapping (high level)

Aligned with client sample `samples/Response_SMPRES_4.bin` (no `ORG`):

| Seat-map JSON | SMPRES segment / field |
|---------------|-------------------------|
| `transactionIdentifiers.channelName` | `UNB` recipient (sender fixed `UA1SM`) |
| `flightInfo.*` | `TVL` (airports, date, flight `:L`) |
| `aircraftInfo.icr` | `EQI++++++{icr}` |
| `cabins[]` (filtered by `cabinCode`) | `CBD` + `ROD` (incl. `Z` / exit `E` rows) |

Details: [TECHNICAL_DESIGN.md §5](./TECHNICAL_DESIGN.md#5-unparse-flow-json--binary).

---


### How the response is returned (parse)

### Success — HTTP 200

```json
{
  "success": true,
  "xml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<SMPREQ>...</SMPREQ>",
  "json": {
    "ChannelId": "4101",
    "ChannelName": "1A",
    "CurrencyCode": "USD",
    "RecordLocator": "",
    "ProductCode": "",
    "FlightSegments": [
      {
        "DepartureAirport": { "IataCode": "LAX" },
        "ArrivalAirport": { "IataCode": "DEN" },
        "DepartureDateTime": "2026-12-21",
        "MarketingAirlineCode": "UA",
        "OperatingAirlineCode": "UA",
        "FlightNumber": 1275,
        "OperatingFlightNumber": 1275,
        "ClassOfService": "Y",
        "Pricing": "true"
      }
    ]
  },
  "infoset": { "...": "raw DFDL JSON infoset (UNB/UNH/ORG/TVL/...)" }
}
```

| Field | Meaning |
|-------|---------|
| `success` | `true` when parse + mapping completed |
| `json` | **Mapped seat-map payload** — this is what you send downstream |
| `xml` | Raw Daffodil XML infoset (debug / audit) |
| `infoset` | Raw DFDL JSON before business mapping (debug) |

### Failure — HTTP 400 (parse / invalid binary)

```json
{
  "success": false,
  "error": "DFDL parse failed. Diagnostics: ..."
}
```

### Failure — HTTP 500 (schema not compiled)

```json
{
  "success": false,
  "errorCode": "SCHEMA_ERROR",
  "error": "DFDL schema is not compiled. Check startup logs and schema path: /app/schema/CYO_SMPREQ.xsd",
  "exceptionType": "com.example.dfdl.exception.DfdlSchemaException"
}
```

Check readiness first:

```bash
curl http://localhost:8080/health
```

Expected when ready:

```json
{
  "status": "UP",
  "schemaCompiled": true,
  "schema": "CYO_SMPREQ.xsd"
}
```

---

## 6. Components involved

| Layer | Class | Responsibility |
|-------|--------|----------------|
| API | `ParserController` | Accepts multipart binary, returns `ParseResponse` |
| API | `ProcessController` | Full pipeline: parse → seatmap API → unparse → `.bin` |
| API | `UnparseController` | Seat-map response JSON → SMPRES `.bin` |
| Parse | `DaffodilParserService` | Compiles schema once; parses binary with Daffodil |
| Pipeline | `SeatMapPipelineService` / `SeatMapApiClient` | Orchestrates external seat-map call |
| Convert | `XmlJsonConverter` | XML infoset → JSON |
| Map | `SeatMapRequestMapper` | DFDL JSON → seat-map request JSON |
| Map | `SeatMapResponseMapper` | Seat-map response JSON → SMPRES XML |
| DTO | `ParseResponse` / `SeatMapRequest` | Response and mapped payload shapes |
| Errors | `GlobalExceptionHandler` | Structured JSON errors |
| Config | `application.yml` / `DaffodilConfiguration` | Schema path, channel defaults, seatmap URL |

---

## 7. Running the application

### Docker (recommended)

Host needs **only Docker Desktop** (no Java/Maven on the host).

```bash
cd dfdl-parser
docker compose up --build -d
```

API base URL: [http://127.0.0.1:8080](http://127.0.0.1:8080)

| Host folder | Container path | Live without rebuild? |
|-------------|----------------|------------------------|
| `./schemas` | `/app/schema` | Yes — then `docker compose restart` to recompile schemas |
| `./samples` | `/app/samples` | Yes — immediate |
| `./src/main/resources/static` | `/app/static` | Yes — UI HTML/CSS/JS; hard-refresh browser (`Ctrl+F5`) |

### Do I need `--build` every time?

| What you changed | Command needed |
|------------------|----------------|
| UI (`static/ui/compare.html`, CSS/JS) | **Nothing** — save file, hard-refresh browser |
| Sample `.bin` / JSON under `samples/` | **Nothing** — already mounted |
| DFDL XSD under `schemas/` | `docker compose restart` (no rebuild) |
| Java code (controllers, services, mappers) | `docker compose up --build -d` |

**Why?** Java is compiled into the JAR inside the image. Schemas/samples/UI are mounted from your PC, so those updates do not need a full image rebuild.

First time after pulling this compose change (static volume), run once:

```bash
docker compose up --build -d
```

### Other useful APIs

| Method | URL | Purpose |
|--------|-----|---------|
| `GET` | `/health` | Request + response schema compile status |
| `GET` | `/ui/compare` | Browser UI for binary compare (upload + formatted JSON) |
| `POST` | `/parse` | Binary → mapped seat-map **request** JSON |
| `POST` | `/parse/sample/{fileName}` | Parse file from `/app/samples` |
| `POST` | `/process` | Request `.bin` → seatmap API → response `.bin` |
| `POST` | `/process/sample/{fileName}` | Same pipeline using a sample request `.bin` |
| `POST` | `/unparse` | Seat-map **response** JSON → SMPRES `.bin` |
| `POST` | `/compare` | Compare client SMPRES `.bin` vs unparse `.bin` |
| `POST` | `/diagnose` | ACE/Daffodil compile (+ optional parse) diagnostics |

### Compare UI (browser)

Open:

[http://127.0.0.1:8080/ui/compare](http://127.0.0.1:8080/ui/compare)

1. Upload **client shared** `.bin` (e.g. `Response_SMPRES_4.bin`)
2. Upload **unparse output** `.bin` (e.g. `SMPRES.bin`)
3. Click **Compare** — calls `POST /compare` and shows:
   - Verdict / match % / mismatch %
   - **Mismatched values** table (`path`, client vs unparse)
   - Pretty-printed **Client JSON** and **Unparse JSON**
   - Full API response + checks

### Compare client binary vs unparse binary

Upload the client-shared response binary and your unparse output:

```bash
curl.exe -X POST http://127.0.0.1:8080/compare ^
  -F "clientFile=@./samples/Response_SMPRES_4.bin;type=application/octet-stream" ^
  -F "unparseFile=@./samples/SMPRES.bin;type=application/octet-stream"
```

Response includes:
- `verdict` — `IDENTICAL` / `STRUCTURALLY_MATCHED` / `PARTIAL_MATCH` / `MISMATCH`
- `matchPercent` — share of checks passed
- `mismatchPercent` — remaining percent that did not match (`100 - matchPercent`)
- `clientJson` / `unparseJson` — both binaries extracted to readable JSON (envelope, flight, aircraft, cabin, rows)
- `mismatchedValues` — field-level diffs with `path`, `clientValue`, `unparseValue`, `category`, `explanation`
- `mismatchDetails` — present when not 100%; failed checks + why + same `mismatchedValues`
- `matches` / `differences` — human-readable lists
- `checks` — encoding, message type, TVL/EQI/CBD/UNT exactness, ROD skeleton, etc.
- `segmentDiffs` — per-segment EXACT / STRUCTURAL_MATCH / CONTENT_DIFF

Example when not fully matched:

```json
{
  "matchPercent": 92,
  "mismatchPercent": 8,
  "clientJson": {
    "flight": { "departureAirport": "LAX", "arrivalAirport": "DEN", "flightNumber": "1275" },
    "envelope": { "UNB": { "interchangeRef": "01735HSGF30001", "time": "0136" } }
  },
  "unparseJson": {
    "flight": { "departureAirport": "LAX", "arrivalAirport": "DEN", "flightNumber": "1275" },
    "envelope": { "UNB": { "interchangeRef": "25196ea0f99f4a", "time": "1828" } }
  },
  "mismatchedValues": [
    {
      "path": "envelope.UNB.interchangeRef",
      "clientValue": "01735HSGF30001",
      "unparseValue": "25196ea0f99f4a",
      "category": "TRANSACTION",
      "explanation": "UNB dynamic fields often differ (recipient/time/refs)"
    },
    {
      "path": "envelope.UNB.time",
      "clientValue": "0136",
      "unparseValue": "1828",
      "category": "TRANSACTION",
      "explanation": "UNB dynamic fields often differ (recipient/time/refs)"
    }
  ],
  "mismatchDetails": {
    "failedCheckCount": 1,
    "totalCheckCount": 12,
    "reason": "8% of structural checks did not pass (1 of 12 failed). mismatchedValues lists 2 JSON field difference(s).",
    "failedChecks": [
      {
        "check": "byteIdentical",
        "detail": "Files differ at byte level (expected if different transaction)",
        "impact": "Not a layout failure by itself — expected when transaction ids, timestamps, or seat inventory differ."
      }
    ],
    "whyNotMatched": [
      "byteIdentical: Files differ at byte level ...",
      "field envelope.UNB.interchangeRef: client=01735HSGF30001, unparse=25196ea0f99f4a (TRANSACTION)"
    ],
    "mismatchedValues": ["...same as top-level mismatchedValues..."]
  }
}
```

---

## 8. Configuration

`src/main/resources/application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port |
| `daffodil.schema` | `/app/schema/CYO_SMPREQ.xsd` | Request DFDL schema (parse) |
| `daffodil.response-schema` | `/app/schema/CYO_SMPRES.xsd` | Response DFDL schema (unparse) |
| `daffodil.samples-dir` | `/app/samples` | Sample binaries directory |
| `daffodil.channel-id` | `4101` | Mapped `ChannelId` |
| `daffodil.default-channel-name` | `1A` | Fallback `ChannelName` |
| `daffodil.default-currency-code` | `USD` | Fallback `CurrencyCode` |
| `daffodil.default-class-of-service` | `Y` | Mapped `ClassOfService` |
| `daffodil.default-pricing` | `true` | Mapped `Pricing` |
| `daffodil.seat-map-api-url` | `http://localhost:9000/api/seatmap` | External seat-map API for `POST /process` |

Environment overrides: `DAFFODIL_SCHEMA`, `DAFFODIL_SAMPLES_DIR`, `DAFFODIL_CHANNEL_ID`, `SEATMAP_API_URL`, `SERVER_PORT`, etc.

---

## 9. Folder structure

```
dfdl-parser/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
├── schemas/                 # IBM ACE DFDL XSDs (mounted → /app/schema)
│   ├── CYO_SMPREQ.xsd
│   ├── CDE_Common.xsd
│   └── CDE_*.xsd ...
├── samples/
│   ├── Request_SMPREQ_1.bin              # input binary
│   └── Request_seatmaprequest_2.txt      # target JSON shape
└── src/main/java/com/example/dfdl/
    ├── controller/ParserController.java
    ├── service/DaffodilParserService.java
    ├── service/SeatMapRequestMapper.java
    ├── service/DiagnosticService.java
    ├── dto/ParseResponse.java
    ├── dto/SeatMapRequest.java
    └── ...
```

---

## 10. Troubleshooting

| Symptom | Cause | Action |
|---------|--------|--------|
| `schemaCompiled: false` | Missing XSD include / compile error | Copy all ACE includes into `schemas/`, run `POST /diagnose`, restart |
| `SCHEMA_ERROR` on `/parse` | Schema did not compile at startup | Fix schemas, `docker compose restart`, check `/health` |
| HTTP 400 parse error | Binary does not match schema / wrong encoding | Confirm EBCDIC sample; inspect `error` diagnostics |
| JSON shape wrong | Mapping defaults | Adjust `daffodil.channel-id` / class / pricing in `application.yml` |

```bash
docker compose logs -f dfdl-parser
```

---

## 11. Technology stack

- Java 21
- Spring Boot 3.4
- Maven
- Apache Daffodil 3.10.0 (Java API only — no CLI)
- Jackson XML / JSON
- JUnit 5 + Mockito
- SLF4J
- Docker / Docker Compose
