import api from './api';
import { Tournament, TournamentPrize } from '../types';

export const tournamentService = {
  getAll: async (): Promise<Tournament[]> => {
    const response = await api.get<Tournament[]>('/tournaments');
    return response.data;
  },

  getById: async (id: number): Promise<Tournament> => {
    const response = await api.get<Tournament>(`/tournaments/${id}`);
    return response.data;
  },

  getTournamentById: async (id: number): Promise<Tournament> => {
    const response = await api.get<Tournament>(`/tournaments/${id}`);
    return response.data;
  },

  getByCodigo: async (codigo: string): Promise<Tournament> => {
    const response = await api.get<Tournament>(`/tournaments/code/${codigo}`);
    return response.data;
  },

  create: async (data: any): Promise<Tournament> => {
    const response = await api.post<Tournament>('/tournaments', data);
    return response.data;
  },

  update: async (id: number, data: any): Promise<Tournament> => {
    const response = await api.put<Tournament>(`/tournaments/${id}`, data);
    return response.data;
  },

  delete: async (id: number): Promise<void> => {
    await api.delete(`/tournaments/${id}`);
  },

  start: async (id: number): Promise<Tournament> => {
    const response = await api.post<Tournament>(`/tournaments/${id}/start`);
    return response.data;
  },

  finalize: async (id: number): Promise<Tournament> => {
    const response = await api.post<Tournament>(`/tournaments/${id}/finalize`);
    return response.data;
  },

  reopen: async (id: number): Promise<Tournament> => {
    const response = await api.post<Tournament>(`/tournaments/${id}/reopen`);
    return response.data;
  },

  getPrizes: async (tournamentId: number): Promise<TournamentPrize[]> => {
    const response = await api.get<TournamentPrize[]>(`/tournaments/${tournamentId}/prizes`);
    return response.data;
  },

  assignPrizeWinner: async (tournamentId: number, prizeType: string, inscriptionId: number): Promise<TournamentPrize> => {
    const response = await api.post<TournamentPrize>(`/tournaments/${tournamentId}/prizes/${prizeType}/winner`, { inscriptionId });
    return response.data;
  },
};
