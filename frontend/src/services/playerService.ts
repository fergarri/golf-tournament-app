import api from './api';
import { Player } from '../types';

export const playerService = {
  getAll: async (): Promise<Player[]> => {
    const response = await api.get<Player[]>('/players');
    return response.data;
  },

  getById: async (id: number): Promise<Player> => {
    const response = await api.get<Player>(`/players/${id}`);
    return response.data;
  },

  getByMatricula: async (matricula: string): Promise<Player> => {
    const response = await api.get<Player>(`/players/matricula/${matricula}`);
    return response.data;
  },

  search: async (query: string): Promise<Player[]> => {
    const response = await api.get<Player[]>(`/players/search?query=${query}`);
    return response.data;
  },

  create: async (data: Partial<Player>): Promise<Player> => {
    const response = await api.post<Player>('/players', data);
    return response.data;
  },

  update: async (id: number, data: Partial<Player>): Promise<Player> => {
    const response = await api.put<Player>(`/players/${id}`, data);
    return response.data;
  },

  delete: async (id: number): Promise<void> => {
    await api.delete(`/players/${id}`);
  },
};
