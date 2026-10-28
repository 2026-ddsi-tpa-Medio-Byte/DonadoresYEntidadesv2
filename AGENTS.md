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
   con `origen: SOLICITUD_DONADORES`. La satisfacción local se registra solo si Logística
   confirmó la asignación, y una `RECURRENTE` solo se asigna si el stock cubre todo lo que falta.

Ese `origen` es lo que distingue esta asignación de la que nace del matchmaking, y lo necesita
Incentivos.

También es el que decide si un donador puede donar: acumula las quejas y de ahí sale el estado
`VERIFICADO` → `SOSPECHOSO` (5 quejas) → `BANEADO` (10 quejas). Donaciones consulta eso antes de
aceptar una donación.

## Historial de categorías

La ficha del donador (consulta 1 de la Entrega 6) pide la categoría actual **y el historial**.
Cada cambio queda en `CambioCategoriaDonador` (`{ categoria, desde }`, tabla
`cambios_categoria_donador`), en cascada desde `Donador`, igual que `CambioEstadoDonador`.

- El alta registra la categoría inicial (la del DTO, o `Colaborador`). Por eso la categoría se pasa
  en el constructor y no con el setter después: el historial no puede arrancar en `Colaborador` si
  el donador nunca lo fue.
- `modifcarCategoria`, que es lo que usa Incentivos con su PATCH, pasa por
  `Donador.modificarCategoria`. Si la categoría es la misma, no agrega entrada ni publica evento.
  `setCategoria` también pasa por ahí, así que no hay forma de saltear el historial.
- `desde` es un `Instant` en milisegundos, no un día: dos misiones el mismo día dejan dos entradas.
- Los donadores dados de alta antes de la Entrega 6 tienen el historial vacío hasta su primer
  cambio. No se inventa una fecha para lo anterior.
- `DonadorDTO` es de cátedra y no se tocó: el historial sale en `GET /donadores/{id}/categorias`
  (tag `Consultas`) y en los eventos del donador.

## Eventos para el Servicio de Consultas (Entrega 6, CQRS)

El contrato está en `TP DDSI/guias-entrega-6/CONTRATO-EVENTOS.md`. Todo vive en `eventos/`:
`EventosDonadores` arma cada evento con el estado del momento y `PublicadorEventos` lo manda al
exchange `donatrack.eventos` (topic, ya creado por Logística). **El módulo solo publica**: no
declara colas ni el exchange.

| `tipo` | routing key | se emite en |
|---|---|---|
| `DONADOR_CREADO` | `donadores.donador.creado` | `agregarDonador` |
| `DONADOR_MODIFICADO` | `donadores.donador.modificado` | `modificarEstado`, `modifcarCategoria` (si cambió), `agregarQueja` |
| `QUEJA_REGISTRADA` | `donadores.queja.registrada` | `agregarQueja` (antes que el `DONADOR_MODIFICADO`) |
| `ENTIDAD_CREADA` / `ENTIDAD_MODIFICADA` | `donadores.entidad.creada` / `.modificada` | `agregarEntidad` / `editarEntidad` |
| `NECESIDAD_REGISTRADA` | `donadores.necesidad.registrada` | `registrarNecesidad` |
| `NECESIDAD_MODIFICADA` | `donadores.necesidad.modificada` | `modificarNecesidad`, `satisfacerNecesidad` |
| `NECESIDAD_ELIMINADA` | `donadores.necesidad.eliminada` | `eliminarNecesidad` (`datos: { id }`) |
| `MODULO_RESETEADO` | `donadores.modulo.reseteado` | `resetBaseDeDatos` (`entidadId: "*"`, `datos: {}`) |

- **`datos` va sin email, documento, domicilio ni edad**, y la entidad solo con `id` y
  `razonSocial`: son records propios (`DatosEvento`), no los DTO de cátedra.
- **Fechas:** todo en UTC con milisegundos y `Z`, de ancho fijo (`FechasUtc`). La fecha de la queja
  es un `LocalDate`: sale como el comienzo de ese día en Argentina, en UTC (el 26/10 es
  `2026-10-26T03:00:00.000Z`), así el día no cambia ni leído en UTC ni en hora local.
- **`ocurridoEn` es estrictamente creciente** dentro del módulo: si el reloj no avanzó desde el
  evento anterior, se le suma 1 ms. Consultas solo pisa con un evento más nuevo, y un empate podía
  dejarle una versión vieja.
- **Después del commit:** `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`.
  Solo `resetBaseDeDatos` tiene transacción propia; en el resto cada `save` ya confirmó cuando se
  emite, y el listener corre en el mismo hilo.
- **Publicar nunca hace fallar la operación:** WARN en el log y `eventos.fallidos` (tag `tipo`);
  los que salen bien suman a `eventos.publicados`. La salud de RabbitMQ está apagada
  (`management.health.rabbit.enabled=false`) para que un broker caído no ponga DOWN al módulo, y
  la conexión espera como mucho 3 s.
- **`satisfacerNecesidad`:** el evento se **arma dentro** del candado (lleva la `cantidadCubierta`
  que dejó esa entrega, y la hora sigue el orden de las entregas) y se **publica afuera**, para que
  la espera de RabbitMQ no frene a las otras entregas de la misma necesidad. Hay tests de las dos
  cosas.
- **Satisfacción por stock al crear:** sale **un solo** `NECESIDAD_REGISTRADA`, ya con la cubierta
  final, en vez de uno en 0 y un `NECESIDAD_MODIFICADA`. Se relee de la base antes de armarlo (no
  se publica lo que no se guardó) y va en un `finally`, para que una falla en el paso de Logística
  no se lleve el evento de un alta que ya está guardada.

**`RABBITMQ_URL`** (variable de entorno de Render, `amqps://usuario:password@host/vhost`, la misma
de Logística) se mapea a `spring.rabbitmq.addresses`. Sin ella el publicador queda deshabilitado:
lo avisa una vez al arrancar y no intenta conectar. `application-test.properties` la vacía a
propósito, para que ningún test publique en el exchange real aunque la máquina la tenga.

**`POST /admin/republicar-eventos`** vuelve a publicar todos los donadores, entidades y necesidades
(`*_MODIFICADO` / `*_MODIFICADA`) y todas las quejas (`QUEJA_REGISTRADA`), con `ocurridoEn` =
ahora. Responde `{"publicados": N, "fallidos": F}`; sin `RABBITMQ_URL`, 503 con el motivo. Es la
carga inicial de Mongo y la forma de resincronizar.

## Cuidado con la concurrencia

`satisfacerNecesidad` está **serializado por ID de necesidad**. Sin eso, dos entregas
simultáneas leían la misma `cantidadCubierta` y una pisaba a la otra: con diez concurrentes
contra una necesidad de 20, quedaba en 8 o en 16.

Alcanza porque el servicio corre en **una sola instancia**. Si alguna vez se levanta más de una,
hay que reemplazarlo por un bloqueo en la base (`SELECT FOR UPDATE` o `@Version`). Hay un test
que lo cubre y falla si se saca el candado.

## Dependencias hacia afuera

`FachadaDonaciones`, `LogisticaStockClient` y `EventosDonadores` se inyectan con
`@Autowired(required = false)` y están **guardadas por null**: con el constructor sin argumentos que usan los tests de cátedra no
se ejecutan, y por eso esa suite sigue pasando.

La consulta de stock es *best-effort*: si Logística no responde, la necesidad se crea igual.

Solo **Incentivos** tiene timeout (`restTemplateIncentivos`: 3 s de conexión y 8 s de lectura,
configurables con `incentivos.timeout.conexion-ms` y `incentivos.timeout.lectura-ms`): sin él, las
estadísticas de un donador no respondían nunca con Incentivos caído. El `RestTemplate` de
Donaciones y Logística sigue sin timeout a propósito: validar el producto es bloqueante, y un
límite corto rechazaría necesidades válidas mientras Donaciones despierta en Render.

## Estructura

```
Fachada.java                    lógica de negocio
controllers/                    DonadorController, EntidadController,
                                NecesidadController, AdminController
clients/LogisticaStockClient    consulta de stock y solicitud de asignación
eventos/EventosDonadores        catálogo: arma cada evento con el estado del momento
eventos/PublicadorEventos       lo manda a RabbitMQ después del commit, nunca lanza
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

204 tests, 0 fallos. Corren 174: los 30 saltados son los tests de cátedra de los otros tres
módulos, que se desactivan solos con `@EnabledIf`. Cátedra `DonadoresYEntidadesTest`: 22/22.
