import api from './api';
import { Tournament } from '../types';

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
};
