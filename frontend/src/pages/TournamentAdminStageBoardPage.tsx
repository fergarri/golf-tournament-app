import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { tournamentAdminStageService } from '../services/tournamentAdminStageService';
import { excelExportService } from '../services/excelExportService';
import { TournamentAdminStageBoard, TournamentAdminStageBoardRow } from '../types';
import { formatDateSafe } from '../utils/dateUtils';
import { standardRank, computeRowspans } from '../utils/ranking';
import Modal from '../components/Modal';
import '../components/Form.css';
import '../components/Table.css';
import './TournamentLeaderboardPage.css';

const TournamentAdminStageBoardPage = () => {
  const { id, stageId } = useParams<{ id: string; stageId: string }>();
  const navigate = useNavigate();
  const tournamentAdminId = Number(id);
  const stageIdNumber = Number(stageId);

  const [board, setBoard] = useState<TournamentAdminStageBoard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCopyLinkModal, setShowCopyLinkModal] = useState(false);
  const [activeTab, setActiveTab] = useState<string>('scratch');
  const [exportingExcel, setExportingExcel] = useState(false);

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
      // Para CLASICO, activar la primera categoría por defecto
      if (data.tipo === 'CLASICO' && data.categoryRows && data.categoryRows.length > 0) {
        setActiveTab(data.categoryRows[0].categoryId.toString());
      }
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando fechas de la etapa');
    } finally {
      setLoading(false);
    }
  };

  const handleExcelExport = async () => {
    if (!board) return;
    try {
      setExportingExcel(true);
      await excelExportService.exportStageBoard(tournamentAdminId, stageIdNumber, board.stageName);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error al descargar Excel de etapa');
    } finally {
      setExportingExcel(false);
    }
  };

  const getPublicResultsLink = () => {
    return `${window.location.origin}/stage-results/${tournamentAdminId}/${stageIdNumber}`;
  };

  const copyResultsLink = () => {
    navigator.clipboard.writeText(getPublicResultsLink());
    setShowCopyLinkModal(true);
  };

  if (loading) return <div className="loading">Cargando fechas de etapa...</div>;
  if (!board) return <div className="error-message">No se encontró la etapa</div>;

  const isClasic = board.tipo === 'CLASICO';

  // Para CLASICO: tabs por categoría + Scratch. Para FRUTALES: solo una vista sin tabs.
  const getDisplayRows = (): TournamentAdminStageBoardRow[] => {
    if (!isClasic) return board.rows;
    if (activeTab === 'scratch') return board.scratchRows ?? [];
    const catId = parseInt(activeTab);
    const catRows = board.categoryRows?.find(c => c.categoryId === catId);
    return catRows?.rows ?? [];
  };
  const displayRows = getDisplayRows();

  const totalPlayers = isClasic
    ? (board.categoryRows?.reduce((acc, c) => acc + c.rows.length, 0) ?? board.rows.length)
    : board.rows.length;

  const renderTable = (rows: TournamentAdminStageBoardRow[]) => {
    const rowspans = computeRowspans(rows, r => r.totalPoints);
    return (
      <div style={{ overflowX: 'auto' }}>
        <table className="data-table">
          <thead>
            <tr>
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
            {rows.length === 0 ? (
              <tr>
                <td colSpan={4 + board.tournaments.length} className="empty-row">
                  No hay jugadores para mostrar en esta etapa
                </td>
              </tr>
            ) : (
              rows.map((row, index) => {
                const span = rowspans[index];
                const pos = standardRank(rows, index, r => r.totalPoints);
                return (
                  <tr key={row.playerId}>
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
                    {span > 0 && (
                      <td
                        rowSpan={span}
                        style={{ textAlign: 'center', verticalAlign: 'middle', fontWeight: 600 }}
                      >
                        {pos}
                      </td>
                    )}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    );
  };

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
            onClick={copyResultsLink}
            className="btn-compact btn-compact-primary"
          >
            Link Resultados
          </button>
          <button
            onClick={handleExcelExport}
            disabled={exportingExcel}
            className="btn-export"
          >
            {exportingExcel ? 'Descargando...' : '⬇ Excel'}
          </button>
        </div>

        <div className="tournament-info">
          <h1>{board.stageName}</h1>
          <div className="tournament-details">
            <span className="detail-item">
              <strong>Fechas:</strong> {board.tournaments.length}
            </span>
            <span className="detail-item">
              <strong>Jugadores:</strong> {totalPlayers}
            </span>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      {/* Tabs por categoría + Scratch para CLASICO */}
      {isClasic && board.categoryRows && board.categoryRows.length > 0 && (
        <div style={{ display: 'flex', gap: '0', marginBottom: '1.5rem', borderBottom: '2px solid #e0e0e0', flexWrap: 'wrap' }}>
          {board.categoryRows.map(cat => {
            const tabId = cat.categoryId.toString();
            const isActive = activeTab === tabId;
            return (
              <button
                key={cat.categoryId}
                onClick={() => setActiveTab(tabId)}
                style={{
                  padding: '0.75rem 1.5rem',
                  border: 'none',
                  borderBottom: isActive ? '3px solid #2980b9' : '3px solid transparent',
                  background: 'none',
                  cursor: 'pointer',
                  fontWeight: isActive ? 700 : 400,
                  color: isActive ? '#2980b9' : '#555',
                  fontSize: '1rem',
                }}
              >
                {cat.categoryName} ({cat.handicapMin}-{cat.handicapMax})
              </button>
            );
          })}
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
            Scratch
          </button>
        </div>
      )}

      <div className="leaderboard-container">
        {renderTable(displayRows)}
      </div>

      <Modal
        isOpen={showCopyLinkModal}
        onClose={() => setShowCopyLinkModal(false)}
        title="Link Copiado"
        size="medium"
      >
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>✓</div>
          <p style={{ marginBottom: '1rem' }}>El link de resultados de etapa fue copiado al portapapeles</p>
          <button onClick={() => setShowCopyLinkModal(false)} className="btn btn-primary">
            Cerrar
          </button>
        </div>
      </Modal>
    </div>
  );
};

export default TournamentAdminStageBoardPage;
