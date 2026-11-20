# Especificación: arranque de la aplicación

## Purpose

Permitir que el equipo construya y arranque localmente una base mínima verificable de DemandFlow, preparada para incrementos posteriores.

## ADDED Requirements

### Requirement: Construcción reproducible con Java 21
El proyecto MUST permitir su compilación y empaquetado mediante Maven con Java 21. Referencia: ADR-002; no corresponde directamente a una HU o regla RN/RF.

#### Scenario: Compilación y empaquetado satisfactorios
- **WHEN** se ejecuta la construcción del proyecto con Java 21 y las dependencias disponibles
- **THEN** la construcción finaliza satisfactoriamente y genera un artefacto ejecutable.

#### Scenario: Construcción mediante Maven Wrapper
- **WHEN** se ejecuta Maven Wrapper con Java 21 y la distribución Maven y las dependencias disponibles
- **THEN** el proyecto se compila, ejecuta las pruebas y genera un artefacto ejecutable sin requerir una instalación global de Maven.

#### Scenario: Fallo durante la construcción
- **WHEN** una prueba requerida o la compilación falla
- **THEN** la construcción informa el fallo y termina sin indicar éxito.

### Requirement: Arranque local sin base de datos
La aplicación MUST arrancar localmente sin requerir una base de datos ni servicios de persistencia. Referencias: ADR-001 y ADR-002; no corresponde directamente a una HU o regla RN/RF.

#### Scenario: Arranque sin infraestructura de persistencia
- **WHEN** se inicia el artefacto en un entorno local compatible sin base de datos configurada ni disponible
- **THEN** la aplicación completa su arranque y permanece disponible hasta su detención.

### Requirement: Ejecución en entorno local protegido
La aplicación MUST limitar su exposición al entorno local protegido y no requerir secretos reales versionados para arrancar. Referencias: ADR-007 y estándares de seguridad; no corresponde directamente a una HU o regla RN/RF.

#### Scenario: Configuración inicial de red
- **WHEN** se arranca la aplicación con la configuración local prevista
- **THEN** su interfaz de escucha queda restringida al entorno local y no se expone públicamente.

#### Scenario: Repositorio sin credenciales reales
- **WHEN** se revisan los archivos de configuración entregados para el arranque
- **THEN** no contienen credenciales ni secretos reales.
