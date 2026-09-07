# Módulo Donadores y Entidades — DonaTrack

Uno de los cuatro microservicios del TP anual de DDS (K3003, UTN 2026). Maneja donadores,
entidades beneficiarias y sus necesidades.

El contexto completo del sistema está en `AGENTS.md` de `TP DDSI/`.

## Reglas que no se rompen

- **No modificar `catedra/` ni `.github/`**: implica desaprobar la instancia de entrega.
- Los tests de cátedra tienen que seguir en verde: **22/22 en `DonadoresYEntidadesTest`**.
- Commits con la identidad del alumno, sin `Co-Authored-By`.

## Qué hace este módulo

Es el dueño de tres cosas: quién es cada donador, qué entidades reciben donaciones, y qué
necesita cada una.

Al registrar una necesidad:

1. Valida que la cantidad objetivo sea mayor a cero y que la entidad exista.
2. Le pregunta a **Donaciones** si el producto existe; si no, la rechaza.
3. Guarda la necesidad.
4. Le consulta el stock a **Logística** y, si hay disponible, pide que se asigne en el momento
   con `origen: SOLICITUD_DONADORES`.

Ese `origen` es lo que distingue esta asignación de la que nace del matchmaking, y lo necesita
Incentivos.

También es el que decide si un donador puede donar: acumula las quejas y de ahí sale el estado
`VERIFICADO` → `SOSPECHOSO` (≈8 quejas) → `BANEADO` (≈11). Donaciones consulta eso antes de
aceptar una donación.

## Cuidado con la concurrencia

`satisfacerNecesidad` está **serializado por ID de necesidad**. Sin eso, dos entregas
simultáneas leían la misma `cantidadCubierta` y una pisaba a la otra: con diez concurrentes
contra una necesidad de 20, quedaba en 8 o en 16.

Alcanza porque el servicio corre en **una sola instancia**. Si alguna vez se levanta más de una,
hay que reemplazarlo por un bloqueo en la base (`SELECT FOR UPDATE` o `@Version`). Hay un test
que lo cubre y falla si se saca el candado.

## Dependencias hacia afuera

`FachadaDonaciones` y `LogisticaStockClient` se inyectan con `@Autowired(required = false)` y
están **guardadas por null**: con el constructor sin argumentos que usan los tests de cátedra no
se ejecutan, y por eso esa suite sigue pasando.

La consulta de stock es *best-effort*: si Logística no responde, la necesidad se crea igual.

## Estructura

```
Fachada.java                    lógica de negocio
controllers/                    DonadorController, EntidadController,
                                NecesidadController, AdminController
clients/LogisticaStockClient    consulta de stock y solicitud de asignación
logging/DatadogLogAppender      envía logs a Datadog en lotes, nunca bloquea
logging/TrazabilidadFilter      pone traceId y requestId en el MDC
config/OpenApiConfig            agrupa el Swagger por propósito
```

## Detalle de la API

`GET /necesidades` **exige** el parámetro `productoID`. Sin él devuelve 400, y está bien: ese
endpoint existe para que Logística consulte las insatisfechas de un producto.

## Antes de terminar un cambio

```bash
mvn test
```

89 tests, 0 fallos.
