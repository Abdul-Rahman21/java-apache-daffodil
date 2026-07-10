#!/usr/bin/env bash
set -eu
cd /workspace
# Use app classpath from running image deps via maven in a one-off compile of schemas
mvn -q -DincludeScope=compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP=$(cat /tmp/cp.txt)
cat > /tmp/DiagCompile.java <<'JAVA'
import org.apache.daffodil.japi.*;
import java.io.File;
public class DiagCompile {
  public static void main(String[] args) throws Exception {
    try {
      Compiler c = Daffodil.compiler();
      ProcessorFactory pf = c.compileFile(new File(args[0]));
      System.out.println("isError=" + pf.isError());
      for (Diagnostic d : pf.getDiagnostics()) {
        System.out.println("DIAG: " + d.toString());
      }
      if (!pf.isError()) {
        DataProcessor dp = pf.onPath("/");
        System.out.println("dpError=" + dp.isError());
        for (Diagnostic d : dp.getDiagnostics()) {
          System.out.println("DPDIAG: " + d.toString());
        }
      }
    } catch (Throwable t) {
      System.out.println("THROWABLE: " + t.getClass().getName() + ": " + t.getMessage());
      t.printStackTrace(System.out);
    }
  }
}
JAVA
javac -cp "$CP" -d /tmp /tmp/DiagCompile.java
echo "=== compiling $1 ==="
java -cp "/tmp:$CP" DiagCompile "$1"
