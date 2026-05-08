import api from './api';
import { ExportTournamentInscriptionsResult, ImportAdminInscriptionsResult, ScoringConfig, SaveScoringConfigRequest, TournamentAdmin, TournamentAdminDetail } from '../types';

interface SavePaymentUpdate {
  paymentId: number;
  pagado: boolean;
}

export const tournamentAdminService = {
  getAll: async (): Promise<TournamentAdmin[]> => {
    const response = await api.get<TournamentAdmin[]>('/tournament-admin');
    return response.data;
  },

  getById: async (id: number): Promise<TournamentAdmin> => {
    const response = await api.get<TournamentAdmin>(`/tournament-admin/${id}`);
    return response.data;
  },

  create: async (data: {
    nombre: string;
    fecha: string;
    tipo: string;
    valorInscripcion: number;
    cantidadCuotas: number;
  }): Promise<TournamentAdmin> => {
    const response = await api.post<TournamentAdmin>('/tournament-admin', data);
    return response.data;
  },

  update: async (id: number, data: {
    nombre: string;
    fecha: string;
    tipo: string;
    valorInscripcion: number;
    cantidadCuotas: number;
  }): Promise<TournamentAdmin> => {
    const response = await api.put<TournamentAdmin>(`/tournament-admin/${id}`, data);
    return response.data;
  },

  delete: async (id: number): Promise<void> => {
    await api.delete(`/tournament-admin/${id}`);
  },

  finalize: async (id: number): Promise<TournamentAdmin> => {
    const response = await api.post<TournamentAdmin>(`/tournament-admin/${id}/finalize`);
    return response.data;
  },

  inscribePlayer: async (tournamentAdminId: number, playerId: number): Promise<void> => {
    await api.post(`/tournament-admin/${tournamentAdminId}/inscriptions/${playerId}`);
  },

  importInscriptions: async (tournamentAdminId: number): Promise<ImportAdminInscriptionsResult> => {
    const response = await api.post<ImportAdminInscriptionsResult>(`/tournament-admin/${tournamentAdminId}/import-inscriptions`);
    return response.data;
  },

  removeInscription: async (inscriptionId: number): Promise<void> => {
    await api.delete(`/tournament-admin/inscriptions/${inscriptionId}`);
  },

  getDetail: async (id: number): Promise<TournamentAdminDetail> => {
    const response = await api.get<TournamentAdminDetail>(`/tournament-admin/${id}/detail`);
    return response.data;
  },

  savePayments: async (tournamentAdminId: number, payments: SavePaymentUpdate[]): Promise<void> => {
    await api.put(`/tournament-admin/${tournamentAdminId}/payments`, { payments });
  },

  getScoringConfig: async (tournamentAdminId: number): Promise<ScoringConfig> => {
    const response = await api.get<ScoringConfig>(`/tournament-admin/${tournamentAdminId}/scoring-config`);
    return response.data;
  },

  saveScoringConfig: async (tournamentAdminId: number, data: SaveScoringConfigRequest): Promise<ScoringConfig> => {
    const response = await api.put<ScoringConfig>(`/tournament-admin/${tournamentAdminId}/scoring-config`, data);
    return response.data;
  },

  exportTournamentInscriptions: async (tournamentId: number): Promise<ExportTournamentInscriptionsResult> => {
    const response = await api.post<ExportTournamentInscriptionsResult>(
      `/tournament-admin/from-tournament/${tournamentId}/export-inscriptions`
    );
    return response.data;
  },
};
