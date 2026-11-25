# Revisión de imagen PostgreSQL — 2026-11-24

Estado vigente: cambio a Trixie y riesgo residual aprobados expresamente por el usuario el 2026-11-24, exclusivamente para el MVP local de DemandFlow sin exposición a Internet. No se declara ausencia de vulnerabilidades. La sección histórica de Bookworm se conserva para trazabilidad.

## Decisión humana y plataforma vigente

El usuario indicó: «Apruebo la actualización de D2 para utilizar la imagen oficial `postgres:17.12-trixie`» y «Acepto expresamente los riesgos residuales documentados, exclusivamente para el MVP local de DemandFlow, bajo las medidas de seguridad propuestas y sin exposición a Internet». Autorizó actualizar configuración/pruebas/documentación y completar 7.1–8.2; el contenedor permanente se gestionará manualmente. Esta aceptación humana levanta el bloqueo previo, sin afirmar que los CVE estén corregidos ni extenderse a producción o usuarios no confiables.

- Imagen vigente: `postgres:17.12-trixie`, digest evaluado `postgres@sha256:87c8b42f423b45d5247e6aa8fdb2e7762fec164c68a50fe3eb07e7bfcaf4dbcf`, plataforma linux/amd64, PostgreSQL `17.12-1.pgdg13+1`, gosu 1.20.
- Docker Scout 1.28.0: 209 paquetes, 23 paquetes vulnerables, 135 avisos: 4 críticos, 36 altos, 34 medios y 61 bajos. Bookworm: 227 paquetes, 30 vulnerables, 178 avisos: 4 críticos, 50 altos, 51 medios, 67 bajos y 6 sin clasificación.
- Trixie mantiene los cuatro críticos y treinta altos Go/gosu; cuatro altos OpenSSL y dos libxml2. La ausencia de avisos de glibc/SQLite en este escaneo no prueba corrección: sus versiones continúan señaladas por Debian. No se descartan esos riesgos por el menor recuento.
- Mejora comprobada: zlib `1:1.3.dfsg+really1.3.1-1+deb13u1` contiene la corrección de CVE-2026-85091 según Debian. Se reduce la exposición sin cambiar motor, añadir herramientas ni construir imágenes.
- Medidas aceptadas: publicación exclusivamente loopback, equipo local de confianza sin acceso público, SCRAM, secretos externos protegidos, separación administrador/migrador/runtime, SQL y diagnósticos sin secretos, respaldo/restauración verificados antes de datos relevantes. Cualquier cambio de digest o de condiciones de uso requiere revisar la decisión; no hay actualización automática ni aceptación general futura.
- Evidencias locales del contraste: `bookworm-comparison.sarif`, SHA256 `0f008fbaa941411583af03de6f92acb69c94a2f41c0e18af51630d2a3a397501`; `trixie-comparison.sarif`, SHA256 `5ab1e66b44ab82da62440035fc5c0ad302356fe779cc329bae35c95955964066`; inventario `setup-database-trixie-sbom.log`. Se preservan en caché de verificación durante las construcciones limpias y se restituyen a `target/` al finalizar. La descarga de Trixie necesitó un reintento por DNS; terminó satisfactoriamente sin crear contenedores.

Fuentes de contraste ya consultadas: [zlib](https://security-tracker.debian.org/tracker/CVE-2026-85091), [glibc](https://security-tracker.debian.org/tracker/CVE-2026-84243), [SQLite](https://security-tracker.debian.org/tracker/CVE-2026-119373), [libxml2](https://security-tracker.debian.org/tracker/CVE-2026-86140). No se amplía la investigación ni se instala Go/govulncheck.

### Corrección del contraste previo de gosu

El binario extraído previamente es Linux/amd64, Go 1.24.6, CGO_ENABLED=0 y módulo gosu v1.20.0. Su SHA256 `b1892636c281590f34bc63c1118f058cd9de66c4ea463772e3909672d8b302ac` coincide exactamente con [gosu-amd64 1.20 publicado por upstream](https://github.com/tianon/gosu/releases/expanded_assets/1.20). La diferencia histórica era una comparación con 1.19, no una modificación demostrada del binario. La inspección estática de `.gopclntab` no encontró funciones HTTP/TLS, IDNA ni os.Root; las cadenas sueltas no prueban alcanzabilidad. No se ejecutó govulncheck ni se presenta este análisis como certificación. CVE-2026-76844, atribuido a zlib por Scout en Bookworm, corresponde a [webpack](https://security-tracker.debian.org/tracker/CVE-2026-76844).

## Antecedente histórico — Bookworm, antes de la aprobación

## Imagen y herramienta

- Imagen aprobada: `postgres:17.12-bookworm`, sin sustitución.
- Digest observado: `postgres@sha256:837bed18782820fe967067cb5328b02b0d9992c1be9a37ee0c9c492cfc03af3b`.
- Docker Scout v1.28.0; comando ejecutado: `docker scout cves --format sarif --output target/setup-database-metadata/postgres-scout.sarif postgres:17.12-bookworm`.
- Salida satisfactoria del analizador, 227 paquetes indexados. Informe local excluido de Git; resumen preservado aquí.
- Resultado por severidad: 4 críticas, 50 altas, 51 medias, 67 bajas, 6 sin clasificación; total 178 avisos sobre 30 paquetes. El éxito del comando no implica seguridad de la imagen.

## Avisos críticos

Los cuatro resultados apuntan a Go stdlib 1.24.6 en `/usr/local/bin/gosu`, no al servidor PostgreSQL. Las correcciones son versiones de Go informadas por Scout, no versiones PostgreSQL autorizadas.

| CVE | CVSS informado | Corrección informada | Funcionalidad descrita por el informe |
|---|---|---|---|
| CVE-2026-78663 | 9.1 | Go 1.26.9 | Control de flujo de servidor HTTP/2 |
| CVE-2026-39821 | 9.6 | Go 1.25.13 | Conversión IDNA/Punycode |
| CVE-2026-56857 | 9.8 | Go 1.26.9 | Root.Mkdir/MkdirAll y junctions en Windows |
| CVE-2025-68121 | 10.0 | Go 1.24.13 | Reanudación de sesiones crypto/tls |

Las altas se distribuyen entre Go stdlib (30), krb5 (6), openssl (4), sqlite3 (3), util-linux (3), zlib (2), glibc (1) y pam (1). El resto no se descarta por no ser crítico; el SARIF conserva versiones y referencias por aviso.

## Contraste de aplicabilidad y límites

La [política upstream de gosu 1.19](https://github.com/tianon/gosu/blob/1.19/SECURITY.md) advierte de falsos positivos por interfaces Go no invocadas y propone verificar alcanzabilidad con su wrapper de govulncheck. Su [main.go etiquetado](https://github.com/tianon/gosu/blob/1.19/main.go) se limita a cambio de usuario y ejecución; no importa HTTP/TLS. El [aviso primario GO-2026-4337](https://pkg.go.dev/vuln/GO-2026-4337) confirma que CVE-2025-68121 afecta crypto/tls. La [release 1.19](https://github.com/tianon/gosu/releases/tag/1.19) declara compilación Go 1.24.6, coincidente con el inventario Scout.

**Inferencia preliminar:** los avisos críticos podrían no ser alcanzables en gosu; el de junctions Windows tampoco corresponde al SO Linux de la imagen. Esto no equivale a una comprobación de alcanzabilidad del binario exacto ni permite declarar cuatro falsos positivos verificados. No se ejecutó govulncheck ni se instalaron herramientas o dependencias adicionales. Tampoco se declara saneada la lista de avisos altos.

Comprobación adicional realizada sin iniciar el contenedor: se copió `/usr/local/bin/gosu` desde la imagen a `target/setup-database-metadata/gosu-image`. SHA256 observado: `b1892636c281590f34bc63c1118f058cd9de66c4ea463772e3909672d8b302ac`, distinto del publicado para gosu-amd64 1.19 (`52c8749d0142edd234e9d6bd5237dff2d81e71f43537e2f4f66f75dd4b243dd0`). Se encontraron las cadenas `crypto/tls`, `net/http` y `go1.24.6`; no `golang.org/x/net/idna`. La presencia de cadenas no demuestra ejecución de funciones vulnerables; su ausencia tampoco es un análisis completo. Estos resultados impiden usar la coincidencia de versión de compilador como prueba de identidad del binario upstream o descartar avisos basándose solo en main.go. No se ha establecido causa del checksum diferente ni se infiere manipulación.

El contenedor `demandflow-it-security-<UUID>` no se inició y se eliminó, junto con su volumen anónimo, tras comprobar nombre e identidad `io.demandflow.verification=setup-database`. Ningún volumen de desarrollo intervino. No están instalados `go` ni `govulncheck`; no se añadieron herramientas. Evidencia local: `gosu-static-review.json`.

## Tratamiento histórico antes de la aceptación humana

No cambiar imagen, parche PostgreSQL, variante ni dependencias para silenciar el analizador. Conservar D1-D9. Antes de cerrar 7.1/8.1 se necesita resolver la aplicabilidad con evidencia suficiente y documentar el tratamiento, o someter cualquier cambio de la imagen aprobada a revisión humana. Este informe no aprueba aceptación de riesgos ni nuevas versiones.

En ese punto, `ComposePersistenceIT` solo había compilado y no permitía marcar tareas. Después de la aceptación humana registrada al principio de este informe, se ejecutó contra Trixie y verificó recreación, restauración y limpieza en proyectos temporales; el detalle y la verificación integrada están en `evidence.md`. No se ha utilizado ni eliminado el volumen de desarrollo. Los resultados funcionales no convierten los CVE residuales en corregidos.
