Place binary sample files in this directory.

They are mounted into the container at /app/samples.

Parse via:
  POST /parse  (multipart field "file")
  POST /parse/sample/{fileName}
