import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { tournamentAdminPlayoffResultService } from '../services/tournamentAdminPlayoffResultService';
import { TournamentAdminPlayoffResults } from '../types';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';

const TournamentAdminPlayoffResultsPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const tournamentAdminId = Number(id);

  const [results, setResults] = useState<TournamentAdminPlayoffResults | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [calculating, setCalculating] = useState(false);

  useEffect(() => {
    if (!Number.isFinite(tournamentAdminId)) {
      setError('Parámetros inválidos');
      setLoading(false);
      return;
    }
    loadData();
  }, [tournamentAdminId]);

  const loadData = async () => {
    try {
      setLoading(true);
      const data = await tournamentAdminPlayoffResultService.get(tournamentAdminId);
      setResults(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando resultados de Play Off');
    } finally {
      setLoading(false);
    }
  };

  const handleCalculate = async () => {
    try {
      setCalculating(true);
      const data = await tournamentAdminPlayoffResultService.calculate(tournamentAdminId);
      setResults(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error calculando resultados de Play Off');
    } finally {
      setCalculating(false);
    }
  };

  if (loading) return <div className="loading">Cargando resultados de Play Off...</div>;
  if (!results) return <div className="error-message">No se encontraron resultados</div>;

  const hasStages = results.stages.length > 0;

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button onClick={() => navigate(`/administration/${tournamentAdminId}/stages`)} className="btn-back">
            ← Volver a Etapas
          </button>
          <button onClick={loadData} className="btn-refresh">
            ⟳ Actualizar
          </button>
          <button
            onClick={handleCalculate}
            className="btn-compact btn-compact-primary"
            disabled={calculating || !hasStages}
            title={!hasStages ? 'No hay etapas para calcular' : undefined}
          >
            {calculating ? 'Calculando...' : 'Calcular Resultados'}
          </button>
        </div>

        <div className="tournament-info">
          <h1>Resultados Play Off</h1>
          <div className="tournament-details">
            <span className="detail-item"><strong>Etapas:</strong> {results.stages.length}</span>
            <span className="detail-item"><strong>Jugadores:</strong> {results.rows.length}</span>
            <span className="detail-item"><strong>Clasificados:</strong> 8</span>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {!hasStages && (
        <div className="error-message">
          No hay etapas creadas. Creá al menos una etapa para calcular resultados Play Off.
        </div>
      )}

      <div className="leaderboard-container">
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '60px', textAlign: 'center' }}>#</th>
                <th>Jugador</th>
                {results.stages.map(stage => (
                  <th key={stage.stageId} style={{ minWidth: '90px', textAlign: 'center' }} title={stage.stageName}>
                    {stage.code}
                  </th>
                ))}
                <th style={{ textAlign: 'center' }}>Ptos</th>
                <th style={{ textAlign: 'center' }}>Pos</th>
              </tr>
            </thead>
            <tbody>
              {results.rows.length === 0 ? (
                <tr>
                  <td colSpan={5 + results.stages.length} className="empty-row">
                    Aún no hay resultados calculados
                  </td>
                </tr>
              ) : (
                results.rows.map((row, index) => (
                  <tr key={row.playerId}>
                    <td style={{ textAlign: 'center', fontWeight: 600 }}>{index + 1}</td>
                    <td>{row.playerName}</td>
                    {results.stages.map(stage => (
                      <td key={`${row.playerId}-${stage.stageId}`} style={{ textAlign: 'center' }}>
                        {row.pointsByStage[stage.stageId] ?? 0}
                      </td>
                    ))}
                    <td style={{ textAlign: 'center', fontWeight: 700 }}>{row.totalPoints}</td>
                    <td
                      style={{
                        textAlign: 'center',
                        fontWeight: 700,
                        background: row.qualified ? '#d8f97e' : undefined,
                        color: row.qualified ? '#1f3d06' : undefined,
                      }}
                    >
                      {row.position}
                    </td>
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

export default TournamentAdminPlayoffResultsPage;
