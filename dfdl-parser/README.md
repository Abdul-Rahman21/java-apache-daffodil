# dfdl-parser — Technical Documentation

Spring Boot REST service that accepts an **IBM ACE / EDIFACT binary** (`.bin`), parses it with **Apache Daffodil** using DFDL XSD schemas, maps the result into a **seat-map request JSON**, and returns that JSON in the API response.

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

## 4. How the response is returned

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

## 5. Components involved

| Layer | Class | Responsibility |
|-------|--------|----------------|
| API | `ParserController` | Accepts multipart binary, returns `ParseResponse` |
| Parse | `DaffodilParserService` | Compiles schema once; parses binary with Daffodil |
| Convert | `XmlJsonConverter` | XML infoset → JSON |
| Map | `SeatMapRequestMapper` | DFDL JSON → seat-map request JSON |
| DTO | `ParseResponse` / `SeatMapRequest` | Response and mapped payload shapes |
| Errors | `GlobalExceptionHandler` | Structured JSON errors |
| Config | `application.yml` / `DaffodilConfiguration` | Schema path, channel defaults, logging |

---

## 6. Running the application

### Docker (recommended)

Host needs **only Docker Desktop** (no Java/Maven on the host).

```bash
cd dfdl-parser
docker compose up --build
```

API base URL: [http://localhost:8080](http://localhost:8080)

| Host folder | Container path |
|-------------|----------------|
| `./schemas` | `/app/schema` |
| `./samples` | `/app/samples` |

After changing XSDs:

```bash
docker compose restart
```

### Other useful APIs

| Method | URL | Purpose |
|--------|-----|---------|
| `GET` | `/health` | Schema compile status |
| `POST` | `/parse` | Binary → mapped seat-map JSON |
| `POST` | `/parse/sample/{fileName}` | Parse file from `/app/samples` |
| `POST` | `/diagnose` | ACE/Daffodil compile (+ optional parse) diagnostics |

---

## 7. Configuration

`src/main/resources/application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port |
| `daffodil.schema` | `/app/schema/CYO_SMPREQ.xsd` | Primary DFDL schema |
| `daffodil.samples-dir` | `/app/samples` | Sample binaries directory |
| `daffodil.channel-id` | `4101` | Mapped `ChannelId` |
| `daffodil.default-channel-name` | `1A` | Fallback `ChannelName` |
| `daffodil.default-currency-code` | `USD` | Fallback `CurrencyCode` |
| `daffodil.default-class-of-service` | `Y` | Mapped `ClassOfService` |
| `daffodil.default-pricing` | `true` | Mapped `Pricing` |

Environment overrides: `DAFFODIL_SCHEMA`, `DAFFODIL_SAMPLES_DIR`, `DAFFODIL_CHANNEL_ID`, `SERVER_PORT`, etc.

---

## 8. Folder structure

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

## 9. Troubleshooting

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

## 10. Technology stack

- Java 21
- Spring Boot 3.4
- Maven
- Apache Daffodil 3.10.0 (Java API only — no CLI)
- Jackson XML / JSON
- JUnit 5 + Mockito
- SLF4J
- Docker / Docker Compose
