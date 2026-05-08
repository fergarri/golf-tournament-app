import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { tournamentAdminPlayoffResultService } from '../services/tournamentAdminPlayoffResultService';
import { TournamentAdminPlayoffResults, TournamentAdminPlayoffResultRow } from '../types';
import '../components/Form.css';
import '../components/Table.css';
import './TournamentLeaderboardPage.css';

type PlayoffTab = 'hcp' | 'scratch';

const PublicTournamentAdminPlayoffResultsPage = () => {
  const { tournamentAdminId: tournamentAdminIdParam } = useParams<{ tournamentAdminId: string }>();
  const tournamentAdminId = Number(tournamentAdminIdParam);

  const [results, setResults] = useState<TournamentAdminPlayoffResults | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<PlayoffTab>('hcp');

  useEffect(() => {
    if (!Number.isFinite(tournamentAdminId)) {
      setError('Parámetros inválidos');
      setLoading(false);
      return;
    }

    loadData();
    const interval = setInterval(() => loadData({ silent: true }), 100000);
    return () => clearInterval(interval);
  }, [tournamentAdminId]);

  const loadData = async (options?: { silent?: boolean }) => {
    const silent = options?.silent ?? false;
    try {
      if (!silent) setLoading(true);
      const data = await tournamentAdminPlayoffResultService.getPublic(tournamentAdminId);
      setResults(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando resultados de Play Off');
    } finally {
      if (!silent) setLoading(false);
    }
  };

  if (loading && !results) return <div className="loading">Cargando resultados de Play Off...</div>;
  if (!results) return <div className="error-message">No se encontraron resultados</div>;

  const isClasic = results.tipo === 'CLASICO';
  const hasStages = results.stages.length > 0;
  const displayRows: TournamentAdminPlayoffResultRow[] =
    activeTab === 'scratch' && isClasic ? (results.scratchRows ?? []) : results.rows;
  const qualifiedCount = displayRows.filter((r) => r.qualified).length;
  const colCount = 4 + results.stages.length;

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button
            type="button"
            onClick={() => void loadData()}
            className="btn-refresh"
            disabled={loading}
          >
            ⟳ Actualizar
          </button>
        </div>

        <div className="tournament-info">
          <h1>Resultados Play Off</h1>
          <span className="final-badge">RESULTADOS PÚBLICOS</span>
          <div className="tournament-details">
            <span className="detail-item">
              <strong>Etapas:</strong> {results.stages.length}
            </span>
            <span className="detail-item">
              <strong>Jugadores:</strong> {displayRows.length}
            </span>
            <span className="detail-item">
              <strong>Clasificados:</strong> {qualifiedCount}
            </span>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {!hasStages && (
        <div className="error-message">
          No hay etapas creadas para este torneo administrativo.
        </div>
      )}

      {/* Tabs Con HCP / Sin HCP solo para CLASICO */}
      {isClasic && (
        <div style={{ display: 'flex', gap: '0', marginBottom: '1.5rem', borderBottom: '2px solid #e0e0e0' }}>
          <button
            onClick={() => setActiveTab('hcp')}
            style={{
              padding: '0.75rem 1.5rem',
              border: 'none',
              borderBottom: activeTab === 'hcp' ? '3px solid #2980b9' : '3px solid transparent',
              background: 'none',
              cursor: 'pointer',
              fontWeight: activeTab === 'hcp' ? 700 : 400,
              color: activeTab === 'hcp' ? '#2980b9' : '#555',
              fontSize: '1rem',
            }}
          >
            Con HCP
          </button>
          <button
            onClick={() => setActiveTab('scratch')}
            style={{
              padding: '0.75rem 1.5rem',
              border: 'none',
              borderBottom: activeTab === 'scratch' ? '3px solid #2980b9' : '3px solid transparent',
              background: 'none',
              cursor: 'pointer',
              fontWeight: activeTab === 'scratch' ? 700 : 400,
              color: activeTab === 'scratch' ? '#2980b9' : '#555',
              fontSize: '1rem',
            }}
          >
            Sin HCP (Scratch)
          </button>
        </div>
      )}

      <div className="leaderboard-container">
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '60px', textAlign: 'center' }}>#</th>
                <th>Jugador</th>
                {results.stages.map((stage) => (
                  <th
                    key={stage.stageId}
                    style={{ minWidth: '90px', textAlign: 'center' }}
                    title={stage.stageName}
                  >
                    {stage.code}
                  </th>
                ))}
                <th style={{ textAlign: 'center' }}>Ptos</th>
                <th style={{ textAlign: 'center' }}>Pos</th>
              </tr>
            </thead>
            <tbody>
              {displayRows.length === 0 ? (
                <tr>
                  <td colSpan={colCount} className="empty-row">
                    Aún no hay resultados calculados
                  </td>
                </tr>
              ) : (
                displayRows.map((row, index) => (
                  <tr key={row.playerId}>
                    <td style={{ textAlign: 'center', fontWeight: 600 }}>{index + 1}</td>
                    <td>{row.playerName}</td>
                    {results.stages.map((stage) => (
                      <td
                        key={`${row.playerId}-${stage.stageId}`}
                        style={{ textAlign: 'center' }}
                      >
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

      <div className="update-info">
        <span className="live-indicator"></span>
        <span>Actualización automática cada 100 segundos</span>
      </div>
    </div>
  );
};

export default PublicTournamentAdminPlayoffResultsPage;
