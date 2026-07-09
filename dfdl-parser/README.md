# dfdl-parser

Production-ready Spring Boot REST application that parses binary files with **Apache Daffodil** DFDL schemas, returns XML infoset + JSON, and exposes a diagnostic endpoint for IBM ACE DFDL schema compatibility checks.

## Project overview

```
Binary File
    ↓
Spring Boot REST API
    ↓
Apache Daffodil Java API
    ↓
DFDL Schema
    ↓
XML Infoset
    ↓
JSON
    ↓
REST Response
```

The DFDL schema is compiled **once at startup** into a singleton `DataProcessor` and reused for every request (thread-safe parse). Customer schemas are **not** packaged inside the JAR; they are loaded from `/app/schema` (Docker volume).

## Requirements

### Host machine (Docker path — recommended)

- Docker Desktop only

The host does **not** need Java, Maven, Apache Daffodil, Scala, or Spring Boot.

### Optional local development

- Java 21
- Maven 3.9+

## Folder structure

```
dfdl-parser/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
├── schemas/                          # DFDL XSD files (mounted to /app/schema)
│   └── CYO_SMPREQ.xsd                # sample schema (replace with IBM ACE schemas)
├── samples/                          # binary samples (mounted to /app/samples)
│   ├── sample_smpreq.bin
│   └── invalid.bin
└── src/
    ├── main/
    │   ├── java/com/example/dfdl/
    │   │   ├── Application.java
    │   │   ├── config/DaffodilConfiguration.java
    │   │   ├── controller/ParserController.java
    │   │   ├── controller/DiagnosticController.java
    │   │   ├── service/DaffodilParserService.java
    │   │   ├── service/DiagnosticService.java
    │   │   ├── dto/ParseResponse.java
    │   │   ├── dto/DiagnosticResponse.java
    │   │   ├── dto/HealthResponse.java
    │   │   ├── exception/GlobalExceptionHandler.java
    │   │   ├── exception/DfdlParseException.java
    │   │   ├── exception/DfdlSchemaException.java
    │   │   └── util/XmlJsonConverter.java
    │   └── resources/application.yml
    └── test/java/com/example/dfdl/...
```

## Running using Docker (recommended)

From the `dfdl-parser` directory:

```bash
docker compose up --build
```

The API listens on [http://localhost:8080](http://localhost:8080).

Volumes:

| Host path     | Container path |
|---------------|----------------|
| `./schemas`   | `/app/schema`  |
| `./samples`   | `/app/samples` |

## Running locally (optional)

```bash
# Windows PowerShell
$env:DAFFODIL_SCHEMA = "$PWD\schemas\CYO_SMPREQ.xsd"
$env:DAFFODIL_SAMPLES_DIR = "$PWD\samples"
mvn spring-boot:run
```

```bash
# Linux / macOS
export DAFFODIL_SCHEMA="$PWD/schemas/CYO_SMPREQ.xsd"
export DAFFODIL_SAMPLES_DIR="$PWD/samples"
mvn spring-boot:run
```

Run tests:

```bash
mvn test
```

## How to replace schemas

1. Copy your IBM ACE DFDL XSD files (and any `xs:import` / `xs:include` dependencies) into `./schemas`.
2. Ensure the primary schema filename matches the configured path, or update `daffodil.schema` / `DAFFODIL_SCHEMA`.
3. Default configuration:

   ```yaml
   daffodil:
     schema: /app/schema/CYO_SMPREQ.xsd
   ```

4. With Docker Compose, restart the container after replacing files:

   ```bash
   docker compose restart
   ```

   No image rebuild is required for schema-only changes.

## How to replace binary samples

1. Place binary files in `./samples`.
2. Parse via multipart upload (`POST /parse`) **or** by filename:

   ```bash
   curl -X POST http://localhost:8080/parse/sample/sample_smpreq.bin
   ```

## API reference

### GET /health

```bash
curl http://localhost:8080/health
```

Example response:

```json
{
  "status": "UP",
  "schemaCompiled": true,
  "schema": "CYO_SMPREQ.xsd"
}
```

### POST /parse

Multipart field name: `file`

```bash
curl -X POST http://localhost:8080/parse \
  -F "file=@./samples/sample_smpreq.bin;type=application/octet-stream"
```

Success:

```json
{
  "success": true,
  "xml": "<?xml ...>",
  "json": { "...": "..." }
}
```

Failure (HTTP 400):

```json
{
  "success": false,
  "error": "DFDL parse failed. Diagnostics: ..."
}
```

### POST /parse/sample/{fileName}

Parse a file already present under `/app/samples`:

```bash
curl -X POST http://localhost:8080/parse/sample/sample_smpreq.bin
```

### POST /diagnose

Determines whether Apache Daffodil can compile (and optionally parse with) the configured IBM ACE DFDL schema. Raw Daffodil diagnostics and exception messages are returned (not suppressed).

Compile-only:

```bash
curl -X POST http://localhost:8080/diagnose
```

Compile + parse:

```bash
curl -X POST http://localhost:8080/diagnose \
  -F "file=@./samples/sample_smpreq.bin;type=application/octet-stream"
```

Response fields include:

- `compileSuccess`
- `compileDiagnostics`
- `parseSuccess`
- `parseDiagnostics`
- `schemaPath`
- `elapsedMillis`
- `exceptionMessage` / `exceptionType` (when applicable)

## Configuration

`src/main/resources/application.yml`:

| Property                 | Default                          | Description              |
|--------------------------|----------------------------------|--------------------------|
| `server.port`            | `8080`                           | HTTP port                |
| `daffodil.schema`        | `/app/schema/CYO_SMPREQ.xsd`     | DFDL schema path         |
| `daffodil.samples-dir`   | `/app/samples`                   | Binary samples directory |
| `logging.level.*`        | see YAML                         | Log levels               |

Environment overrides:

- `DAFFODIL_SCHEMA`
- `DAFFODIL_SAMPLES_DIR`
- `SERVER_PORT`

## Sample binary layout (bundled demo schema)

| Field     | Type / length      | Sample value |
|-----------|--------------------|--------------|
| `msgId`   | unsignedInt / 4 B  | `1`          |
| `msgType` | text / 4 B ASCII   | `REQ1`       |
| `payload` | text / 5 B ASCII   | `HELLO`      |

## Troubleshooting

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| `schemaCompiled: false` on `/health` | Schema missing or failed compile | Check `./schemas`, container logs, call `POST /diagnose` |
| Import/include errors | ACE schema dependencies not mounted | Copy all referenced XSDs into `./schemas` preserving relative paths |
| HTTP 400 on `/parse` | Binary does not match schema | Inspect `error` / diagnostics; try `/diagnose` with the same file |
| Container starts but parse fails after schema swap | Schema compiled at startup | `docker compose restart` after replacing XSDs |
| Port already in use | Host port 8080 busy | Change compose ports mapping, e.g. `"8081:8080"` |

View logs:

```bash
docker compose logs -f dfdl-parser
```

## Technology

- Java 21
- Spring Boot 3.4
- Maven
- Apache Daffodil 3.10.0 (Java API only — no CLI)
- Jackson XML / JSON
- JUnit 5 + Mockito
- SLF4J
- Docker / Docker Compose
