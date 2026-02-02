import api from './api';

export const inscriptionService = {
  inscribePlayer: async (codigo: string, matricula: string) => {
    const response = await api.post(`/inscriptions/tournaments/${codigo}`, { matricula });
    return response.data;
  },

  inscribePlayerManual: async (tournamentId: number, playerId: number) => {
    const response = await api.post(`/inscriptions/admin/tournaments/${tournamentId}/players/${playerId}`);
    return response.data;
  },

  getTournamentInscriptions: async (tournamentId: number) => {
    const response = await api.get(`/inscriptions/tournaments/${tournamentId}`);
    return response.data;
  },

  removeInscription: async (inscriptionId: number) => {
    await api.delete(`/inscriptions/${inscriptionId}`);
  },
};
