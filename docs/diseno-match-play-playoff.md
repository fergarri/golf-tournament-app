# Diseño: Match Play para los partidos de la llave de Playoff

## 1. Objetivo

Reemplazar el botón manual **"Vencedor"** de la vista "Llaves" (ver
`docs/diseno-llaves-playoff.md`, sección 9 "Siguiente fase") por un flujo real de
**Match Play**: el admin inicia una ronda de la llave, se genera un código único para esa
ronda, cada jugador entra con matrícula + código y carga su propia tarjeta hoyo a hoyo (con
el rival como marcador cruzado), y al entregar la tarjeta el sistema calcula el ganador según
las reglas de Match Play (con hándicap si la llave es HCP) y lo propaga automáticamente en el
bracket, reutilizando toda la lógica de avance ya existente (`markWinner` /
`propagateWinner`). El botón manual "Vencedor" **se mantiene** como override para casos
excepcionales (ver sección 8).

Este documento asume ya implementado todo lo descrito en `docs/diseno-llaves-playoff.md`.

## 2. Decisiones confirmadas por Fer (31/08 - 03/09/2026)

1. **handicap_mode**: Opción A — los golpes de hándicap se dan en los hoyos más difíciles
   según el índice de dificultad (`Hole.handicap`) de cada hoyo, con el criterio detallado en
   la sección 5.
2. **marker_model**: `opponent_as_marker` — el rival del partido es siempre quien hace de
   marcador cruzado. No hay paso de "asignar marcador": está fijo por la definición del
   partido.
3. **code_scope**: `one_code_per_round` — un solo código habilita a **todos** los jugadores de
   esa ronda de esa llave (no un código por partido).
4. **dual_bracket_player**: si un jugador se enfrenta con el mismo rival en la llave HCP y en
   la SCRATCH, son **dos partidos independientes** con **códigos de ronda independientes**
   (cada llave tiene su propia progresión de rondas).
5. **winner_automation**: `auto_set_winner` — el ganador se determina automáticamente al
   entregarse la tarjeta que deja el resultado decidido; no hace falta que el admin lo
   confirme a mano (pero puede corregirlo con el override manual).
6. **holes_config**: `configurable` — el admin define cuántos hoyos se juegan (9 o 18) al
   iniciar la ronda, igual que hoy se define en un torneo normal.
7. **all_square_tie**: si al terminar los hoyos de la ronda regular el partido sigue
   empatado, se juega **muerte súbita**: hoyo extra tras hoyo extra (repitiendo el recorrido
   configurado desde el hoyo 1) hasta que alguien gane uno. La tarjeta virtual soporta hoyos
   "extra" más allá de los configurados.
8. **Tees por género**: igual que en los torneos normales — al iniciar la ronda el admin
   selecciona el tee de Damas y el de Caballeros a usar, y cada jugador juega con el que
   corresponda a su sexo.
9. **Abandono ("Levantar Bola")**: la tarjeta del partido tiene los mismos botones que una
   tarjeta normal, **"Entregar Tarjeta"** y **"Levantar Bola"**. Si un jugador levanta la
   bola en cualquier momento, el rival gana el partido automáticamente sin más validaciones,
   y el propio ganador puede igualmente tocar "Entregar Tarjeta" después (acción formal, sin
   efecto en el resultado que ya quedó fijado).
10. **deliver_incomplete**: `yes_if_decided` — se puede entregar la tarjeta con menos hoyos
    que los configurados si el resultado ya está matemáticamente decidido (cierre anticipado,
    ej. "3&2"). Si no está decidido, no se puede entregar.
11. **winner_trigger**: `on_first_delivery` — el ganador se fija en el momento en que **la
    primera** de las dos tarjetas del partido se entrega, siempre que el resultado ya esté
    decidido en ese momento (por cierre anticipado, por resultado tras la ronda regular, o por
    muerte súbita). No depende de que ambos jugadores entreguen.
12. **scorecard_architecture**: `separate_tables` — no se reutiliza la tabla `scorecards` de
    torneos normales (Medal Play). Se crean tablas nuevas y dedicadas para partidos de
    Match Play, aunque el modelo hoyo a hoyo es análogo (ver sección 4).

## 3. Contexto actual (para no duplicar trabajo)

Ya existe (ver `docs/diseno-llaves-playoff.md`):

- `TournamentAdminPlayoffBracket` / `TournamentAdminPlayoffBracketSlot`: la llave y sus
  casilleros, con `is_winner` y avance por posición (`propagateWinner` en
  `TournamentAdminPlayoffBracketService`).
- `TournamentAdminPlayoffBracketService.markWinner(tournamentAdminId, bracketId, slotId)`:
  marca un casillero como ganador, resetea en cascada si corresponde, y propaga el jugador a
  la ronda siguiente. **Este método se reutiliza tal cual** como mecanismo de propagación:
  cuando el Match Play calcula un ganador, simplemente invoca `markWinner` con el `slotId`
  del jugador ganador. No se duplica lógica de bracket.
- Patrón de tarjeta hoyo a hoyo para torneos normales: `Scorecard` + `HoleScore`
  (`golpes_propio` / `golpes_marcador` / `validado` + `marcador_validado` agregado), con
  `ScorecardService` resolviendo tee según sexo, calculando `handicap_course` vía
  `HandicapConversionRepository.findByTeeAndHandicapIndex(teeId, handicapIndex)` (devuelve un
  `course_handicap` entero), e inicializando una fila de `hole_scores` vacía por cada hoyo a
  jugar.
- `TournamentAdmin.course`: el torneo administrativo (temporada) pertenece a una única cancha
  (`Course`), de la cual salen los `tees` y `holes` disponibles — igual que hoy usa
  `TournamentAdminPlayoffResultService`/`Course` para el resto de la app.
- Página `TournamentScorecardPage.tsx` + `TournamentAccessPage.tsx`: flujo de acceso por
  matrícula y carga de tarjeta hoyo a hoyo, con concordancia marcador (SSE + polling). El
  Match Play **reutiliza el patrón visual y de UX**, pero con un modelo de datos y unas
  páginas propias (más simples, porque el marcador ya está fijo: es el rival).

**Lo que NO existe todavía**: ningún concepto de "partido" (match), ronda de juego, código de
acceso para partidos, ni cálculo de resultado de Match Play. Todo lo de este documento es
funcionalidad nueva.

## 4. Modelo de datos propuesto

### Tabla `tournament_admin_playoff_round_sessions`

Una fila por cada vez que el admin toca **"Iniciar Ronda"** sobre una ronda de una llave.
Habilita el código de acceso para **todos los partidos de esa ronda**.

| Columna | Tipo | Notas |
|---|---|---|
| id | BIGSERIAL PK | |
| bracket_id | BIGINT FK → `tournament_admin_playoff_brackets(id)` ON DELETE CASCADE | |
| round_number | INTEGER | ronda de la llave que se está jugando (1, 2, 3...) |
| code | VARCHAR(20) UNIQUE | código de acceso, mismo estilo que `tournaments.codigo` (8 chars A-Z0-9) |
| status | VARCHAR(20) | `OPEN` (se puede seguir jugando/entrando) / `CLOSED` (todos los partidos terminaron) |
| tee_masculino_id | BIGINT FK → `course_tees(id)`, nullable | tee de Caballeros para esta ronda |
| tee_femenino_id | BIGINT FK → `course_tees(id)`, nullable | tee de Damas para esta ronda |
| cantidad_hoyos_juego | INTEGER | 9 o 18 |
| started_at | TIMESTAMP | |
| created_at / updated_at | TIMESTAMP | |

Constraint `UNIQUE (bracket_id, round_number)`: solo puede existir **una** sesión de ronda por
ronda de cada llave (coherente con "un código por ronda"). Si el admin necesita reiniciarla
(ej. se equivocó de tee), existe `POST /rounds/{roundSessionId}/reset` (ver sección 6), que
solo funciona si **ningún** partido de esa ronda tiene hoyos cargados todavía.

### Tabla `tournament_admin_playoff_matches`

Un partido = un enfrentamiento entre los dos casilleros hermanos de una ronda de la llave.

| Columna | Tipo | Notas |
|---|---|---|
| id | BIGSERIAL PK | |
| round_session_id | BIGINT FK → `tournament_admin_playoff_round_sessions(id)` ON DELETE CASCADE | |
| top_slot_id | BIGINT FK → `tournament_admin_playoff_bracket_slots(id)` | casillero par (slot_index par) |
| bottom_slot_id | BIGINT FK → `tournament_admin_playoff_bracket_slots(id)` | casillero impar hermano |
| player_a_id | BIGINT FK → `players(id)` | jugador del `top_slot_id` en el momento de iniciar la ronda |
| player_b_id | BIGINT FK → `players(id)` | jugador del `bottom_slot_id` en el momento de iniciar la ronda |
| status | VARCHAR(20) | `IN_PROGRESS` / `FINISHED` |
| winner_player_id | BIGINT FK → `players(id)`, nullable | se completa al finalizar |
| winner_slot_id | BIGINT FK → `tournament_admin_playoff_bracket_slots(id)`, nullable | `top_slot_id` o `bottom_slot_id`, para invocar `markWinner` |
| result_summary | VARCHAR(50), nullable | ej. `"3&2"`, `"2 up"`, `"1 up (19 hoyos)"`, `"Abandono"` |
| finished_at | TIMESTAMP, nullable | |
| created_at / updated_at | TIMESTAMP | |

Constraint `UNIQUE (round_session_id, top_slot_id)`. **Solo se crea un partido para pares de
casilleros donde ambos ya tienen jugador asignado** en el momento de iniciar la ronda (ver
regla de BYE en sección 6): si un casillero está vacío (BYE) o ambos están vacíos (rama
todavía no alcanzada), no se genera partido para ese par; el admin sigue usando el botón
manual "Vencedor" para esos casos.

### Tabla `tournament_admin_playoff_match_cards`

La "tarjeta" de **un jugador** dentro de un partido (hay exactamente 2 filas por partido).

| Columna | Tipo | Notas |
|---|---|---|
| id | BIGSERIAL PK | |
| match_id | BIGINT FK → `tournament_admin_playoff_matches(id)` ON DELETE CASCADE | |
| player_id | BIGINT FK → `players(id)` | |
| tee_id | BIGINT FK → `course_tees(id)` | resuelto según sexo del jugador + tees de la ronda |
| handicap_course | INTEGER, nullable | course handicap del jugador para ese tee (null si llave SCRATCH: no se usa) |
| status | VARCHAR(20) | `IN_PROGRESS` / `DELIVERED` / `CANCELLED` (levantó la bola) |
| delivered_at | TIMESTAMP, nullable | |
| created_at / updated_at | TIMESTAMP | |

Constraint `UNIQUE (match_id, player_id)`.

### Tabla `tournament_admin_playoff_match_hole_scores`

Análoga a `hole_scores`, pero **el "marcador" siempre es el rival del partido** (no hace
falta guardar quién marca a quién: se resuelve por el `match_id` de la tarjeta).

| Columna | Tipo | Notas |
|---|---|---|
| id | BIGSERIAL PK | |
| card_id | BIGINT FK → `tournament_admin_playoff_match_cards(id)` ON DELETE CASCADE | |
| hole_sequence | INTEGER | 1..N en el orden jugado; 1..`cantidad_hoyos_juego` son la ronda regular, valores mayores son hoyos de muerte súbita |
| hole_id | BIGINT FK → `holes(id)` | hoyo físico correspondiente: `((hole_sequence - 1) % cantidad_hoyos_juego) + (1 o el hoyo N según offset de 9 hoyos)` — ver sección 5 |
| golpes_propio | INTEGER, nullable | lo que el dueño de la tarjeta cargó para sí mismo |
| golpes_rival | INTEGER, nullable | lo que el dueño de la tarjeta observó/cargó para su rival en ese hoyo (marcador cruzado) |
| validado | BOOLEAN | `true` si `golpes_rival` de esta fila coincide con el `golpes_propio` que el rival cargó en su propia tarjeta para ese hoyo |
| created_at / updated_at | TIMESTAMP | |

Constraint `UNIQUE (card_id, hole_sequence)`. Las filas de la ronda regular
(`hole_sequence` 1..N) se crean vacías al iniciar la ronda, igual que hoy
`initializeHoleScores` para `Scorecard`. Las filas de muerte súbita (`hole_sequence` > N) se
crean **on-demand**, una por una, recién cuando hace falta jugar un hoyo extra (ambas tarjetas
del partido a la vez, para que los dos jugadores puedan cargar ese hoyo).

**Cálculo del resultado**: se hace siempre a partir de `golpes_propio` de **ambas** tarjetas
del partido (fuente de verdad), nunca de `golpes_rival` — ese campo es sólo para la
validación cruzada visual (semáforo verde/rojo/pendiente, igual que en tarjetas normales) que
condiciona poder entregar (ver sección 5).

## 5. Reglas de Match Play (algoritmo)

### 5.1. Hándicap y asignación de golpes por hoyo

- Si la llave es `SCRATCH`: nadie recibe golpes. Se compara `golpes_propio` bruto en cada
  hoyo.
- Si la llave es `HCP`: al iniciar la ronda, para cada jugador se resuelve su
  `handicap_course` igual que en una tarjeta normal
  (`HandicapConversionRepository.findByTeeAndHandicapIndex(tee del jugador según sexo,
  player.handicapIndex)` → `courseHandicap` entero; si `cantidad_hoyos_juego == 9` se divide
  por 2 y se redondea al entero más cercano, igual criterio que usa hoy `ScorecardService`
  para 9 hoyos).
- **Diferencia de hándicap**: `strokes = |handicapCourse(A) - handicapCourse(B)|`. El jugador
  con el `handicap_course` más alto recibe `strokes` golpes en total a lo largo de la ronda.
- **En qué hoyos se dan los golpes (criterio adoptado)**: se ordenan **solo los hoyos que se
  van a jugar** (los 9 o 18 de `cantidad_hoyos_juego`) por su `Hole.handicap` (índice de
  dificultad) de forma ascendente (1 = hoyo más difícil de esa selección). Al jugador con más
  hándicap se le da 1 golpe en cada uno de los `strokes` hoyos más difíciles de esa lista
  (empezando por el rank 1). Si `strokes` supera la cantidad de hoyos jugados, se repite el
  ciclo dando un golpe extra adicional a partir del hoyo más difícil otra vez (igual que las
  tablas de asignación de golpes estándar en 18 hoyos cuando el hándicap supera 18).
  *(Nota de implementación: como el proyecto no tiene una tabla de "índice de hándicap para 9
  hoyos" separada, se recalcula el ranking de dificultad usando únicamente los hoyos
  efectivamente jugados. Es una decisión pragmática — si en el futuro se define una tabla de
  asignación oficial para 9 hoyos, se puede sustituir este cálculo sin tocar el resto del
  modelo.)*
- El "neto" de un jugador en un hoyo = `golpes_propio - golpes_recibidos_en_ese_hoyo` (0 o 1,
  salvo hándicaps muy altos donde puede ser 2 en el mismo hoyo tras repetir el ciclo).
- En hoyos de muerte súbita (`hole_sequence > cantidad_hoyos_juego`), se sigue aplicando la
  misma asignación: el hoyo físico usado se determina como
  `((hole_sequence - 1) % cantidad_hoyos_juego)` y se le aplican los golpes que le
  correspondían a ese mismo hoyo en la ronda regular (si ese hoyo daba golpe en la ronda
  regular, lo sigue dando en la repetición de muerte súbita).

### 5.2. Determinación de hoyo ganado/perdido/empatado

Para cada `hole_sequence` donde **ambas** tarjetas tienen `golpes_propio` cargado:
`netoA` vs `netoB` → gana quien tenga menos golpes netos; empatan si son iguales ("hoyo
partido").

### 5.3. Cierre anticipado (ej. "3&2")

Después de procesar cada hoyo de la ronda regular donde ambas tarjetas ya cargaron
`golpes_propio`, se recalcula: `diferencia = holesGanadosA - holesGanadosB`,
`hoyosRestantes = cantidad_hoyos_juego - hoyosJugadosHastaAhora`. Si
`|diferencia| > hoyosRestantes`, el partido está **decidido**: gana quien tenga la ventaja,
con margen `|diferencia|` y hoyos restantes `hoyosRestantes` (formato `"3&2"`).

### 5.4. Resultado tras la ronda regular completa

Si se llega al último hoyo de la ronda regular (`hole_sequence == cantidad_hoyos_juego`) con
ambas tarjetas cargadas y no hubo cierre anticipado:

- Si `holesGanadosA != holesGanadosB`: gana quien tenga más hoyos ganados, resultado
  `"N up"` (ej. `"2 up"`), sin margen de hoyos restantes (ya no quedan).
- Si son iguales: el partido sigue en **muerte súbita** (ver 5.5). No se puede entregar
  todavía (ver 5.6).

### 5.5. Muerte súbita

Se juega un hoyo extra a la vez (`hole_sequence = cantidad_hoyos_juego + 1`, `+2`, ...),
repitiendo el recorrido desde el hoyo 1 configurado. Cada vez que ambos jugadores cargan ese
hoyo extra: si hay un ganador neto (no empatan), el partido queda decidido con resultado
`"1 up (N+1 hoyos)"` (indicando cuántos hoyos se jugaron en total). Si empatan, se habilita
automáticamente el siguiente hoyo extra y se sigue.

### 5.6. Cuándo se puede "Entregar Tarjeta"

Un jugador puede entregar su tarjeta del partido si, con los datos cargados **hasta el
momento** (por ambos jugadores), el partido ya está matemáticamente decidido según 5.3, 5.4 o
5.5. Si todavía no está decidido (faltan hoyos por cargar, o hay empate exacto en la ronda
regular sin haber jugado el hoyo extra), el botón "Entregar Tarjeta" devuelve un error
explicando qué falta.

Además, al igual que en tarjetas normales, se exige **validación cruzada**: para cada hoyo
donde el jugador que quiere entregar cargó `golpes_propio`, el campo `golpes_rival` que él
mismo cargó sobre su rival en ese hoyo debe coincidir con el `golpes_propio` que el rival
cargó en su propia tarjeta (si el rival ya lo cargó). No hace falta que el rival haya cargado
absolutamente todos los hoyos: solo se valida hasta donde ambos coinciden en haber jugado.

### 5.7. Fijar el ganador (`winner_trigger: on_first_delivery`)

Al entregarse la **primera** tarjeta del partido y confirmarse que el resultado está decidido
(5.6): se calcula `winner_player_id`, `winner_slot_id`, `result_summary`, se marca
`status = FINISHED` y `finished_at` en el partido, y se invoca
`TournamentAdminPlayoffBracketService.markWinner(tournamentAdminId, bracketId,
winnerSlotId)` para propagar el resultado en la llave (reutilizando toda la lógica de
reseteo en cascada / avance ya existente). La tarjeta que se entregó pasa a `DELIVERED`. La
tarjeta del rival queda como está (`IN_PROGRESS`), pero **ya no admite más carga de hoyos**
(el partido está `FINISHED`): el rival solo puede tocar "Entregar Tarjeta" para dejar su
propia tarjeta también en `DELIVERED` (acción formal sin efecto en el resultado) o "Levantar
Bola" (sin efecto real, dado que el partido ya terminó — la UI puede simplemente ocultar
ambos botones y mostrar el resultado final una vez `FINISHED`).

### 5.8. Abandono ("Levantar Bola")

En cualquier momento (con o sin hoyos cargados), un jugador puede tocar "Levantar Bola" en su
propia tarjeta de partido. Efecto inmediato, sin más validaciones:
`card.status = CANCELLED`, `match.winner_player_id` = el rival, `match.winner_slot_id` = el
slot del rival, `match.result_summary = "Abandono"`, `match.status = FINISHED`, y se invoca
`markWinner` igual que en 5.7. El rival puede luego tocar "Entregar Tarjeta" libremente (ya
descrito en 5.7).

## 6. Backend (Controller → Service → Repository)

### Endpoints de administración (bajo `/tournament-admin/{tournamentAdminId}/stages/playoff-brackets`, mismo controller/nivel que el resto de la llave)

- `POST /{bracketId}/rounds/{roundNumber}/start` — body
  `{ teeMasculinoId, teeFemeninoId, cantidadHoyosJuego }`. Requiere llave `CONFIRMED`.
  Recorre los pares de casilleros hermanos de esa ronda; para cada par donde **ambos**
  casilleros tienen jugador asignado, crea el `TournamentAdminPlayoffMatch` con sus 2
  `TournamentAdminPlayoffMatchCard` (resolviendo tee/hándicap por jugador) y las filas vacías
  de `hole_scores` para los `cantidadHoyosJuego` configurados. Genera y devuelve el `code`
  de la nueva `TournamentAdminPlayoffRoundSession` (`status = OPEN`). Falla si ya existe una
  sesión para esa `(bracketId, roundNumber)` — hay que usar `reset` primero. Si no hay ningún
  par con ambos casilleros ocupados, devuelve error ("todavía no hay partidos para iniciar en
  esta ronda").
- `GET /{bracketId}/rounds/{roundNumber}` — devuelve la sesión de ronda (si existe) con sus
  partidos y el estado en vivo de cada uno (para mostrar el chip en la UI admin sin tener que
  ir partido por partido).
- `POST /rounds/{roundSessionId}/reset` — borra la sesión de ronda y sus partidos/tarjetas,
  solo si **ningún** partido de esa ronda tiene algún hoyo cargado (`golpes_propio` no nulo en
  ninguna fila). Devuelve error explicando que hay que resolver los partidos en curso antes
  (no se puede perder progreso ya jugado desde acá; para eso está el override manual de
  "Vencedor", que primero requeriría deshacer manualmente si hiciera falta — caso extremo,
  fuera del flujo normal).
- `PUT /matches/{matchId}/winner` *(alias de conveniencia, opcional)* — no se agrega: el
  override manual sigue siendo el mismo `PUT /{bracketId}/slots/{slotId}/winner` /
  `DELETE .../winner` que ya existe para la llave (sección 8). No hace falta un endpoint
  nuevo: si el admin fuerza un ganador manualmente sobre un casillero que tiene un partido de
  Match Play en curso, el partido de Match Play queda "huérfano" (ver sección 8).

### Endpoints públicos (bajo `/public/playoff-match-rounds`, sin auth — agregar patrón a `PUBLIC_PATHS` del frontend, ya cubierto por `/public/**` en `SecurityConfig` del backend)

- `POST /public/playoff-match-rounds/{code}/access` — body `{ matricula }`. Busca, entre los
  partidos de la sesión de ronda con ese código, la tarjeta cuyo jugador tiene esa matrícula.
  Devuelve `{ matchId, cardId, tournamentAdminName, roundName, opponentName, ... }` para que el
  frontend navegue a la pantalla del partido. Error claro si la matrícula no participa en
  ningún partido de esa ronda, o si el código no existe/no está `OPEN`.
- `GET /public/playoff-match-rounds/{code}/matches/{matchId}?matricula=...` — devuelve el
  estado completo del partido: nombres y hándicaps de ambos jugadores, hoyos configurados
  (par, índice de hándicap, golpes recibidos por cada jugador en cada hoyo), los
  `golpes_propio`/`golpes_rival`/`validado` de **ambas** tarjetas (necesario para pintar la
  tarjeta completa, como hoy se ve la fila "TU" y la fila del marcador), la tabla en vivo
  (hoyos ganados por cada uno, hoyo actual, si está decidido y con qué resultado), y el
  `matricula` sirve para validar que quien pide el estado es uno de los dos jugadores del
  partido (403/400 si no).
- `PUT /public/playoff-match-rounds/{code}/matches/{matchId}/holes` — body
  `{ matricula, holeScores: [{ holeSequence, golpesPropio, golpesRival }] }`. Actualiza la
  tarjeta del jugador identificado por `matricula` dentro de ese partido. Si hace falta
  extender a un hoyo de muerte súbita todavía no creado, lo crea on-demand para **ambas**
  tarjetas del partido en esta misma llamada.
- `POST /public/playoff-match-rounds/{code}/matches/{matchId}/deliver` — body
  `{ matricula }`. Aplica las reglas 5.6/5.7. Si el partido pasa a `FINISHED` en esta llamada,
  invoca `markWinner` sobre el bracket correspondiente.
- `POST /public/playoff-match-rounds/{code}/matches/{matchId}/concede` — body
  `{ matricula }` ("Levantar Bola"). Aplica la regla 5.8.

## 7. Frontend

### Administración (`TournamentAdminBracketsPage.tsx`)

- En el encabezado de cada ronda (columna) que ya tenga **todos sus pares con ambos
  casilleros asignados** y la llave `CONFIRMED`, se agrega un botón **"Iniciar Ronda"**. Abre
  un modal (`Modal.tsx`) para elegir tee de Damas, tee de Caballeros y cantidad de hoyos (9 o
  18) — mismos selects que se usan hoy para configurar un torneo normal.
- Al confirmar, se muestra el código generado (mismo patrón visual que el código de un
  torneo: destacado, con botón "Copiar") y un link directo `/playoff-match/{code}` para
  compartir.
- Si la ronda ya tiene una sesión iniciada, en lugar de "Iniciar Ronda" se muestra el código
  vigente (con opción de volver a copiarlo) y, por cada partido, un **chip de estado** entre
  los dos casilleros: `"En curso — 2 arriba, hoyo 5"` mientras se juega, o el
  `result_summary` (ej. `"3&2"`) cuando termina, con un tono verde para indicar que ya
  propagó el ganador. Si no hay partido para ese par (fue un BYE), no se muestra chip.
- El botón manual "Vencedor" en cada casillero se sigue mostrando siempre (ver sección 8),
  incluso si hay un partido de Match Play en curso.

### Vista pública de la llave (`PublicTournamentAdminBracketsPage.tsx`)

- Mismo chip de estado en vivo entre casilleros enfrentados, en modo solo lectura (sin botón
  "Iniciar Ronda" ni "Vencedor", igual que hoy).

### Nueva página de acceso: `PlayoffMatchAccessPage.tsx` — ruta `/playoff-match/:code`

- Formulario simple: número de matrícula. Al enviar, llama a
  `POST /public/playoff-match-rounds/{code}/access` y navega a
  `/playoff-match/:code/match/:matchId` pasando `matricula` en el router state (mismo patrón
  que `TournamentAccessPage` → `TournamentScorecardPage`).
- No hay selección de tee/hoyos (ya los fijó el admin al iniciar la ronda).

### Nueva página de tarjeta de partido: `PlayoffMatchScorecardPage.tsx` — ruta `/playoff-match/:code/match/:matchId`

- Tabla hoyo a hoyo con el mismo estilo visual que `TournamentScorecardPage.tsx`: fila "TU"
  (`golpes_propio` propio, editable) y fila con el **nombre del rival** (en vez de "MARCAR
  A...", ya que el rival está fijo) mostrando `golpes_rival` (lo que este jugador cargó sobre
  el rival), con el mismo semáforo de concordancia (verde/rojo/pendiente) que hoy.
- Fila adicional de "golpes recibidos" (marca con un punto/ícono el hoyo donde el jugador
  recibe golpe de hándicap), solo si la llave es `HCP`.
- Encabezado con marcador en vivo: `"Vas 2 arriba"` / `"Vas 1 abajo"` / `"Igualados"`,
  actualizado tras cada guardado (auto-guardado con debounce, igual criterio que
  `TournamentScorecardPage`, sin necesidad de SSE — con polling cada 30s para reflejar cambios
  del rival es suficiente, dado que ambos están online cargando en el mismo momento).
- Si la ronda regular termina empatada, se agrega dinámicamente una columna "Hoyo extra 1",
  "Hoyo extra 2", etc., a medida que hace falta.
- Botones flotantes **"Levantar Bola"** y **"Entregar Tarjeta"**, mismo componente visual que
  hoy. Al entregar, si el backend devuelve error de "todavía no está decidido", se muestra con
  `Modal.tsx` (nunca `alert()`).
- Cuando el partido está `FINISHED` (ya sea porque este jugador lo entregó, porque lo entregó
  el rival, o por abandono), la tarjeta pasa a modo solo lectura con el resultado final
  destacado (`"Ganaste 3&2"` / `"Perdiste 2 up"` / `"Ganaste por abandono del rival"`).

## 8. Convivencia con el override manual "Vencedor"

El botón manual "Vencedor" de `docs/diseno-llaves-playoff.md` (sección 3.6) **no desaparece**:
sigue siendo la vía de excepción para casos donde no tiene sentido cargar un partido completo
(ej. un BYE, un jugador que nunca se presentó y hay que resolverlo administrativamente sin
tarjeta). Si el admin marca "Vencedor" manualmente sobre un casillero que tiene un partido de
Match Play en curso (`status = IN_PROGRESS`), ese partido queda **huérfano**: no se cierra
automáticamente ni se borra, simplemente deja de tener efecto porque el bracket ya avanzó por
la vía manual. La UI debe advertir con `Modal.tsx` antes de permitir marcar "Vencedor" a mano
si existe un partido de Match Play `IN_PROGRESS` para ese casillero ("Hay un partido de Match
Play en curso para este cruce. Si marcás el vencedor a mano, el resultado de la tarjeta que se
está jugando quedará sin efecto. ¿Confirmás?").

## 9. Fuera de alcance de esta entrega

- Notificaciones (WhatsApp, etc.) de resultados de partidos.
- Reasignación de "quién marca a quién" (no aplica: siempre es el rival).
- Tabla oficial de asignación de golpes de hándicap específica para 9 hoyos (se usa el
  criterio pragmático de la sección 5.1).
- Reabrir/editar un partido ya `FINISHED` sin pasar por el override manual de "Vencedor" (no
  hay un "deshacer entrega" de Match Play en esta entrega; para corregir un error ya
  propagado, se usa `DELETE /{bracketId}/slots/{slotId}/winner` existente y luego el override
  manual si hace falta).
