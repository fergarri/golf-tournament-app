# Diseño: Vista "Llaves" de Playoff

## 1. Objetivo

Agregar una vista nueva llamada **"Llaves"** donde el administrador arma manualmente el
cuadro de eliminación directa (bracket) del playoff: ubica a cada clasificado en una posición
de la llave (arrastrando con el mouse), confirma la configuración, y después va marcando el
ganador de cada partido para que avance automáticamente a la siguiente ronda.

## 2. Contexto actual (para no duplicar trabajo)

Ya existe en el proyecto:

- `TournamentAdminScoringConfig`: define cuántos clasifican al playoff
  (`qualifiedPlayoffPositions`), cuántos por el track "Scratch" (`qualifiedPlayoffPositionsScratch`,
  solo CLASICO) y el modo de clasificación (`hcpQualifiedMode`: `GLOBAL` o `PER_CATEGORY`).
- `TournamentAdminPlayoffResult`: ya calcula quién queda `qualified = true` y en qué
  `position`, separado por `scoreType` (`HCP` / `SCRATCH`) y por `categoryId` cuando el modo
  es `PER_CATEGORY`.
- `TournamentAdminPlayoffResultService.calculateResults(...)`: arma esa lista de clasificados
  cada vez que se recalculan las etapas.
- Vistas existentes: `TournamentAdminPlayoffResultsPage.tsx` (admin, tabla de resultados) y
  `PublicTournamentAdminPlayoffResultsPage.tsx` (pública).

**Lo que NO existe todavía**: ningún concepto de "llave"/bracket ni de partidos (matches).
Hoy solo hay un ranking plano. Todo lo de este documento es funcionalidad nueva.

## 3. Alcance funcional (lo que pidió el usuario)

1. Vista nueva "Llaves", accesible desde la vista de Play-off y desde la administración de
   etapas.
2. Muestra el cuadro de eliminación directa (dieciseisavos / octavos / cuartos / semifinal /
   final, según corresponda), **inicialmente vacío**: ningún casillero tiene jugador hasta que
   se asigne.
3. Siempre hay **exactamente dos llaves**: **"Con HCP"** y **"SCRATCH"**, mostradas como
   tabs. No se divide por categoría: la llave "Con HCP" agrupa a todos los clasificados con
   `scoreType = HCP` sin importar su categoría (aunque el torneo esté en modo
   `PER_CATEGORY`), y la llave "SCRATCH" agrupa a los clasificados con `scoreType = SCRATCH`
   (solo aplica a CLASICO, y solo si `qualifiedPlayoffPositionsScratch` > 0).
4. Panel de asignación: a la izquierda, listado de jugadores clasificados sin ubicar, cada uno
   en una celda que se puede **arrastrar con el mouse** hasta la posición de la llave deseada.
5. Botón **"Confirmar"**: guarda la configuración de la llave y da por iniciada esa etapa del
   playoff. Se puede seguir **editando después** de confirmar (mover un jugador de posición).
6. Cada jugador de un partido tiene un botón **"Vencedor"**. Al tocarlo:
   - su casillero se pinta **verde**,
   - el casillero del rival se pinta **rojo**,
   - el jugador ganador pasa automáticamente al casillero correspondiente de la siguiente
     ronda.
7. Vista **pública** de solo lectura con las mismas dos tabs ("Con HCP" / "SCRATCH") para que
   los jugadores vean el estado de la llave.

## 4. Modelo de datos propuesto

### Tabla `tournament_admin_playoff_brackets`

Siempre a lo sumo dos filas por torneo: una con `score_type = HCP` y otra con
`score_type = SCRATCH` (esta última solo si corresponde clasificación Scratch).

| Columna | Tipo | Notas |
|---|---|---|
| id | BIGSERIAL PK | |
| tournament_admin_id | BIGINT FK | |
| score_type | VARCHAR(20) | `HCP` o `SCRATCH` — es el único criterio que separa llaves. No hay `category_id`: la llave agrupa a todos los clasificados de ese `score_type` sin importar categoría. |
| size | INTEGER | cantidad de casilleros en la primera ronda (siempre potencia de 2: 4, 8, 16, 32...) |
| status | VARCHAR(20) | `DRAFT` (armando) / `CONFIRMED` (en juego) |
| created_at / updated_at | TIMESTAMP | |

### Tabla `tournament_admin_playoff_bracket_slots`

Un casillero de la llave. La ronda siguiente se arma solo por posición: el casillero
`slot_index = k` de la ronda `round_number + 1` es el ganador del partido entre
`slot_index = 2k` y `slot_index = 2k + 1` de la ronda `round_number`. No hace falta guardar
relaciones explícitas entre rondas.

| Columna | Tipo | Notas |
|---|---|---|
| id | BIGSERIAL PK | |
| bracket_id | BIGINT FK | |
| round_number | INTEGER | 1 = primera ronda (la que arman a mano), 2, 3... hasta la final |
| slot_index | INTEGER | posición dentro de la ronda, 0-based |
| player_id | BIGINT FK, nullable | null = vacío (sin asignar todavía, o BYE) |
| is_winner | BOOLEAN | default false. Se marca true al tocar "Vencedor" |
| created_at / updated_at | TIMESTAMP | |

Con `size` y `round_number` se calcula el nombre de la ronda para mostrar en la UI:

| size (ronda 1) | Nombre ronda 1 | Rondas siguientes |
|---|---|---|
| 32 | Dieciseisavos de Final | Octavos → Cuartos → Semifinal → Final |
| 16 | Octavos de Final | Cuartos → Semifinal → Final |
| 8 | Cuartos de Final | Semifinal → Final |
| 4 | Semifinal | Final |

## 5. Backend (Controller → Service → Repository, como el resto del proyecto)

Endpoints propuestos, bajo `/api/tournament-admin/{tournamentAdminId}/stages/playoff-brackets`
(mismo nivel que `/stages/playoff-results`, para ser consistente con lo ya existente):

- `GET /` → devuelve las llaves del torneo que ya existen (HCP y, si corresponde, SCRATCH),
  cada una con su árbol completo de rondas y casilleros, más la lista de clasificados de ese
  `score_type` aún sin ubicar. Si una llave todavía no fue generada, simplemente no aparece en
  la respuesta (el frontend muestra un botón "Generar Llave").
- `POST /generate?scoreType=HCP|SCRATCH` → genera la estructura vacía de la llave (todos los
  casilleros de todas las rondas, **incluida la ronda 1, sin ningún jugador asignado**): el
  tamaño se calcula a partir de los clasificados (`qualified = true`) de ese `score_type` sin
  importar categoría, pero la ubicación de cada jugador en un casillero es 100% manual — no
  hay asignación automática. Todos los clasificados aparecen en el panel "Sin ubicar" hasta
  que el admin los arrastra a la llave. Si se omite `scoreType`, genera todas las llaves aplicables (HCP siempre;
  SCRATCH solo si el torneo es CLASICO y tiene clasificación Scratch configurada). Es
  **idempotente y no destructivo**: si la llave ya existe en `DRAFT`, la reemplaza; si está
  `CONFIRMED`, la deja intacta (no hace nada) — para regenerarla desde cero hay que usar antes
  `POST /{bracketId}/reset`.
- `PUT /{bracketId}/slots` → guarda la asignación de jugadores a los casilleros de la **ronda
  1** (recibe una lista de `{slotId, playerId}`, `playerId` puede ser `null` para dejar el
  casillero vacío). Válido tanto en `DRAFT` como en `CONFIRMED` (edición post-confirmación).
- `POST /{bracketId}/confirm` → pasa la llave de `DRAFT` a `CONFIRMED`. Requiere que no queden
  clasificados sin ubicar (el panel de "sin asignar" debe estar vacío).
- `POST /{bracketId}/revert` → vuelve la llave de `CONFIRMED` a `DRAFT`. Solo permitido si
  **todavía no se marcó ningún "Vencedor"** en esa llave (el playoff no arrancó). Se usa, por
  ejemplo, antes de reasignar jugadores si cambiaron los clasificados tras un recálculo de
  puntos.
- `POST /{bracketId}/reset` → borra por completo la llave (estructura y casilleros), sin
  importar su estado ni si ya se jugaron partidos. Acción explícita y destructiva del admin
  (el frontend debe confirmar con `Modal.tsx` avisando que se pierden los partidos jugados);
  después de este llamado hay que volver a generar la llave con `POST /generate`.
- `PUT /{bracketId}/slots/{slotId}/winner` → marca ese casillero como ganador del partido,
  marca a su rival como perdedor, y escribe su `player_id` en el casillero correspondiente de
  la ronda siguiente. Requiere que la llave esté `CONFIRMED`. Si el partido ya tenía un
  vencedor marcado (se está corrigiendo un error), resetea en cascada lo que ya se había
  propagado a partir de ese casillero antes de propagar el nuevo resultado.
- `DELETE /{bracketId}/slots/{slotId}/winner` → deshace un "Vencedor" ya marcado: vuelve
  `is_winner = false` en ese casillero y resetea en cascada las rondas posteriores que
  dependían de él.
- `GET /public/tournament-admin/{tournamentAdminId}/playoff-brackets` (sin auth, mismo patrón
  que `PublicTournamentAdminPlayoffResultController`) → devuelve las llaves existentes en modo
  solo lectura, para la vista pública.

**Regla de BYE (confirmada):** si la cantidad de clasificados de un `score_type` no es
potencia de 2 (ej. 12), la llave se redondea al tamaño inmediato superior (16) y los
casilleros sobrantes de la ronda 1 quedan vacíos. El admin decide a mano quién queda con BYE
simplemente no ubicando rival en ese casillero, y puede tocar "Vencedor" en ese jugador igual
para que avance sin haber jugado.

**Regla de edición retroactiva (confirmada):** si se cambia el jugador de un casillero de una
ronda cuyo resultado ya se había propagado a rondas siguientes, `PUT /slots` debe **resetear
en cascada** todas las rondas posteriores que dependían de ese casillero (vaciar `player_id` e
`is_winner` de esos slots) para evitar inconsistencias, en vez de dejar datos viejos
mezclados con los nuevos. La misma lógica de reseteo en cascada aplica al deshacer un
"Vencedor" con `DELETE /winner`.

**Cálculo del tamaño (`size`) de la llave (confirmado):** se calcula contando cuántos
`TournamentAdminPlayoffResult` tienen `qualified = true` para ese `score_type` (suma real,
sumando todas las categorías si el modo es `PER_CATEGORY` — no se usa `qualifiedPlayoffPositions`
directamente), redondeado a la potencia de 2 inmediata superior.

**Interacción con "Calcular Puntos" (confirmada):** al recalcular resultados de playoff
(`POST /stages/playoff-results/calculate`), por cada llave existente de ese torneo:
- Si está `CONFIRMED` y **ya tiene** al menos un "Vencedor" marcado (el playoff arrancó): se
  **bloquea el recálculo completo** con un error explicando que hay que resolver la llave
  manualmente antes de recalcular puntos.
- Si está `CONFIRMED` pero **todavía no** se marcó ningún "Vencedor": se revierte
  automáticamente a `DRAFT` como parte del recálculo, para permitir reasignar jugadores si
  cambió la lista de clasificados.
- Si está en `DRAFT`, no se toca (sigue en `DRAFT`; el admin reasigna a mano si hace falta).

## 6. Frontend

- Página nueva de administración, por ejemplo `TournamentAdminBracketsPage.tsx`, en
  `frontend/src/pages/`.
- Acceso: un botón/link "Llaves" en `TournamentAdminPlayoffResultsPage.tsx` (vista de
  Play-off) y en `TournamentAdminStagesPage.tsx` (administración de etapas).
- Tabs fijas: **"Con HCP"** y **"SCRATCH"** (esta última solo visible si el torneo tiene
  clasificación Scratch configurada). Mismo componente `Tabs.tsx` que ya se usa en el resto
  de la app.
- Layout de cada llave:
  - **Izquierda**: columna con los jugadores clasificados de ese `score_type` todavía sin
    ubicar, cada uno en una celda arrastrable.
  - **Derecha**: el cuadro, una columna por ronda, con líneas de emparejamiento entre
    casilleros. Casillero vacío = placeholder gris con texto "Sin asignar".
- Arrastrar y soltar: se recomienda la librería **`@dnd-kit/core`** (liviana, mantenida,
  compatible con React 18 — a diferencia de `react-beautiful-dnd`, que está discontinuada).
- Botón **"Confirmar llave"**: usa el componente `Modal.tsx` existente para la confirmación
  (según las convenciones del proyecto, no `confirm()` del navegador).
- Después de confirmada, se puede seguir editando: mover un jugador de casillero dispara el
  guardado (mismo endpoint `PUT /slots`) y, si corresponde, el reseteo en cascada de rondas
  posteriores (ver regla en sección 5). Conviene avisar al admin con el `Modal.tsx` antes de
  confirmar una edición que vaya a borrar resultados ya cargados.
- Cada casillero con jugador asignado tiene el botón **"Vencedor"**. Al presionarlo: ese
  casillero queda verde, el casillero rival queda rojo, y el ganador aparece automáticamente
  en el casillero de la siguiente ronda (sin recargar la página, actualizando el estado local
  y confirmando contra el backend).

### Vista pública

- Página nueva `PublicTournamentAdminBracketsPage.tsx`, mismo patrón que
  `PublicTournamentAdminPlayoffResultsPage.tsx`.
- Mismas dos tabs, **"Con HCP"** y **"SCRATCH"**, en modo **solo lectura**: se ve el cuadro
  completo con los jugadores ubicados y los partidos ya definidos como ganados/perdidos
  (verde/rojo), pero sin ningún control de edición ni botón "Vencedor".

## 7. Decisiones confirmadas por Fer (29-30/08/2026)

1. **BYE cuando la cantidad de clasificados no es potencia de 2**: se redondea al tamaño de
   llave inmediato superior, casilleros sobrantes vacíos en ronda 1, el admin puede marcar
   "Vencedor" igual para avanzar a un jugador sin rival. *Confirmado.*
2. **Editar una ronda ya jugada**: se debe resetear en cascada la ronda siguiente (y las que
   dependan de ella) para evitar inconsistencias. *Confirmado.*
3. **Cantidad de llaves**: siempre dos, "Con HCP" y "SCRATCH" — no se abre una llave por
   categoría aunque el torneo esté en modo `PER_CATEGORY`. *Confirmado.*
4. **Vista pública**: sí, se agrega, con las mismas dos tabs "Con HCP" y "SCRATCH", en modo
   solo lectura. *Confirmado — pasa a estar dentro del alcance (ver sección 6).*
5. **Tamaño de llave, recálculo, reset y deshacer Vencedor**: ver reglas detalladas en la
   sección 5 (cálculo de `size` a partir del conteo real de `qualified = true`, bloqueo/
   reversión automática de llaves `CONFIRMED` al recalcular puntos, endpoint `reset` separado
   para regenerar una llave `CONFIRMED`, y endpoint para deshacer un "Vencedor"). *Confirmado
   30/08/2026.*
6. **"Vencedor" manual vs. automático por tarjeta**: por ahora el admin lo marca a mano con
   el botón "Vencedor". Está previsto para **corto plazo** (siguiente fase, no esta primera
   entrega) vincular cada partido de la llave a una tarjeta de puntuación real: los dos
   jugadores del match cargarían su tarjeta y, al entregarla, el sistema calcularía el
   ganador según las reglas de la modalidad "Match Play" (todavía sin definir). El modelo de
   datos de este documento **debe quedar preparado** para esa siguiente fase — el campo
   `is_winner` y el avance por posición siguen siendo válidos aunque el cálculo del ganador
   después pase a ser automático en vez de manual. Ver sección 9.

## 8. Fuera de alcance

- Notificaciones automáticas (WhatsApp, etc.) de resultados de la llave. No está previsto
  hacerlo.

## 9. Siguiente fase prevista (corto plazo, no incluida en esta entrega)

- Carga de scorecards para los partidos de playoff.
- Definir la modalidad de juego **"Match Play"** y sus reglas para determinar el ganador de
  un partido a partir de las dos tarjetas entregadas (hoyo a hoyo, con hándicap aplicado
  según corresponda).
- Reemplazar el botón manual "Vencedor" por el cálculo automático una vez entregadas ambas
  tarjetas del match, reutilizando el mismo campo `is_winner` y la misma lógica de avance de
  ronda descripta en la sección 5.
- Por tratarse de una fase cercana, conviene que Cursor tenga esto presente al nombrar
  clases/tablas de esta primera entrega (por ejemplo, evitar nombres que asuman que
  "Vencedor" siempre será una acción manual).
