export interface User {
  email: string;
  role: string;
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
  delivered: boolean;
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
  playerName: string;
  matricula: string;
  clubOrigen?: string;
  categoryName?: string;
  scoreGross: number;
  scoreNeto: number;
  totalPar: number;
  scoreToPar: number;
  handicapCourse?: number;
}
