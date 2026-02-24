import api from './api';
import { LeaderboardEntry, FrutalesScore } from '../types';

interface PaymentUpdate {
  inscriptionId: number;
  pagado: boolean;
}

interface UpdatePaymentRequest {
  payments: PaymentUpdate[];
}

export const leaderboardService = {
  getLeaderboard: async (tournamentId: number, categoryId?: number): Promise<LeaderboardEntry[]> => {
    const url = categoryId
      ? `/leaderboard/tournaments/${tournamentId}/categories/${categoryId}`
      : `/leaderboard/tournaments/${tournamentId}`;
    const response = await api.get<LeaderboardEntry[]>(url);
    return response.data;
  },

  getPublicLeaderboard: async (codigo: string): Promise<LeaderboardEntry[]> => {
    const response = await api.get<LeaderboardEntry[]>(`/leaderboard/public/${codigo}`);
    return response.data;
  },
  
  updatePayments: async (tournamentId: number, payments: PaymentUpdate[]): Promise<void> => {
    const request: UpdatePaymentRequest = { payments };
    await api.put(`/leaderboard/tournaments/${tournamentId}/payments`, request);
  },

  getFrutalesScores: async (tournamentId: number): Promise<FrutalesScore[]> => {
    const response = await api.get<FrutalesScore[]>(`/leaderboard/tournaments/${tournamentId}/frutales`);
    return response.data;
  },

  calculateFrutalesScores: async (tournamentId: number): Promise<FrutalesScore[]> => {
    const response = await api.post<FrutalesScore[]>(`/leaderboard/tournaments/${tournamentId}/frutales/calculate`);
    return response.data;
  },

  getPublicFrutalesScores: async (codigo: string): Promise<FrutalesScore[]> => {
    const response = await api.get<FrutalesScore[]>(`/leaderboard/public/${codigo}/frutales`);
    return response.data;
  },
};
