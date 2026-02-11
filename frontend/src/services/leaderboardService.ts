import api from './api';
import { LeaderboardEntry } from '../types';

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
  
  updatePayments: async (tournamentId: number, payments: PaymentUpdate[]): Promise<void> => {
    const request: UpdatePaymentRequest = { payments };
    await api.put(`/leaderboard/tournaments/${tournamentId}/payments`, request);
  },
};
