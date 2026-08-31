import api from './api';
import { PlayoffScoreType, TournamentAdminPlayoffBrackets } from '../types';

export interface SlotAssignment {
  slotId: number;
  playerId: number | null;
}

export const tournamentAdminPlayoffBracketService = {
  get: async (tournamentAdminId: number): Promise<TournamentAdminPlayoffBrackets> => {
    const response = await api.get<TournamentAdminPlayoffBrackets>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets`
    );
    return response.data;
  },

  getPublic: async (tournamentAdminId: number): Promise<TournamentAdminPlayoffBrackets> => {
    const response = await api.get<TournamentAdminPlayoffBrackets>(
      `/public/tournament-admin/${tournamentAdminId}/playoff-brackets`
    );
    return response.data;
  },

  generate: async (tournamentAdminId: number, scoreType?: PlayoffScoreType): Promise<TournamentAdminPlayoffBrackets> => {
    const response = await api.post<TournamentAdminPlayoffBrackets>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/generate`,
      null,
      { params: scoreType ? { scoreType } : undefined }
    );
    return response.data;
  },

  saveSlots: async (
    tournamentAdminId: number,
    bracketId: number,
    assignments: SlotAssignment[]
  ): Promise<TournamentAdminPlayoffBrackets> => {
    const response = await api.put<TournamentAdminPlayoffBrackets>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/${bracketId}/slots`,
      { assignments }
    );
    return response.data;
  },

  confirm: async (tournamentAdminId: number, bracketId: number): Promise<TournamentAdminPlayoffBrackets> => {
    const response = await api.post<TournamentAdminPlayoffBrackets>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/${bracketId}/confirm`
    );
    return response.data;
  },

  revert: async (tournamentAdminId: number, bracketId: number): Promise<TournamentAdminPlayoffBrackets> => {
    const response = await api.post<TournamentAdminPlayoffBrackets>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/${bracketId}/revert`
    );
    return response.data;
  },

  reset: async (tournamentAdminId: number, bracketId: number): Promise<TournamentAdminPlayoffBrackets> => {
    const response = await api.post<TournamentAdminPlayoffBrackets>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/${bracketId}/reset`
    );
    return response.data;
  },

  markWinner: async (
    tournamentAdminId: number,
    bracketId: number,
    slotId: number
  ): Promise<TournamentAdminPlayoffBrackets> => {
    const response = await api.put<TournamentAdminPlayoffBrackets>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/${bracketId}/slots/${slotId}/winner`
    );
    return response.data;
  },

  undoWinner: async (
    tournamentAdminId: number,
    bracketId: number,
    slotId: number
  ): Promise<TournamentAdminPlayoffBrackets> => {
    const response = await api.delete<TournamentAdminPlayoffBrackets>(
      `/tournament-admin/${tournamentAdminId}/stages/playoff-brackets/${bracketId}/slots/${slotId}/winner`
    );
    return response.data;
  },
};
