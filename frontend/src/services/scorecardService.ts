import api from './api';
import { Scorecard } from '../types';

export interface UpdateScoreRequest {
  holeId: number;
  golpes: number;
  tipo: 'PROPIO' | 'MARCADOR';
}

export interface HoleScoreUpdate {
  holeId: number;
  golpesPropio?: number;
  golpesMarcador?: number;
}

export interface UpdateScorecardRequest {
  holeScores: HoleScoreUpdate[];
}

export const scorecardService = {
  getOrCreate: async (tournamentId: number, playerId: number, teeId: number): Promise<Scorecard> => {
    const response = await api.post<Scorecard>(
      `/scorecards/tournaments/${tournamentId}/players/${playerId}`,
      { teeId }
    );
    return response.data;
  },

  getById: async (id: number): Promise<Scorecard> => {
    const response = await api.get<Scorecard>(`/scorecards/${id}`);
    return response.data;
  },

  updateScore: async (
    scorecardId: number,
    request: UpdateScoreRequest
  ): Promise<void> => {
    await api.patch(`/scorecards/${scorecardId}/scores`, request);
  },

  updateScorecard: async (
    scorecardId: number,
    request: UpdateScorecardRequest
  ): Promise<Scorecard> => {
    const response = await api.put<Scorecard>(`/scorecards/${scorecardId}`, request);
    return response.data;
  },

  deliverScorecard: async (scorecardId: number): Promise<Scorecard> => {
    const response = await api.post<Scorecard>(`/scorecards/${scorecardId}/deliver`);
    return response.data;
  },

  assignMarker: async (scorecardId: number, markerId: number): Promise<Scorecard> => {
    const response = await api.patch<Scorecard>(
      `/scorecards/${scorecardId}/marker/${markerId}`
    );
    return response.data;
  },
};
