import api from './api';
import { TournamentAdmin, TournamentAdminDetail } from '../types';

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
    tournamentId?: number | null;
    valorInscripcion: number;
    cantidadCuotas: number;
  }): Promise<TournamentAdmin> => {
    const response = await api.post<TournamentAdmin>('/tournament-admin', data);
    return response.data;
  },

  update: async (id: number, data: {
    nombre: string;
    fecha: string;
    tournamentId?: number | null;
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
};
