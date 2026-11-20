# Evidencia de implementación

## Compatibilidad e integridad

- Spring Boot 4.1.2 admite Java 21 y Maven 3.6.3 o posterior: [requisitos oficiales](https://docs.spring.io/spring-boot/4.1.2/system-requirements.html). Se seleccionó Maven 3.9.11.
- **Decisión aprobada:** el responsable aprobó expresamente sustituir JUnit 5 por JUnit Jupiter 6. El BOM oficial de Spring Boot 4.1.2 fija `junit-jupiter.version` en `6.0.3` y Spring Framework 7.0.10; la [documentación de Spring Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/support-classes.html) establece que `SpringExtension` requiere JUnit Jupiter 6.0 o superior. La incompatibilidad de la decisión anterior quedó resuelta; la prueba de contexto se ejecutó satisfactoriamente con Java 21, Spring Boot 4.1.2 y JUnit Jupiter 6, como se detalla más abajo.
- Se descargó Maven 3.9.11 desde [Maven Central](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip) y se comparó su SHA-512 con la [suma publicada por Apache](https://www.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip.sha512): coincidió. SHA-256: `0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb`. El [Wrapper de Apache](https://maven.apache.org/tools/wrapper/) permite fijar esa suma.
- Entorno comprobado: Eclipse Temurin 21.0.13+9 para `java` y `javac`.
- Resolución del descriptor y árbol de dependencias mediante Maven: exitosa; Spring Boot Test 4.1.2 y JUnit Jupiter 6.0.3, sin versiones forzadas. El Wrapper oficial 3.3.4 de tipo `bin` ejecutó Maven 3.9.11 con Temurin 21 sin Maven global. Se usó esta variante porque el lanzador `only-script` falló bajo el modo de lenguaje restringido de PowerShell. El JAR coincide con SHA-1 publicado por Maven Central; se registró además su SHA-256 en la configuración. La distribución Maven se verifica con SHA-256.
- `mvnw.cmd clean package`: resultado 0 con Java 21; Surefire informó una prueba de contexto ejecutada, cero fallos, errores u omisiones. Se generó `target/demandflow-0.0.1-SNAPSHOT.jar` con el lanzador y la clase principal.
- En una copia aislada en el directorio temporal se introdujo un archivo Java inválido: `mvnw.cmd -o compile` terminó con código 1. El archivo deliberadamente fallido no se incorporó al repositorio.
- Arranque real con Java 21 y `--server.port=18080`: Spring Boot informó inicio exitoso; el proceso permaneció activo y `netstat` mostró una única escucha del proceso en `127.0.0.1:18080`. No se configuró base de datos ni existen dependencias de persistencia; el proceso se detuvo después de la comprobación.
- Revisión de seguridad (2026-11-14): se inspeccionaron descriptor, configuración, fuentes y árbol de dependencias; no se identificaron credenciales reales, componentes de persistencia ni Actuator. Una consulta a [OSV](https://osv.dev/) de los 68 componentes Maven resueltos (incluidas dependencias de pruebas) no devolvió vulnerabilidades conocidas para sus coordenadas y versiones. Esto no garantiza ausencia de vulnerabilidades; debe repetirse periódicamente. Se consultó la edición vigente [OWASP Top 10:2025](https://top10.owasp.org/2025/). No había escucha local en el puerto PostgreSQL habitual 5432 durante la verificación.

## Comandos documentados

- Se ejecutaron `mvnw.cmd test` y `mvnw.cmd package` con código 0, usando Java 21 y un PATH sin Maven global. Se ejecutó el JAR con el comando documentado y se observó escucha en `127.0.0.1:8080`.

## Escenarios y alcance

- Construcción y Wrapper: `clean package`, `test` y `package` terminaron correctamente y generaron el JAR ejecutable. Fallo de construcción: la compilación inválida aislada devolvió código 1.
- Arranque sin persistencia y red local: el JAR arrancó, permaneció activo y su única escucha observada fue loopback; no hay configuración ni dependencias de base de datos, y no se detectó escucha local en el puerto 5432.
- Credenciales: la revisión de los archivos entregados no identificó secretos reales. Se revisaron las diferencias y el árbol de dependencias; no se incorporaron PostgreSQL, Docker Compose, JPA, Hibernate, Flyway, migraciones ni funcionalidad de negocio.
