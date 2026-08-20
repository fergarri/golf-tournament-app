import { useState, useEffect, useMemo } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { leaderboardService } from '../services/leaderboardService';
import { scorecardService } from '../services/scorecardService';
import { inscriptionService } from '../services/inscriptionService';
import { Tournament, FrutalesScore, Scorecard, LeaderboardEntry, InscriptionResponse } from '../types';
import Table, { TableAction } from '../components/Table';
import { formatDateSafe } from '../utils/dateUtils';
import { getScorecardStatusLabel } from '../utils/scorecardStatusLabel';
import { buildResultsShareMessage } from '../utils/resultsMessage';
import ResultsMessageModal from '../components/ResultsMessageModal';
import PrintScorecardsModal from '../components/PrintScorecardsModal';
import Modal from '../components/Modal';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';

const FrutalesLeaderboardPage = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isFinal = searchParams.get('final') === 'true';

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [frutalesScores, setFrutalesScores] = useState<FrutalesScore[]>([]);
  const [inscriptions, setInscriptions] = useState<InscriptionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [calculating, setCalculating] = useState(false);
  const [error, setError] = useState('');
  const [editingScorecardId, setEditingScorecardId] = useState<number | null>(null);
  const [editingScorecard, setEditingScorecard] = useState<Scorecard | null>(null);
  const [savingScorecard, setSavingScorecard] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [resultsMessageModal, setResultsMessageModal] = useState<string | null>(null);
  const [markAsDelivered, setMarkAsDelivered] = useState(false);
  const [finalizing, setFinalizing] = useState(false);
  const [showPrintModal, setShowPrintModal] = useState(false);
  const [confirmDialog, setConfirmDialog] = useState<
    | null
    | { kind: 'disqualify'; entry: FrutalesScore }
    | { kind: 'undoDisqualify'; entry: FrutalesScore }
    | { kind: 'cancelScorecard'; entry: FrutalesScore }
    | { kind: 'undoCancelScorecard'; entry: FrutalesScore }
  >(null);
  const [confirmActionLoading, setConfirmActionLoading] = useState(false);

  const mergeScoresWithInscriptions = (
    scores: FrutalesScore[],
    entries: LeaderboardEntry[],
    inscriptions: InscriptionResponse[]
  ): FrutalesScore[] => {
    const scoreByPlayerId = new Map<number, FrutalesScore>(scores.map((score) => [score.playerId, score]));
    const entryByPlayerId = new Map<number, LeaderboardEntry>(entries.map((entry) => [entry.playerId, entry]));

    const uniqueInscriptions = Array.from(
      new Map(inscriptions.map((ins) => [ins.player.id, ins])).values()
    );

    const mergedByPlayer = new Map<number, FrutalesScore>();

    for (const inscription of uniqueInscriptions) {
      const playerId = inscription.player.id;
      const calculated = scoreByPlayerId.get(playerId);
      const entry = entryByPlayerId.get(playerId);
      const hasScorecard = Boolean(entry?.scorecardId || calculated?.scorecardId);

      const base: FrutalesScore = {
        scorecardId: calculated?.scorecardId || entry?.scorecardId || undefined,
        playerId,
        playerName: calculated?.playerName || `${inscription.player.apellido} ${inscription.player.nombre}`,
        matricula: calculated?.matricula || inscription.player.matricula,
        position: calculated?.position,
        handicapIndex: calculated?.handicapIndex ?? inscription.player.handicapIndex,
        handicapCourse: calculated?.handicapCourse ?? entry?.handicapCourse ?? inscription.handicapCourse,
        scoreGross: calculated?.scoreGross ?? entry?.scoreGross,
        scoreNeto: calculated?.scoreNeto ?? entry?.scoreNeto,
        status: calculated?.status || entry?.status || 'IN_PROGRESS',
        birdieCount: calculated?.birdieCount || 0,
        eagleCount: calculated?.eagleCount || 0,
        aceCount: calculated?.aceCount || 0,
        positionPoints: calculated?.positionPoints || 0,
        birdiePoints: calculated?.birdiePoints || 0,
        eaglePoints: calculated?.eaglePoints || 0,
        acePoints: calculated?.acePoints || 0,
        participationPoints: calculated?.participationPoints || 0,
        totalPoints: calculated?.totalPoints || 0,
      };

      if (!hasScorecard && !calculated) {
        base.status = 'IN_PROGRESS';
        base.scoreGross = undefined;
        base.scoreNeto = undefined;
      }

      mergedByPlayer.set(playerId, base);
    }

    if (scores.length === 0) {
      return Array.from(mergedByPlayer.values()).sort((a, b) => a.playerName.localeCompare(b.playerName));
    }

    const orderedCalculated: FrutalesScore[] = scores
      .map((score) => mergedByPlayer.get(score.playerId))
      .filter((score): score is FrutalesScore => Boolean(score));

    const calculatedIds = new Set(scores.map((score) => score.playerId));
    const missingCalculated = Array.from(mergedByPlayer.values())
      .filter((entry) => !calculatedIds.has(entry.playerId))
      .sort((a, b) => a.playerName.localeCompare(b.playerName));

    const calculatedWithoutInscription = scores.filter((score) => !mergedByPlayer.has(score.playerId));

    return [...orderedCalculated, ...missingCalculated, ...calculatedWithoutInscription];
  };

  useEffect(() => {
    loadData();
  }, [id]);

  const loadData = async () => {
    if (!id) return;
    try {
      setLoading(true);
      const tournamentData = await tournamentService.getById(parseInt(id));
      setTournament(tournamentData);

      const [entries, scores, inscriptions] = await Promise.all([
        leaderboardService.getLeaderboard(parseInt(id)),
        leaderboardService.getFrutalesScores(parseInt(id)),
        inscriptionService.getTournamentInscriptions(parseInt(id)),
      ]);

      setInscriptions(inscriptions);
      setFrutalesScores(mergeScoresWithInscriptions(scores, entries, inscriptions));
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading leaderboard');
    } finally {
      setLoading(false);
    }
  };

  const handleCalculateScores = async () => {
    if (!id) return;
    try {
      setCalculating(true);
      const [entries, scores, inscriptions] = await Promise.all([
        leaderboardService.getLeaderboard(parseInt(id)),
        leaderboardService.calculateFrutalesScores(parseInt(id)),
        inscriptionService.getTournamentInscriptions(parseInt(id)),
      ]);
      setInscriptions(inscriptions);
      setFrutalesScores(mergeScoresWithInscriptions(scores, entries, inscriptions));
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error al calcular puntos');
    } finally {
      setCalculating(false);
    }
  };

  const handleRemoveInscription = async (row: FrutalesScore) => {
    if (!confirm(`¿Dar de baja a ${row.playerName} de este torneo?`)) return;
    const entry = inscriptions.find(i => i.player.id === row.playerId);
    if (!entry) {
      setError('No se encontró la inscripción del jugador');
      return;
    }

    try {
      await inscriptionService.removeInscription(entry.inscriptionId);
      await loadData();
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error dando de baja al jugador');
    }
  };

  const handleFinalizeTournament = async () => {
    if (!id || !tournament) return;
    const confirmed = window.confirm(
      'Al finalizar el torneo, las tarjetas IN_PROGRESS pasarán a CANCELLED. ¿Desea continuar?'
    );
    if (!confirmed) return;

    try {
      setFinalizing(true);
      const updatedTournament = await tournamentService.finalize(parseInt(id));
      setTournament(updatedTournament);
      setError('');
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error al finalizar torneo');
    } finally {
      setFinalizing(false);
    }
  };

  const executeConfirmDialog = async () => {
    if (!confirmDialog) return;
    try {
      setConfirmActionLoading(true);
      if (confirmDialog.kind === 'disqualify' && confirmDialog.entry.scorecardId) {
        await scorecardService.disqualifyScorecard(confirmDialog.entry.scorecardId);
      } else if (confirmDialog.kind === 'undoDisqualify' && confirmDialog.entry.scorecardId) {
        await scorecardService.undoDisqualifyScorecard(confirmDialog.entry.scorecardId);
      } else if (confirmDialog.kind === 'cancelScorecard' && confirmDialog.entry.scorecardId) {
        await scorecardService.adminCancelScorecard(confirmDialog.entry.scorecardId);
      } else if (confirmDialog.kind === 'undoCancelScorecard' && confirmDialog.entry.scorecardId) {
        await scorecardService.undoCancelScorecard(confirmDialog.entry.scorecardId);
      }
      await loadData();
      setError('');
      setConfirmDialog(null);
    } catch (err: any) {
      const msg =
        err.response?.data?.message ||
        (confirmDialog.kind === 'disqualify'
          ? 'Error al descalificar'
          : confirmDialog.kind === 'undoDisqualify'
            ? 'Error al quitar la descalificación'
            : confirmDialog.kind === 'cancelScorecard'
              ? 'Error al cancelar la tarjeta'
              : 'Error al habilitar la tarjeta');
      setError(msg);
    } finally {
      setConfirmActionLoading(false);
    }
  };

  const getPositionClass = (position: number) => {
    if (position === 1) return 'position-first';
    if (position === 2) return 'position-second';
    if (position === 3) return 'position-third';
    return '';
  };

  const handleEditScorecard = async (row: FrutalesScore) => {
    if (!id) return;
    const tournamentId = parseInt(id, 10);
    if (!Number.isFinite(tournamentId)) return;

    try {
      const scorecard =
        row.scorecardId != null
          ? await scorecardService.getById(row.scorecardId)
          : await scorecardService.getOrCreate(tournamentId, row.playerId);
      setMarkAsDelivered(scorecard.status === 'DELIVERED');
      setEditingScorecard(scorecard);
      setEditingScorecardId(scorecard.id);
    } catch (err) {
      console.error('Error loading scorecard:', err);
      setError('Error al cargar la scorecard');
    }
  };

  const handleCloseModal = () => {
    setEditingScorecardId(null);
    setEditingScorecard(null);
  };

  const handleScoreChange = (holeScoreId: number, newScore: number) => {
    if (!editingScorecard) return;
    setEditingScorecard({
      ...editingScorecard,
      holeScores: editingScorecard.holeScores.map(hs =>
        hs.id === holeScoreId ? { ...hs, golpesPropio: newScore } : hs
      )
    });
  };

  const handleHcpChange = (value: string) => {
    if (!editingScorecard) return;
    const parsed = value === '' ? undefined : parseFloat(value);
    const newHcp = parsed === undefined || Number.isNaN(parsed) ? undefined : parsed;
    setEditingScorecard({
      ...editingScorecard,
      handicapCourse: newHcp as any,
    });
  };

  const handleSaveScorecard = async () => {
    if (!editingScorecard) return;
    try {
      setSavingScorecard(true);
      const holeScores = editingScorecard.holeScores.map(hs => ({
        holeId: hs.holeId,
        golpesPropio: hs.golpesPropio || undefined,
        golpesMarcador: hs.golpesMarcador || undefined
      }));

      await scorecardService.updateScorecard(editingScorecard.id, {
        handicapCourse: editingScorecard.handicapCourse != null ? Number(editingScorecard.handicapCourse) : undefined,
        holeScores
      });

      if (markAsDelivered && editingScorecard.status !== 'DELIVERED') {
        await scorecardService.deliverScorecard(editingScorecard.id);
      }

      await loadData();
      handleCloseModal();
    } catch (err: any) {
      console.error('Error saving scorecard:', err);
      setError(err.response?.data?.message || 'Error al guardar los cambios');
    } finally {
      setSavingScorecard(false);
    }
  };

  const copyResultsMessage = () => {
    if (!tournament) return;
    const message = buildResultsShareMessage(tournament);
    navigator.clipboard.writeText(message);
    setResultsMessageModal(message);
  };

  const filteredScores = searchQuery
    ? frutalesScores.filter((entry: FrutalesScore) =>
        `${entry.playerName} ${entry.matricula}`.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : frutalesScores;

  const daPlayerIds = useMemo(() => {
    const byNeto = new Map<string, number[]>();
    for (const entry of frutalesScores) {
      if (
        entry.status !== 'DELIVERED' ||
        entry.scoreNeto == null ||
        entry.position == null ||
        entry.position > 6
      ) continue;
      const key = entry.scoreNeto.toString();
      const ids = byNeto.get(key) || [];
      ids.push(entry.playerId);
      byNeto.set(key, ids);
    }

    const result = new Set<number>();
    for (const ids of byNeto.values()) {
      if (ids.length > 1) {
        ids.forEach((id) => result.add(id));
      }
    }
    return result;
  }, [frutalesScores]);

  const columns = [
    {
      header: 'Pos',
      accessor: (row: FrutalesScore) => {
        const label = getScorecardStatusLabel(row.status, Boolean(row.scorecardId));
        if (label) return <span style={{ color: label.color, fontWeight: 'bold' }}>{label.code}</span>;
        if (row.position) return <span className={`position ${getPositionClass(row.position)}`}>{row.position}</span>;
        return <span>-</span>;
      },
      width: '60px',
    },
    { header: 'Jugador', accessor: 'playerName' as keyof FrutalesScore, width: '15%' },
    { header: 'Matrícula', accessor: 'matricula' as keyof FrutalesScore, width: '8%' },
    {
      header: 'HCP I.',
      accessor: (row: FrutalesScore) => row.handicapIndex?.toFixed(1) || '-',
      width: '7%',
    },
    {
      header: 'HCP C.',
      accessor: (row: FrutalesScore) => row.handicapCourse?.toFixed(1) || '-',
      width: '7%',
    },
    {
      header: 'Gross',
      accessor: (row: FrutalesScore) => row.scoreGross || '-',
      width: '6%',
    },
    {
      header: 'Neto',
      accessor: (row: FrutalesScore) => {
        return row.scoreNeto != null ? <strong>{row.scoreNeto}</strong> : '-';
      },
      width: '6%',
    },
    {
      header: 'Birdie',
      accessor: (row: FrutalesScore) => row.birdieCount || '-',
      width: '5%',
    },
    {
      header: 'Aguila',
      accessor: (row: FrutalesScore) => row.eagleCount || '-',
      width: '5%',
    },
    {
      header: 'Ace',
      accessor: (row: FrutalesScore) => row.aceCount || '-',
      width: '5%',
    },
    {
      header: 'Puntos',
      accessor: (row: FrutalesScore) => (
        <strong style={{ color: '#2980b9' }}>
          {row.totalPoints}
          {daPlayerIds.has(row.playerId) ? ' (DA)' : ''}
        </strong>
      ),
      width: '7%',
    },
  ];

  const actions: TableAction<FrutalesScore>[] = [
    {
      label: 'Editar tarjetas',
      onClick: (row) => {
        void handleEditScorecard(row);
      },
      variant: 'primary',
    },
    {
      label: 'Dar de baja',
      onClick: (row) => {
        if (row.scorecardId) {
          setError('No se puede dar de baja porque ya tiene tarjeta creada');
          return;
        }
        handleRemoveInscription(row);
      },
      variant: 'danger',
    },
    {
      label: (row) => (row.status === 'DISQUALIFIED' ? 'Quitar Descalificación' : 'Descalificar'),
      onClick: (row) => {
        setConfirmDialog(
          row.status === 'DISQUALIFIED'
            ? { kind: 'undoDisqualify', entry: row }
            : { kind: 'disqualify', entry: row }
        );
      },
      variant: 'danger',
      show: (row) => Boolean(row.scorecardId),
    },
    {
      label: (row) => (row.status === 'CANCELLED' ? 'Habilitar Tarjeta' : 'Cancelar Tarjeta'),
      onClick: (row) => {
        setConfirmDialog(
          row.status === 'CANCELLED'
            ? { kind: 'undoCancelScorecard', entry: row }
            : { kind: 'cancelScorecard', entry: row }
        );
      },
      variant: 'danger',
      show: (row) => Boolean(row.scorecardId),
    },
  ];

  if (loading) return <div className="loading">Cargando leaderboard...</div>;

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button onClick={() => navigate('/tournaments')} className="btn-back">
            ← Volver a Torneos
          </button>
          {tournament?.estado === 'IN_PROGRESS' && (
            <button
              onClick={handleFinalizeTournament}
              disabled={finalizing}
              className="btn-finalize-tournament"
              style={finalizing ? { opacity: 0.7 } : undefined}
            >
              {finalizing ? 'Finalizando...' : 'Finalizar Torneo'}
            </button>
          )}
          <button
            onClick={handleCalculateScores}
            disabled={calculating}
            className="btn-calculate"
          >
            {calculating ? 'Calculando...' : 'Calcular Puntos'}
          </button>
          {tournament?.estado === 'FINALIZED' && (
            <button
              onClick={copyResultsMessage}
              className="btn-copy-link"
            >
              Link de Resultados
            </button>
          )}
          {tournament && (
            <button onClick={() => setShowPrintModal(true)} className="btn-export">
              Imprimir Tarjetas
            </button>
          )}
        </div>

        <div className="tournament-info">
          <h1>{tournament?.nombre}</h1>
          {tournament?.doublePoints && <span className="final-badge" style={{ backgroundColor: '#8e44ad' }}>FECHA DOBLE</span>}
          {isFinal && <span className="final-badge">RESULTADOS FINALES</span>}
          <div className="tournament-details">
            <span className="detail-item">
              <strong>Campo:</strong> {tournament?.courseName}
            </span>
            <span className="detail-item">
              <strong>Fecha:</strong> {tournament?.fechaInicio ? formatDateSafe(tournament.fechaInicio) : ''}
            </span>
            <span className="detail-item">
              <strong>Estado:</strong> {tournament?.estado === 'IN_PROGRESS' ? 'En Proceso' : tournament?.estado === 'FINALIZED' ? 'Finalizado' : 'Pendiente'}
            </span>
            <span className="detail-item">
              <strong>Jugadores:</strong> {tournament?.currentInscriptos}
            </span>
            <span className="detail-item">
              <strong>Código:</strong> <span className="tournament-code">{tournament?.codigo}</span>
            </span>
          </div>
        </div>
      </div>

      <div className="search-container" style={{ marginBottom: '1.5rem' }}>
        <div className="search-input-wrapper" style={{ width: '50%' }}>
          <input
            type="text"
            placeholder="Buscar jugadores por nombre o matrícula"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="search-input"
            style={{
              width: '100%',
              padding: '0.75rem 2rem 0.75rem 1rem',
              fontSize: '1rem',
              border: '1px solid #e0e0e0',
              borderRadius: '4px',
              transition: 'border-color 0.3s',
            }}
            onFocus={(e) => e.target.style.borderColor = '#3498db'}
            onBlur={(e) => e.target.style.borderColor = '#e0e0e0'}
          />
          {searchQuery && (
            <button className="search-clear-btn" onClick={() => setSearchQuery('')} type="button">×</button>
          )}
        </div>
        {searchQuery && (
          <p style={{ marginTop: '0.5rem', fontSize: '0.875rem', color: '#7f8c8d' }}>
            Mostrando {filteredScores.length} de {frutalesScores.length} jugadores
          </p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {frutalesScores.length === 0 ? (
        <div className="empty-state">
          <h2>No hay jugadores inscriptos</h2>
          <p>Cuando haya jugadores inscriptos aparecerán en esta tabla.</p>
        </div>
      ) : (
        <>
          <div className="leaderboard-container">
            <Table
              data={filteredScores}
              columns={columns}
              actions={actions}
              emptyMessage="No hay jugadores que coincidan con la búsqueda"
              getRowKey={(row) => `${row.playerId}-${row.scorecardId ?? 'no-scorecard'}`}
            />
          </div>
          <div className="update-info">
            <span className="live-indicator"></span>
            <span>Actualizando en tiempo real cada 100 segundos</span>
          </div>
        </>
      )}

      <ResultsMessageModal
        message={resultsMessageModal}
        onClose={() => setResultsMessageModal(null)}
      />

      {tournament && (
        <PrintScorecardsModal
          isOpen={showPrintModal}
          onClose={() => setShowPrintModal(false)}
          tournament={tournament}
        />
      )}

      <Modal
        isOpen={confirmDialog !== null}
        onClose={() => {
          if (!confirmActionLoading) setConfirmDialog(null);
        }}
        onConfirm={executeConfirmDialog}
        title={
          confirmDialog?.kind === 'disqualify'
            ? 'Descalificar tarjeta'
            : confirmDialog?.kind === 'undoDisqualify'
              ? 'Quitar descalificación'
              : confirmDialog?.kind === 'cancelScorecard'
                ? 'Cancelar tarjeta'
                : 'Habilitar tarjeta'
        }
        message={
          confirmDialog?.kind === 'disqualify'
            ? `¿Descalificar la tarjeta de ${confirmDialog.entry.playerName}?`
            : confirmDialog?.kind === 'undoDisqualify'
              ? `¿Quitar la descalificación de ${confirmDialog.entry.playerName}?`
              : confirmDialog?.kind === 'cancelScorecard'
                ? `¿Cancelar la tarjeta de ${confirmDialog.entry.playerName}? No se computarán sus golpes.`
                : confirmDialog?.kind === 'undoCancelScorecard'
                  ? `¿Habilitar nuevamente la tarjeta de ${confirmDialog.entry.playerName}?`
                  : undefined
        }
        type="confirm"
        confirmText="Confirmar"
        cancelText="Cancelar"
      />

      {/* Edit Scorecard Modal */}
      {editingScorecardId && editingScorecard && (
        <div className="modal-overlay" onClick={handleCloseModal}>
          <div className="modal-content scorecard-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h2>Editar Tarjeta - {editingScorecard.playerName}</h2>
                <p className="scorecard-info">
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}>
                    HCP:
                    <input
                      type="number"
                      min="0"
                      max="54"
                      step="0.1"
                      value={editingScorecard.handicapCourse != null ? Number(editingScorecard.handicapCourse) : ''}
                      onChange={(e) => handleHcpChange(e.target.value)}
                      style={{
                        width: '60px',
                        padding: '1px 4px',
                        fontSize: 'inherit',
                        border: '1px solid #cbd5e1',
                        borderRadius: '4px',
                        textAlign: 'center',
                      }}
                    />
                  </span>
                  {' | '}Score:{' '}
                  {editingScorecard.holeScores.reduce((sum, hs) => sum + (hs.golpesPropio || 0), 0) || '-'}
                </p>
              </div>
              <button className="modal-close" onClick={handleCloseModal}>×</button>
            </div>

            <div className="modal-body">
              <div className="scorecard-table-wrapper">
                <table className="modal-scorecard-table">
                  <thead>
                    <tr className="scorecard-header-row">
                      <th className="scorecard-sticky-col">HOYO</th>
                      {editingScorecard.holeScores
                        .sort((a, b) => a.numeroHoyo - b.numeroHoyo)
                        .map((holeScore) => (
                          <th key={holeScore.id}>{holeScore.numeroHoyo}</th>
                        ))}
                      <th className="scorecard-total-col">TOTAL</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr className="scorecard-par-row">
                      <td className="scorecard-sticky-col scorecard-label">PAR</td>
                      {editingScorecard.holeScores
                        .sort((a, b) => a.numeroHoyo - b.numeroHoyo)
                        .map((holeScore) => (
                          <td key={holeScore.id} className="scorecard-par-cell">{holeScore.par}</td>
                        ))}
                      <td className="scorecard-total-cell">
                        {editingScorecard.holeScores.reduce((sum, hs) => sum + hs.par, 0)}
                      </td>
                    </tr>
                    <tr className="scorecard-score-row">
                      <td className="scorecard-sticky-col scorecard-label scorecard-player-label">SCORE</td>
                      {editingScorecard.holeScores
                        .sort((a, b) => a.numeroHoyo - b.numeroHoyo)
                        .map((holeScore) => (
                          <td key={holeScore.id} className="scorecard-input-cell">
                            <input
                              type="number"
                              min="1"
                              max="15"
                              value={holeScore.golpesPropio || ''}
                              onChange={(e) => handleScoreChange(holeScore.id, parseInt(e.target.value))}
                              className="scorecard-score-input"
                            />
                          </td>
                        ))}
                      <td className="scorecard-total-cell scorecard-score-total">
                        {editingScorecard.holeScores.reduce((sum, hs) => sum + (hs.golpesPropio || 0), 0) || '-'}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div className="modal-footer" style={{ justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', gap: '1.5rem' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: editingScorecard.holeScores.every(hs => hs.golpesPropio != null && hs.golpesPropio > 0) ? 'pointer' : 'not-allowed', color: editingScorecard.holeScores.every(hs => hs.golpesPropio != null && hs.golpesPropio > 0) ? '#2c3e50' : '#bdc3c7' }}>
                  <input
                    type="checkbox"
                    checked={markAsDelivered}
                    onChange={(e) => setMarkAsDelivered(e.target.checked)}
                    disabled={!editingScorecard.holeScores.every(hs => hs.golpesPropio != null && hs.golpesPropio > 0) || editingScorecard.status === 'DELIVERED'}
                    style={{ width: '18px', height: '18px', cursor: 'inherit' }}
                  />
                  Entregada
                </label>
              </div>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button onClick={handleCloseModal} className="btn-cancel">Cancelar</button>
                <button onClick={handleSaveScorecard} className="btn-save" disabled={savingScorecard}>
                  {savingScorecard ? 'Guardando...' : 'Guardar Cambios'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default FrutalesLeaderboardPage;
