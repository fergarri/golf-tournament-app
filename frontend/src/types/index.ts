export interface User {
  email: string;
  role: string;
  permissions: string[];
}

export interface UserDetail {
  id: number;
  email: string;
  matricula?: string;
  role: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  email: string;
  matricula?: string;
  password: string;
  role: string;
}

export interface UpdateUserRequest {
  email: string;
  matricula?: string;
  role: string;
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
}

export interface Player {
  id: number;
  nombre: string;
  apellido: string;
  email?: string;
  matricula: string;
  fechaNacimiento?: string;
  handicapIndex: number;
  telefono?: string;
  clubOrigen?: string;
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
  active: boolean;
}

export interface Hole {
  id: number;
  numeroHoyo: number;
  par: number;
  handicap: number;
  distancesByTee: { [key: number]: number };
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
  fechaInicio: string;
  fechaFin?: string;
  limiteInscriptos?: number;
  valorInscripcion?: number;
  doublePoints?: boolean;
  currentInscriptos: number;
  categories: TournamentCategory[];
  teeConfig: TournamentTeeConfig;
}

export interface TournamentCategory {
  id?: number;
  nombre: string;
  handicapMin: number;
  handicapMax: number;
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
  status: string;
  deliveredAt?: string;
  holeScores: HoleScore[];
  totalScore?: number;
  totalPar: number;
}

export interface HoleScore {
  id: number;
  holeId: number;
  numeroHoyo: number;
  par: number;
  golpesPropio?: number;
  golpesMarcador?: number;
  validado: boolean;
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
  status?: string;
  pagado?: boolean;
}

export interface FrutalesScore {
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
}

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
  tournamentId?: number;
  tournamentNombre?: string;
  valorInscripcion: number;
  cantidadCuotas: number;
  estado: string;
  currentInscriptos: number;
  totalRecaudado: number;
}

export interface TournamentAdminDetail {
  id: number;
  nombre: string;
  fecha: string;
  cantidadCuotas: number;
  valorInscripcion: number;
  currentInscriptos: number;
  totalRecaudado: number;
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
