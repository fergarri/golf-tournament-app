import api from './api';
import { Scorecard } from '../types';

const downloadPdfBlob = (data: ArrayBuffer, filename: string) => {
  const blob = new Blob([data], { type: 'application/pdf' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
};

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
  handicapCourse?: number;
  holeScores: HoleScoreUpdate[];
}

export interface ConfigureScorecardRequest {
  teeId?: number;
  cantidadHoyosJuego?: number;
  inProgressAction?: 'CONTINUE_EXISTING' | 'START_NEW';
}

export const scorecardService = {
  getOrCreate: async (
    tournamentId: number,
    playerId: number,
    config?: ConfigureScorecardRequest
  ): Promise<Scorecard> => {
    const response = await api.post<Scorecard>(
      `/scorecards/tournaments/${tournamentId}/players/${playerId}`,
      config || {}
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

  cancelScorecard: async (scorecardId: number): Promise<Scorecard> => {
    const response = await api.post<Scorecard>(`/scorecards/${scorecardId}/cancel`);
    return response.data;
  },

  assignMarker: async (scorecardId: number, markerId: number): Promise<Scorecard> => {
    const response = await api.patch<Scorecard>(
      `/scorecards/${scorecardId}/marker/${markerId}`
    );
    return response.data;
  },

  clearMarker: async (scorecardId: number): Promise<Scorecard> => {
    const response = await api.delete<Scorecard>(`/scorecards/${scorecardId}/marker`);
    return response.data;
  },

  disqualifyScorecard: async (scorecardId: number): Promise<Scorecard> => {
    const response = await api.post<Scorecard>(`/scorecards/${scorecardId}/disqualify`);
    return response.data;
  },

  undoDisqualifyScorecard: async (scorecardId: number): Promise<Scorecard> => {
    const response = await api.post<Scorecard>(`/scorecards/${scorecardId}/undo-disqualify`);
    return response.data;
  },

  adminCancelScorecard: async (scorecardId: number): Promise<Scorecard> => {
    const response = await api.post<Scorecard>(`/scorecards/${scorecardId}/admin-cancel`);
    return response.data;
  },

  undoCancelScorecard: async (scorecardId: number): Promise<Scorecard> => {
    const response = await api.post<Scorecard>(`/scorecards/${scorecardId}/undo-cancel`);
    return response.data;
  },

  printScorecards: async (
    tournamentId: number,
    playerIds: number[],
    tournamentName?: string
  ): Promise<void> => {
    const response = await api.post(
      `/scorecards/tournaments/${tournamentId}/print`,
      { playerIds },
      { responseType: 'arraybuffer' }
    );
    const filename = tournamentName
      ? `tarjetas_${tournamentName.toLowerCase().replace(/\s+/g, '_')}.pdf`
      : `tarjetas_torneo_${tournamentId}.pdf`;
    downloadPdfBlob(response.data, filename);
  },
};
