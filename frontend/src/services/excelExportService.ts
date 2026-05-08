import api from './api';

const downloadBlob = (data: ArrayBuffer, filename: string) => {
  const blob = new Blob([data], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
};

export const excelExportService = {
  exportTournamentInscriptions: async (tournamentId: number, tournamentName?: string): Promise<void> => {
    const response = await api.get(`/exports/excel/tournaments/${tournamentId}/inscriptions`, {
      responseType: 'arraybuffer',
    });
    const filename = tournamentName
      ? `inscriptos_${tournamentName.toLowerCase().replace(/\s+/g, '_')}.xlsx`
      : `inscriptos_torneo_${tournamentId}.xlsx`;
    downloadBlob(response.data, filename);
  },

  exportAdminInscriptions: async (tournamentAdminId: number, tournamentName?: string): Promise<void> => {
    const response = await api.get(`/exports/excel/tournament-admin/${tournamentAdminId}/inscriptions`, {
      responseType: 'arraybuffer',
    });
    const filename = tournamentName
      ? `inscriptos_admin_${tournamentName.toLowerCase().replace(/\s+/g, '_')}.xlsx`
      : `inscriptos_admin_${tournamentAdminId}.xlsx`;
    downloadBlob(response.data, filename);
  },

  exportTournamentResults: async (tournamentId: number, tournamentName?: string): Promise<void> => {
    const response = await api.get(`/exports/excel/tournaments/${tournamentId}/results`, {
      responseType: 'arraybuffer',
    });
    const filename = tournamentName
      ? `resultados_${tournamentName.toLowerCase().replace(/\s+/g, '_')}.xlsx`
      : `resultados_torneo_${tournamentId}.xlsx`;
    downloadBlob(response.data, filename);
  },

  exportStageBoard: async (tournamentAdminId: number, stageId: number, stageName?: string): Promise<void> => {
    const response = await api.get(
      `/exports/excel/tournament-admin/${tournamentAdminId}/stages/${stageId}/board`,
      { responseType: 'arraybuffer' }
    );
    const filename = stageName
      ? `etapa_${stageName.toLowerCase().replace(/\s+/g, '_')}.xlsx`
      : `etapa_${stageId}.xlsx`;
    downloadBlob(response.data, filename);
  },

  exportPlayoffResults: async (tournamentAdminId: number): Promise<void> => {
    const response = await api.get(
      `/exports/excel/tournament-admin/${tournamentAdminId}/playoff-results`,
      { responseType: 'arraybuffer' }
    );
    downloadBlob(response.data, `playoff_${tournamentAdminId}.xlsx`);
  },
};
