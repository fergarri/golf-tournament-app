import api from './api';
import {
  PlayoffMatchAccessResponse,
  PlayoffMatchState,
  PlayoffRoundSession,
  StartPlayoffRoundRequest,
} from '../types';

export interface UpdatePlayoffMatchHoleScore {
  holeSequence: number;
  golpesPropio?: number | null;
  golpesRival?: number | null;
  /** ME, OPPONENT, HALVED o null para desmarcar. */
  holeResult?: 'ME' | 'OPPONENT' | 'HALVED' | null;
}

export const tournamentAdminPlayoffMatchService = {
  // ── Administración ────────────────────────────────────────────────────

  getRoundSessions: async (tournamentAdminId: number, bracketId: number): Promise<PlayoffRoundSession[]> => {
    const response = await api.get<PlayoffRoundSession[]>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/${bracketId}/rounds`
    );
    return response.data;
  },

  getPublicRoundSessions: async (tournamentAdminId: number, bracketId: number): Promise<PlayoffRoundSession[]> => {
    const response = await api.get<PlayoffRoundSession[]>(
      `/public/tournament-admin/${tournamentAdminId}/playoff-brackets/${bracketId}/rounds`
    );
    return response.data;
  },

  startRound: async (
    tournamentAdminId: number,
    bracketId: number,
    roundNumber: number,
    request: StartPlayoffRoundRequest
  ): Promise<PlayoffRoundSession> => {
    const response = await api.post<PlayoffRoundSession>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/${bracketId}/rounds/${roundNumber}/start`,
      request
    );
    return response.data;
  },

  resetRound: async (tournamentAdminId: number, bracketId: number, roundNumber: number): Promise<void> => {
    await api.post(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/${bracketId}/rounds/${roundNumber}/reset`
    );
  },

  // ── Público ───────────────────────────────────────────────────────────

  access: async (code: string, matricula: string): Promise<PlayoffMatchAccessResponse> => {
    const response = await api.post<PlayoffMatchAccessResponse>(
      `/public/playoff-match-rounds/${code}/access`,
      { matricula }
    );
    return response.data;
  },

  getMatchState: async (code: string, matchId: number, matricula: string): Promise<PlayoffMatchState> => {
    const response = await api.get<PlayoffMatchState>(
      `/public/playoff-match-rounds/${code}/matches/${matchId}`,
      { params: { matricula } }
    );
    return response.data;
  },

  updateHoles: async (
    code: string,
    matchId: number,
    matricula: string,
    holeScores: UpdatePlayoffMatchHoleScore[]
  ): Promise<PlayoffMatchState> => {
    const response = await api.put<PlayoffMatchState>(
      `/public/playoff-match-rounds/${code}/matches/${matchId}/holes`,
      { matricula, holeScores }
    );
    return response.data;
  },

  deliver: async (code: string, matchId: number, matricula: string): Promise<PlayoffMatchState> => {
    const response = await api.post<PlayoffMatchState>(
      `/public/playoff-match-rounds/${code}/matches/${matchId}/deliver`,
      { matricula }
    );
    return response.data;
  },

  concede: async (code: string, matchId: number, matricula: string): Promise<PlayoffMatchState> => {
    const response = await api.post<PlayoffMatchState>(
      `/public/playoff-match-rounds/${code}/matches/${matchId}/concede`,
      { matricula }
    );
    return response.data;
  },
};
