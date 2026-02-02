import api from './api';
import { LeaderboardEntry } from '../types';

export const leaderboardService = {
  getLeaderboard: async (tournamentId: number, categoryId?: number): Promise<LeaderboardEntry[]> => {
    const url = categoryId
      ? `/leaderboard/tournaments/${tournamentId}/categories/${categoryId}`
      : `/leaderboard/tournaments/${tournamentId}`;
    const response = await api.get<LeaderboardEntry[]>(url);
    return response.data;
  },
};
