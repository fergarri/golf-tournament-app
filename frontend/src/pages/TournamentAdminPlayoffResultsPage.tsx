import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { tournamentAdminPlayoffResultService } from '../services/tournamentAdminPlayoffResultService';
import { TournamentAdminPlayoffResults } from '../types';
import Modal from '../components/Modal';
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
  const [showCopyLinkModal, setShowCopyLinkModal] = useState(false);

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

  const getPublicPlayoffResultsLink = () => {
    return `${window.location.origin}/playoff-results/${tournamentAdminId}`;
  };

  const copyPublicPlayoffResultsLink = () => {
    navigator.clipboard.writeText(getPublicPlayoffResultsLink());
    setShowCopyLinkModal(true);
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
  const qualifiedCount = results.rows.filter((r) => r.qualified).length;
  const colCount = 4 + results.stages.length;

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
          <button type="button" onClick={copyPublicPlayoffResultsLink} className="btn-compact btn-compact-primary">
            Link resultados públicos
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
            <span className="detail-item"><strong>Clasificados:</strong> {qualifiedCount}</span>
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
                  <td colSpan={colCount} className="empty-row">
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

      <Modal
        isOpen={showCopyLinkModal}
        onClose={() => setShowCopyLinkModal(false)}
        title="Link copiado"
        size="medium"
      >
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>✓</div>
          <p style={{ marginBottom: '1rem' }}>
            El link público de resultados Play Off fue copiado al portapapeles
          </p>
          <button type="button" onClick={() => setShowCopyLinkModal(false)} className="btn btn-primary">
            Cerrar
          </button>
        </div>
      </Modal>
    </div>
  );
};

export default TournamentAdminPlayoffResultsPage;
