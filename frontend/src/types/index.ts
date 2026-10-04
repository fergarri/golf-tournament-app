export interface User {
  email: string;
  role: string;
  permissions: string[];
  /** Club (course) asignado al usuario. null/undefined para superadmin (rol ADMIN, ve todos los clubes). */
  courseId?: number | null;
  courseName?: string | null;
}

export interface UserDetail {
  id: number;
  email: string;
  matricula?: string;
  role: string;
  courseId?: number | null;
  courseName?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  email: string;
  matricula?: string;
  password: string;
  role: string;
  /** Requerido cuando role=USER (club-admin). Ignorado para ADMIN (superadmin). */
  courseId?: number | null;
}

export interface UpdateUserRequest {
  email: string;
  matricula?: string;
  role: string;
  courseId?: number | null;
}

export interface ChangePasswordRequest {
  newPassword: string;
}

export interface LoginResponse {
  token: string;
  type: string;
  email: string;
  role: string;
  permissions: string[];
  courseId?: number | null;
  courseName?: string | null;
}

export interface Player {
  id: number;
  nombre: string;
  apellido: string;
  email?: string;
  matricula: string;
  fechaNacimiento?: string;
  sexo: 'M' | 'F';
  handicapIndex: number;
  telefono?: string;
  clubOrigen?: string;
  courseId?: number | null;
  courseName?: string | null;
  hcpActivo?: boolean;
}

export interface BulkUpdateAltaItem {
  matricula: string;
  nombre: string;
  apellido: string;
}

export interface BulkUpdateHandicapChangeItem {
  matricula: string;
  nombre: string;
  apellido: string;
  handicapAnterior: number;
  handicapNuevo: number;
}

export interface BulkUpdateResult {
  actualizados: number;
  creados: number;
  matriculasNoProcesadas: string[];
  altas: BulkUpdateAltaItem[];
  cambiosHandicapIndex: BulkUpdateHandicapChangeItem[];
}

export interface Course {
  id: number;
  nombre: string;
  pais: string;
  provincia?: string;
  ciudad?: string;
  cantidadHoyos: number;
  courseRating?: number;
  slopeRating?: number;
  tees: CourseTee[];
  holes: Hole[];
}

export interface CourseTee {
  id: number;
  courseId: number;
  nombre: string;
  grupo?: string;
  /** M = Caballeros, F = Damas */
  genero?: 'M' | 'F';
  active: boolean;
  /** Calificación (Course Rating) de Ida (hoyos 1-9), usada para el HCP Course de 9 hoyos */
  courseRatingIda?: number | null;
  /** Calificación (Course Rating) de Vuelta (hoyos 10-18) */
  courseRatingVuelta?: number | null;
  /** Slope Rating de Ida (hoyos 1-9), usado para el HCP Course de 9 hoyos */
  slopeRatingIda?: number | null;
  /** Slope Rating de Vuelta (hoyos 10-18) */
  slopeRatingVuelta?: number | null;
}

export interface Hole {
  id: number;
  numeroHoyo: number;
  par: number;
  handicap: number;
  distancesByTee: { [key: number]: number };
}

export interface HandicapConversion {
  id: number;
  hcpIndexFrom: number;
  hcpIndexTo: number;
  courseHandicap: number;
}

export interface TeeHandicapTable {
  teeId: number;
  nombre: string;
  grupo?: string;
  genero?: 'M' | 'F';
  active: boolean;
  conversions: HandicapConversion[];
}

export interface ImportHandicapConversionTeeResult {
  teeId: number;
  teeNombre: string;
  genero?: string;
  matchedRows: number;
  imported: boolean;
  message: string;
}

export interface ImportHandicapConversionResponse {
  tees: ImportHandicapConversionTeeResult[];
}

export interface MissingCourseTee {
  nombre: string;
  genero: 'M' | 'F' | string;
}

export interface PreviewHandicapImportResponse {
  missingTees: MissingCourseTee[];
}

export interface ImportNineHoleRatingsTeeResult {
  teeId?: number;
  teeNombre: string;
  genero?: string;
  imported: boolean;
  created: boolean;
  courseRatingIda?: number | null;
  courseRatingVuelta?: number | null;
  slopeRatingIda?: number | null;
  slopeRatingVuelta?: number | null;
  message: string;
}

export interface ImportNineHoleRatingsResponse {
  tees: ImportNineHoleRatingsTeeResult[];
}

export interface TournamentPrize {
  id: number;
  prizeType: 'LONG_DRIVER' | 'BEST_DRIVER' | 'BEST_APPROACH';
  winnerId?: number | null;
  winnerInscriptionId?: number | null;
  winnerName?: string | null;
}

export interface Tournament {
  id: number;
  nombre: string;
  codigo: string;
  tipo: string;
  modalidad: string;
  estado: string;
  courseId: number;
  courseName: string;
  cantidadHoyosJuego?: number | null;
  teeMasculinoId?: number | null;
  teeFemeninoId?: number | null;
  fechaInicio: string;
  fechaFin?: string;
  horarioInicio?: string | null;
  horarioCierre?: string | null;
  limiteInscriptos?: number;
  valorInscripcion?: number;
  doublePoints?: boolean;
  controlCruzado?: boolean;
  currentInscriptos: number;
  categories: TournamentCategory[];
  teeConfig?: TournamentTeeConfig;
  prizes?: TournamentPrize[];
  scoringConfig?: ScoringConfig | null;
  /** Presente si el torneo está asociado a una etapa de un Torneo Administrativo */
  tournamentAdminId?: number | null;
  stageId?: number | null;
  stageName?: string | null;
}

export interface TournamentCategory {
  id?: number;
  nombre: string;
  handicapMin: number;
  handicapMax: number;
  sexoCategoria?: 'M' | 'F' | 'X';
}

export interface TournamentTeeConfig {
  id?: number;
  courseTeeIdPrimeros9: number;
  courseTeeIdSegundos9?: number;
}

export interface Scorecard {
  id: number;
  tournamentId: number;
  playerId: number;
  playerName: string;
  markerId?: number;
  markerName?: string;
  handicapCourse?: number;
  teeId?: number | null;
  cantidadHoyosJuego?: number | null;
  status: string;
  deliveredAt?: string;
  holeScores: HoleScore[];
  totalScore?: number;
  totalPar: number;
  marcadorValidado?: boolean;
  markedPlayerScorecardStatus?: string;
}

export interface HoleScore {
  id: number;
  holeId: number;
  numeroHoyo: number;
  par: number;
  golpesPropio?: number;
  golpesMarcador?: number;
  validado: boolean;
  estadoConcordancia?: 'MATCH' | 'MISMATCH' | 'PENDING' | 'NONE';
}

export interface LeaderboardEntry {
  position: number;
  scorecardId: number;
  playerId: number;
  inscriptionId: number;
  playerName: string;
  matricula: string;
  clubOrigen?: string;
  categoryId?: number | null;
  categoryName?: string;
  scoreGross: number;
  scoreNeto: number;
  totalPar: number;
  scoreToPar: number;
  handicapCourse?: number;
  handicapIndex?: number;
  status?: string;
  pagado?: boolean;
}

export interface TournamentScore {
  scorecardId?: number;
  playerId: number;
  playerName: string;
  matricula: string;
  position?: number;
  handicapIndex?: number;
  handicapCourse?: number;
  scoreGross?: number;
  scoreNeto?: number;
  status: string;
  birdieCount: number;
  eagleCount: number;
  aceCount: number;
  positionPoints: number;
  birdiePoints: number;
  eaglePoints: number;
  acePoints: number;
  participationPoints: number;
  totalPoints: number;
  /** GLOBAL, CATEGORY o SCRATCH */
  scoreType?: string;
  /** ID de categoría (solo cuando scoreType = CATEGORY) */
  categoryId?: number;
  /** Nombre de categoría (solo cuando scoreType = CATEGORY) */
  categoryName?: string;
}

/** Alias para compatibilidad con las páginas de Frutales existentes */
export type FrutalesScore = TournamentScore;

export interface InscriptionResponse {
  inscriptionId: number;
  player: Player;
  categoryName?: string | null;
  handicapCourse?: number;
  message?: string;
}

// Tournament Admin types

export interface TournamentAdmin {
  id: number;
  nombre: string;
  fecha: string;
  tipo: string;
  valorInscripcion: number;
  cantidadCuotas: number;
  estado: string;
  currentInscriptos: number;
  totalRecaudado: number;
  courseId: number;
  courseName: string;
}

export interface TournamentRelationOption {
  id: number;
  nombre: string;
  fechaInicio?: string;
  related: boolean;
}

export interface TournamentAdminDetail {
  id: number;
  nombre: string;
  /** CLASICO o FRUTALES */
  tipo?: string;
  fecha: string;
  cantidadCuotas: number;
  valorInscripcion: number;
  currentInscriptos: number;
  totalRecaudado: number;
  canManageStages: boolean;
  inscriptions: TournamentAdminInscriptionDetail[];
}

export interface TournamentAdminInscriptionDetail {
  inscriptionId: number;
  playerId: number;
  playerName: string;
  telefono?: string;
  email?: string;
  payments: TournamentAdminPaymentDetail[];
}

export interface TournamentAdminPaymentDetail {
  paymentId: number;
  cuotaNumber: number;
  pagado: boolean;
}

export interface ImportAdminInscriptionsResult {
  relatedPendingTournaments: number;
  importedCount: number;
  skippedAlreadyInscribed: number;
  skippedByCapacity: number;
}

export interface ExportTournamentInscriptionsResult {
  tournamentAdminId: number;
  tournamentAdminNombre: string;
  importedCount: number;
  skippedAlreadyInscribed: number;
}

export interface ScoringPositionPoints {
  position: number;
  points: number;
}

export interface ScoringConfig {
  id?: number;
  tournamentAdminId: number;
  birdiePoints: number;
  eaglePoints: number;
  acePoints: number;
  participationPoints: number;
  remainingPositionsPoints: number;
  qualifiedPlayoffPositions: number;
  /** Clasificados Sin HCP (Scratch). 0 = sin clasificación, tab Scratch oculto. Solo CLASICO. */
  qualifiedPlayoffPositionsScratch: number;
  /** GLOBAL o PER_CATEGORY. Solo relevante para torneos CLASICO. */
  hcpQualifiedMode: string;
  tieBreakMode: string;
  /** Si es true, la fecha siguiente descuenta los golpes bajo par de la fecha previa. */
  discountUnderPar?: boolean;
  /** FIRST_PLACE o ALL_UNDER_PAR. */
  underParDiscountMode?: string | null;
  /** Porcentaje de HCP Course para el Match Play Con HCP. 100 = completo. */
  matchPlayHcpPercent?: number;
  positionPoints: ScoringPositionPoints[];
}

export interface SaveScoringConfigRequest {
  birdiePoints: number;
  eaglePoints: number;
  acePoints: number;
  participationPoints: number;
  remainingPositionsPoints: number;
  qualifiedPlayoffPositions: number;
  /** Clasificados Sin HCP (Scratch). 0 = sin clasificación. */
  qualifiedPlayoffPositionsScratch: number;
  /** GLOBAL o PER_CATEGORY. */
  hcpQualifiedMode: string;
  tieBreakMode: string;
  discountUnderPar?: boolean;
  /** FIRST_PLACE o ALL_UNDER_PAR. */
  underParDiscountMode?: string | null;
  /** Porcentaje de HCP Course para el Match Play Con HCP. 100 = completo. */
  matchPlayHcpPercent?: number;
  positionPoints: ScoringPositionPoints[];
}

export interface TournamentAdminStage {
  id: number;
  tournamentAdminId: number;
  nombre: string;
  fechasCount: number;
  createdAt: string;
  tournamentIds: number[];
  tournaments: TournamentAdminStageTournament[];
  /** Resultado de la auto-inscripción al agregar fechas nuevas a la etapa (null si no se agregaron fechas). */
  autoInscriptionResult?: ImportAdminInscriptionsResult | null;
}

export interface TournamentAdminStageTournament {
  id: number;
  nombre: string;
  fechaInicio: string;
  doublePoints: boolean;
}

export interface TournamentAdminStageCategoryRows {
  categoryId: number;
  categoryName: string;
  handicapMin: number;
  handicapMax: number;
  rows: TournamentAdminStageBoardRow[];
}

export interface TournamentAdminStageBoard {
  stageId: number;
  tournamentAdminId: number;
  stageName: string;
  stageCreatedAt: string;
  /** FRUTALES o CLASICO */
  tipo?: string;
  tournaments: TournamentAdminStageBoardTournament[];
  /** Filas Con HCP (para FRUTALES es el único listado) */
  rows: TournamentAdminStageBoardRow[];
  /** Filas Sin HCP / Scratch — solo para tipo CLASICO */
  scratchRows?: TournamentAdminStageBoardRow[] | null;
  /** Filas agrupadas por categoría — solo para tipo CLASICO */
  categoryRows?: TournamentAdminStageCategoryRows[] | null;
}

export interface TournamentAdminStageBoardTournament {
  tournamentId: number;
  tournamentName: string;
  fechaInicio: string;
  doublePoints: boolean;
}

export interface TournamentAdminStageBoardRow {
  playerId: number;
  playerName: string;
  handicapIndex?: number;
  totalPoints: number;
  position?: number;
  pointsByTournament: Record<number, number>;
}

export interface TournamentAdminPlayoffResults {
  tournamentAdminId: number;
  /** FRUTALES o CLASICO */
  tipo?: string;
  stages: TournamentAdminPlayoffStageColumn[];
  /** Filas Con HCP (para FRUTALES es el único listado) */
  rows: TournamentAdminPlayoffResultRow[];
  /** Filas Sin HCP / Scratch — solo para tipo CLASICO */
  scratchRows?: TournamentAdminPlayoffResultRow[] | null;
  /** Leyenda de categorías. Solo para CLASICO con hcpQualifiedMode=PER_CATEGORY. */
  categoryLegend?: TournamentAdminPlayoffCategoryLegend[] | null;
}

export interface TournamentAdminPlayoffStageColumn {
  stageId: number;
  code: string;
  stageName: string;
  stageCreatedAt: string;
}

export interface TournamentAdminPlayoffResultRow {
  playerId: number;
  playerName: string;
  pointsByStage: Record<number, number>;
  totalPoints: number;
  position: number;
  qualified: boolean;
  /** Solo para CLASICO con hcpQualifiedMode=PER_CATEGORY. */
  categoryId?: number | null;
}

export interface TournamentAdminPlayoffCategoryLegend {
  categoryId: number;
  categoryName: string;
  /** Índice 0-based para asignación de color. */
  categoryIndex: number;
}

export type PlayoffScoreType = 'HCP' | 'SCRATCH';
export type PlayoffBracketStatus = 'DRAFT' | 'CONFIRMED';

export interface TournamentAdminPlayoffBrackets {
  tournamentAdminId: number;
  /** FRUTALES o CLASICO */
  tipo: string;
  /** true si el torneo tiene clasificación Scratch configurada (solo CLASICO). */
  scratchApplicable: boolean;
  /** Llaves ya generadas (0, 1 o 2: HCP y/o SCRATCH). */
  brackets: TournamentAdminPlayoffBracket[];
}

export interface TournamentAdminPlayoffBracket {
  bracketId: number;
  scoreType: PlayoffScoreType;
  size: number;
  status: PlayoffBracketStatus;
  /** true si está CONFIRMED y todavía no se jugó ningún partido. */
  canRevertToDraft: boolean;
  rounds: TournamentAdminPlayoffBracketRound[];
  unassignedPlayers: TournamentAdminPlayoffBracketPlayerRef[];
}

export interface TournamentAdminPlayoffBracketRound {
  roundNumber: number;
  roundName: string;
  slots: TournamentAdminPlayoffBracketSlot[];
}

export interface TournamentAdminPlayoffBracketSlot {
  slotId: number;
  slotIndex: number;
  playerId: number | null;
  playerName: string | null;
  playerHandicapIndex: number | null;
  /** Posición del jugador en la Tabla de Play Off (1 = mejor clasificado). Null si no hay jugador. */
  playerSeed: number | null;
  isWinner: boolean;
}

export interface TournamentAdminPlayoffBracketPlayerRef {
  playerId: number;
  playerName: string;
  playerHandicapIndex: number | null;
  /** Posición del jugador en la Tabla de Play Off (1 = mejor clasificado). Usada para el sembrado. */
  seed: number;
}

// ── Match Play de la llave de Playoff ─────────────────────────────────────

export type PlayoffMatchStatus = 'IN_PROGRESS' | 'FINISHED';
export type PlayoffMatchCardStatus = 'IN_PROGRESS' | 'DELIVERED' | 'CANCELLED';

export interface PlayoffRoundSession {
  roundSessionId: number;
  roundNumber: number;
  code: string;
  /** OPEN / CLOSED */
  status: string;
  teeMasculinoId: number | null;
  teeMasculinoName: string | null;
  teeFemeninoId: number | null;
  teeFemeninoName: string | null;
  cantidadHoyosJuego: number;
  matches: PlayoffMatchSummary[];
}

export interface PlayoffMatchSummary {
  matchId: number;
  topSlotId: number;
  bottomSlotId: number;
  playerAId: number;
  playerAName: string;
  playerBId: number;
  playerBName: string;
  status: PlayoffMatchStatus;
  holesWonA: number | null;
  holesWonB: number | null;
  holesPlayed: number | null;
  resultSummary: string | null;
  winnerPlayerId: number | null;
  liveStatusLabel: string | null;
}

export interface StartPlayoffRoundRequest {
  teeMasculinoId?: number | null;
  teeFemeninoId?: number | null;
  cantidadHoyosJuego: number;
}

export interface PlayoffMatchAccessResponse {
  matchId: number;
  tournamentAdminName: string;
  scoreType: PlayoffScoreType;
  roundName: string;
  playerId: number;
  playerName: string;
  opponentId: number;
  opponentName: string;
}

export interface PlayoffMatchPlayerSide {
  playerId: number;
  playerName: string;
  /** Inicial del nombre + apellido (ej: "N. Trachta"), para usar como título de fila. */
  shortName: string;
  /** Nombre del tee desde el que juega (ej: "Blanco", "Rojo"). Puede ser null. */
  teeName: string | null;
  /** HCP Course al 100%. */
  handicapCourse: number | null;
  /** HCP Course al porcentaje congelado del partido. */
  playingHandicap: number | null;
  cardStatus: PlayoffMatchCardStatus;
}

export interface PlayoffMatchHoleInfo {
  holeSequence: number;
  numeroHoyo: number;
  par: number;
  handicapIndex: number;
  /** Distancia en yardas según el tee de cada jugador (null si no está cargada). */
  distanceA: number | null;
  distanceB: number | null;
  strokesA: number;
  strokesB: number;
  golpesPropioA: number | null;
  golpesRivalA: number | null;
  validadoA: boolean | null;
  golpesPropioB: number | null;
  golpesRivalB: number | null;
  validadoB: boolean | null;
  /** "A" / "B" / "HALVED" / null. Solo cuando ambos coinciden. */
  holeWinner: string | null;
  /** Marca del jugador A: "A" / "B" / "HALVED" / null. */
  markedByA?: string | null;
  /** Marca del jugador B: "A" / "B" / "HALVED" / null. */
  markedByB?: string | null;
  /** true si todavía no existe en la BD (próximo hoyo extra a habilitar) */
  pending: boolean;
}

export interface PlayoffMatchTally {
  holesWonA: number;
  holesWonB: number;
  holesHalved: number;
  holesPlayed: number;
  decided: boolean;
  leaderPlayerId: number | null;
  margin: number;
  holesRemaining: number;
}

export interface PlayoffMatchState {
  matchId: number;
  status: PlayoffMatchStatus;
  scoreType: PlayoffScoreType;
  /** STROKES (golpes) o WINNER (ganador del hoyo). */
  entryMode?: 'STROKES' | 'WINNER';
  /** Porcentaje de HCP Course congelado en el partido. */
  hcpAllowancePercent?: number | null;
  cantidadHoyosJuego: number;
  resultSummary: string | null;
  winnerPlayerId: number | null;
  requestingPlayerId: number;
  playerA: PlayoffMatchPlayerSide;
  playerB: PlayoffMatchPlayerSide;
  holes: PlayoffMatchHoleInfo[];
  tally: PlayoffMatchTally;
  canDeliver: boolean;
  blockedReason: string | null;
}
