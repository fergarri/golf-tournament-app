import api from './api';
import { TournamentAdminPlayoffResults } from '../types';

export const tournamentAdminPlayoffResultService = {
  get: async (tournamentAdminId: number): Promise<TournamentAdminPlayoffResults> => {
    const response = await api.get<TournamentAdminPlayoffResults>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-results`
    );
    return response.data;
  },

  calculate: async (tournamentAdminId: number): Promise<TournamentAdminPlayoffResults> => {
    const response = await api.post<TournamentAdminPlayoffResults>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-results/calculate`
    );
    return response.data;
  },
};
