import { Tournament } from '../types';

export const getPublicTournamentResultsLink = (tournament: Pick<Tournament, 'codigo' | 'tipo'>): string => {
  const origin = window.location.origin;
  if (tournament.tipo === 'FRUTALES') {
    return `${origin}/frutales-results/${tournament.codigo}`;
  }
  return `${origin}/results/${tournament.codigo}`;
};

export const getPublicStageResultsLink = (tournamentAdminId: number, stageId: number): string => {
  return `${window.location.origin}/stage-results/${tournamentAdminId}/${stageId}`;
};

export const getPublicPlayoffResultsLink = (tournamentAdminId: number): string => {
  return `${window.location.origin}/playoff-results/${tournamentAdminId}`;
};

export const buildResultsShareMessage = (tournament: Tournament): string => {
  const fechaLink = getPublicTournamentResultsLink(tournament);
  const isAssociated =
    tournament.tournamentAdminId != null &&
    tournament.stageId != null &&
    Boolean(tournament.stageName);

  if (!isAssociated) {
    return `Resultados del torneo ${tournament.nombre}:\n${fechaLink}`;
  }

  const stageLink = getPublicStageResultsLink(tournament.tournamentAdminId!, tournament.stageId!);
  const playoffLink = getPublicPlayoffResultsLink(tournament.tournamentAdminId!);

  return [
    `Resultados ${tournament.nombre}:`,
    '',
    'Resultados de la Fecha:',
    fechaLink,
    '',
    `Resultados ${tournament.stageName}:`,
    stageLink,
    '',
    'Resultados Play Off:',
    playoffLink,
    '',
    'Felicitaciones a todos los golfistas!!',
  ].join('\n');
};
