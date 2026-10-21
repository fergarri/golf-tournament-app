import api from './api';
import { PublicTeeSheet, TeeSheetGroup, TournamentTeeSheet } from '../types';

export const teeSheetService = {
  get: async (tournamentId: number): Promise<TournamentTeeSheet> => {
    const response = await api.get<TournamentTeeSheet>(`/tournaments/${tournamentId}/tee-sheet`);
    return response.data;
  },

  save: async (tournamentId: number, groups: TeeSheetGroup[]): Promise<TournamentTeeSheet> => {
    const response = await api.put<TournamentTeeSheet>(`/tournaments/${tournamentId}/tee-sheet`, { groups });
    return response.data;
  },

  confirm: async (tournamentId: number): Promise<TournamentTeeSheet> => {
    const response = await api.post<TournamentTeeSheet>(`/tournaments/${tournamentId}/tee-sheet/confirm`);
    return response.data;
  },

  edit: async (tournamentId: number): Promise<TournamentTeeSheet> => {
    const response = await api.post<TournamentTeeSheet>(`/tournaments/${tournamentId}/tee-sheet/edit`);
    return response.data;
  },

  getPublic: async (codigo: string): Promise<PublicTeeSheet> => {
    const response = await api.get<PublicTeeSheet>(`/public/tournaments/code/${codigo}/tee-sheet`);
    return response.data;
  },
};
