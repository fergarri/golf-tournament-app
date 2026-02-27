import api from './api';
import {
  TournamentAdminStage,
  TournamentAdminStageBoard,
  TournamentRelationOption,
} from '../types';

interface StagePayload {
  nombre: string;
  tournamentIds: number[];
}

export const tournamentAdminStageService = {
  getAll: async (tournamentAdminId: number): Promise<TournamentAdminStage[]> => {
    const response = await api.get<TournamentAdminStage[]>(
      `/tournament-admin/${tournamentAdminId}/stages`
    );
    return response.data;
  },

  getById: async (tournamentAdminId: number, stageId: number): Promise<TournamentAdminStage> => {
    const response = await api.get<TournamentAdminStage>(
      `/tournament-admin/${tournamentAdminId}/stages/${stageId}`
    );
    return response.data;
  },

  create: async (
    tournamentAdminId: number,
    payload: StagePayload
  ): Promise<TournamentAdminStage> => {
    const response = await api.post<TournamentAdminStage>(
      `/tournament-admin/${tournamentAdminId}/stages`,
      payload
    );
    return response.data;
  },

  update: async (
    tournamentAdminId: number,
    stageId: number,
    payload: StagePayload
  ): Promise<TournamentAdminStage> => {
    const response = await api.put<TournamentAdminStage>(
      `/tournament-admin/${tournamentAdminId}/stages/${stageId}`,
      payload
    );
    return response.data;
  },

  getRelationOptions: async (
    tournamentAdminId: number,
    stageId?: number
  ): Promise<TournamentRelationOption[]> => {
    const query = stageId ? `?stageId=${stageId}` : '';
    const response = await api.get<TournamentRelationOption[]>(
      `/tournament-admin/${tournamentAdminId}/stages/relations/options${query}`
    );
    return response.data;
  },

  getBoard: async (tournamentAdminId: number, stageId: number): Promise<TournamentAdminStageBoard> => {
    const response = await api.get<TournamentAdminStageBoard>(
      `/tournament-admin/${tournamentAdminId}/stages/${stageId}/board`
    );
    return response.data;
  },

  calculate: async (tournamentAdminId: number, stageId: number): Promise<TournamentAdminStageBoard> => {
    const response = await api.post<TournamentAdminStageBoard>(
      `/tournament-admin/${tournamentAdminId}/stages/${stageId}/calculate`
    );
    return response.data;
  },
};
