import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { tournamentAdminStageService } from '../services/tournamentAdminStageService';
import { TournamentAdminStageBoard } from '../types';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';

const TournamentAdminStageBoardPage = () => {
  const { id, stageId } = useParams<{ id: string; stageId: string }>();
  const navigate = useNavigate();
  const tournamentAdminId = Number(id);
  const stageIdNumber = Number(stageId);

  const [board, setBoard] = useState<TournamentAdminStageBoard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [calculating, setCalculating] = useState(false);

  useEffect(() => {
    if (!Number.isFinite(tournamentAdminId) || !Number.isFinite(stageIdNumber)) {
      setError('Parámetros inválidos');
      setLoading(false);
      return;
    }
    loadData();
  }, [tournamentAdminId, stageIdNumber]);

  const loadData = async () => {
    try {
      setLoading(true);
      const data = await tournamentAdminStageService.getBoard(tournamentAdminId, stageIdNumber);
      setBoard(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando fechas de la etapa');
    } finally {
      setLoading(false);
    }
  };

  const handleCalculate = async () => {
    try {
      setCalculating(true);
      const data = await tournamentAdminStageService.calculate(tournamentAdminId, stageIdNumber);
      setBoard(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error calculando puntos de etapa');
    } finally {
      setCalculating(false);
    }
  };

  if (loading) return <div className="loading">Cargando fechas de etapa...</div>;
  if (!board) return <div className="error-message">No se encontró la etapa</div>;

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button
            onClick={() => navigate(`/administration/${tournamentAdminId}/stages`)}
            className="btn-back"
          >
            ← Volver a Etapas
          </button>
          <button onClick={loadData} className="btn-refresh">
            ⟳ Actualizar
          </button>
          <button
            onClick={handleCalculate}
            className="btn btn-primary"
            disabled={calculating}
          >
            {calculating ? 'Calculando...' : 'Calcular Puntos'}
          </button>
        </div>

        <div className="tournament-info">
          <h1>{board.stageName}</h1>
          <div className="tournament-details">
            <span className="detail-item">
              <strong>Fechas:</strong> {board.tournaments.length}
            </span>
            <span className="detail-item">
              <strong>Jugadores:</strong> {board.rows.length}
            </span>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="leaderboard-container">
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '60px', textAlign: 'center' }}>#</th>
                <th>Jugador</th>
                {board.tournaments.map(tournament => (
                  <th
                    key={tournament.tournamentId}
                    style={{
                      minWidth: '150px',
                      textAlign: 'center',
                      background: tournament.doublePoints ? '#fff2cc' : undefined,
                    }}
                    title={tournament.tournamentName}
                  >
                    Fecha: {formatDateSafe(tournament.fechaInicio)}
                  </th>
                ))}
                <th style={{ textAlign: 'center' }}>Puntos</th>
                <th style={{ textAlign: 'center' }}>Posición</th>
              </tr>
            </thead>
            <tbody>
              {board.rows.length === 0 ? (
                <tr>
                  <td colSpan={5 + board.tournaments.length} className="empty-row">
                    No hay jugadores para mostrar en esta etapa
                  </td>
                </tr>
              ) : (
                board.rows.map((row, index) => (
                  <tr key={row.playerId}>
                    <td style={{ textAlign: 'center', fontWeight: 600 }}>{index + 1}</td>
                    <td>{row.playerName}</td>
                    {board.tournaments.map(tournament => (
                      <td
                        key={`${row.playerId}-${tournament.tournamentId}`}
                        style={{
                          textAlign: 'center',
                          background: tournament.doublePoints ? '#fff2cc' : undefined,
                          fontWeight: tournament.doublePoints ? 700 : 500,
                        }}
                      >
                        {row.pointsByTournament[tournament.tournamentId] ?? 0}
                      </td>
                    ))}
                    <td style={{ textAlign: 'center', fontWeight: 700 }}>{row.totalPoints}</td>
                    <td style={{ textAlign: 'center' }}>{row.position ?? '-'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default TournamentAdminStageBoardPage;
