# Entrega 4 — Guía de pruebas

Cómo verificar que el flujo de trabajo de la Entrega 4 funciona, en tres niveles:
automático (tests), manual con Postman/curl, y desde el bot de Telegram.

---

## 1. Pruebas automáticas (ya incluidas, corren con `mvn test`)

### Módulo Donadores y Entidades (este repo, rama `entrega-4`)

```powershell
mvn test
```

| Test | Qué prueba |
|------|-----------|
| `Entrega4FlujoIntegrationTest` | **El flujo completo de la consigna, por HTTP real.** Levanta la app (Tomcat + H2) y simula Donaciones y Logística con `MockRestServiceServer`: ① valida producto con `GET {donaciones}/productos/{id}`, ② consulta `GET {logistica}/stock/{id}`, ③ solicita `POST {logistica}/asignaciones/solicitud` con `origen=SOLICITUD_DONADORES` y cantidad `min(stock, faltante)`. Además: producto inexistente → 400 y no llama a Logística; stock 0 → crea la necesidad sin asignar. |
| `Entrega4DonadoresTest` | La validación y la auto-asignación a nivel Fachada (mocks). |
| Cátedra `DonadoresYEntidadesTest` | 22/22 — el contrato base sigue intacto. |

### Módulo Donaciones (worktree `Donaciones-entrega4`, rama `entrega-4`)

```powershell
mvn test
```

| Test | Qué prueba |
|------|-----------|
| `Entrega4IntegrationTest` | Por HTTP real (H2): alta identificador/producto, `GET /productos/{id}` inexistente → **404** (lo que consume Donadores), listados, métricas en Actuator. |
| `Entrega4Test` | Handler 404/400 y listados a nivel unitario. |
| Cátedra `DonacionesTest` | 10/10. |

### Bot de Telegram (repo `bot-telegram`)

```powershell
mvn test
```
`DonaTrackBotTest` (6 tests): parseo y despacho de todos los comandos con Telegram y API mockeados.

---

## 2. Prueba manual del flujo E4 (Postman o curl)

> Requiere los módulos corriendo. Opciones: **(a)** local con Postgres/variables, o **(b)** en Render
> una vez mergeada la rama `entrega-4` a `main` en ambos repos. Reemplazá las URLs base según corresponda:
> - `DONACIONES` = URL del módulo Donaciones (ej. `https://donatrack-donaciones.onrender.com`)
> - `DONADORES` = URL del módulo Donadores y Entidades

### Paso 0 — Precondiciones en Donaciones (crear el producto)

```bash
# 1. Crear identificador
curl -X POST $DONACIONES/identificadores -H "Content-Type: application/json" \
  -d '{"tipo":"CODIGODEBARRAS","descripcion":"codigo de barras"}'
# → anotar el "id" devuelto (ej: 1)

# 2. Crear producto
curl -X POST $DONACIONES/productos -H "Content-Type: application/json" \
  -d '{"nombre":"Arroz","descripcion":"Arroz blanco largo fino","categoriaID":"alimentos","identificadorID":"1"}'
# → anotar el "id" devuelto (ej: 1)
```

### Paso 1 — Crear la entidad benéfica (Donadores)

```bash
curl -X POST $DONADORES/entidades -H "Content-Type: application/json" \
  -d '{"razonSocial":"Comedor Hogwarts","domicilio":"Calle 1","telefono":"11-3000","correo":"h@mail.com"}'
# → anotar el "id" (ej: 1)
```

### Paso 2 — Crear necesidad con producto VÁLIDO (flujo E4 feliz)

```bash
curl -X POST $DONADORES/necesidades -H "Content-Type: application/json" \
  -d '{"entidadID":"1","nivelDeUrgencia":8,"descripcion":"30 sillas tras inundacion","cantidadObjetivo":30,"productoSolicitadoID":"1","tipo":"EXTRAORDINARIA"}'
```
**Esperado:** `201 Created`. En los logs de Render de Donadores se ve la traza:
```
[Donadores -> Donaciones] Validando producto de necesidad (productoID=1)
[Donadores <- Donaciones] producto valido (productoID=1)
[Donadores -> Logistica] Consultando stock (productoID=1)
```
(Si Logística aún no expone `/stock`, el log muestra el WARN "no se pudo consultar stock" y la
necesidad **se crea igual** — es best-effort a propósito.)

### Paso 3 — Crear necesidad con producto INEXISTENTE (rechazo)

```bash
curl -X POST $DONADORES/necesidades -H "Content-Type: application/json" \
  -d '{"entidadID":"1","nivelDeUrgencia":5,"descripcion":"prueba","cantidadObjetivo":5,"productoSolicitadoID":"99999","tipo":"EXTRAORDINARIA"}'
```
**Esperado:** `400 Bad Request` con "El producto solicitado no existe en Donaciones: 99999".

### Paso 4 — Consultar / modificar / borrar la necesidad (endpoints nuevos)

```bash
curl $DONADORES/necesidades/1                        # GET → 200
curl -X PUT $DONADORES/necesidades/1 -H "Content-Type: application/json" \
  -d '{"nivelDeUrgencia":9,"descripcion":"URGENTE: 30 sillas","cantidadObjetivo":30,"productoSolicitadoID":"1","tipo":"EXTRAORDINARIA"}'
curl -X DELETE $DONADORES/necesidades/1              # → 204
```

---

## 3. Prueba desde el bot de Telegram

1. Correr el bot (ver `README.md` del repo `bot-telegram`): token de @BotFather +
   `DONADORES_URL` apuntando al módulo.
2. En Telegram:
```
/start
/soy_admin
/crearentidad Comedor Hogwarts;Calle 1;11-3000;h@mail.com
/altanecesidad 1;8;30 sillas tras inundacion;30;1;EXTRAORDINARIA     ← dispara el flujo E4 completo
/necesidad 1
/modificarnecesidad 1;9;URGENTE 30 sillas;30;1;EXTRAORDINARIA
/borrarnecesidad 1
/soy_donador
/registrarse Juan;Perez;30;juan@mail.com;40111222;Calle Falsa 123
/donadores
/estadisticas 1
```
Si el producto no existe, el bot responde el error del módulo (el 400 con el mensaje).

---

## 4. Qué NO se puede probar todavía (depende de otros módulos)

| Parte | Bloqueado por |
|-------|---------------|
| Respuesta real de `GET /stock/{productoID}` y `POST /asignaciones/solicitud` | **Logística** debe implementar stock + esos endpoints (contrato en `ENTREGA4.md`). Hasta entonces, el flujo es best-effort (la necesidad se crea, la asignación queda pendiente). |
| Cola de trabajo + workers | **Logística** (Parte B). |
| Cron-job y pérdida de progreso de misiones | **Incentivos**. |
