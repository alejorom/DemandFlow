# Seguridad — Línea base OWASP Top 10
**Estado:** propuesta. Referencia conceptual: OWASP Top 10 para aplicaciones web; validar edición vigente al iniciar implementación.

## Riesgo clave del MVP
Sin autenticación, DemandFlow **solo puede ejecutarse localmente o en red de desarrollo protegida**. No se debe desplegar públicamente ni afirmar que dispone de autorización por usuario.

## Controles por categoría
| Área OWASP | Control previsto | Verificación |
|---|---|---|
| Control de acceso roto | No exponer en Internet; definir autenticación/autorización antes de despliegue compartido | Revisar binding de red y rutas |
| Fallos criptográficos | TLS cuando corresponda; secretos fuera de Git; no guardar contraseñas en texto plano | Revisión de configuración |
| Inyección | JPA con parámetros; evitar concatenar SQL; validar entradas | Pruebas de entradas maliciosas |
| Diseño inseguro | Invariantes documentadas, análisis de abuso, validación servidor | Pruebas de reglas de negocio |
| Configuración insegura | Deshabilitar errores detallados al usuario, perfiles seguros, mínimos permisos | Revisión de configuración |
| Componentes vulnerables | Dependencias fijadas, actualización y escaneo | Revisión de vulnerabilidades |
| Fallos de identificación/autenticación | No disponible en MVP; bloqueo de publicación pública | Checklist de despliegue |
| Integridad de software/datos | Migraciones revisadas, fuentes confiables, auditoría inmutable | CI y revisión |
| Registro y monitoreo insuficientes | Eventos auditables, logs sin secretos ni datos sensibles | Pruebas y revisión de logs |
| SSRF | No introducir solicitudes HTTP arbitrarias; validar destinos si se añade integración | Revisión de futuras integraciones |

## Controles web específicos
- CSRF habilitado para formularios que cambian estado.
- Escapado de salida en Thymeleaf; no insertar HTML sin sanitización.
- Validación de entrada, límites de longitud y errores de usuario no reveladores.
- Encabezados de seguridad apropiados (CSP según recursos, X-Content-Type-Options, etc.).
- Credenciales de PostgreSQL por variables de entorno/secretos, no en repositorio.
- Base de datos sin puerto público innecesario, usuario con privilegios mínimos.
- No confiar en validación del navegador; comprobar todo en servidor.

## Antes de pasar a producción
Autenticación, autorización, gestión de sesiones, TLS, copias de seguridad, política de retención y evaluación de amenazas son requisitos de salida adicionales.
