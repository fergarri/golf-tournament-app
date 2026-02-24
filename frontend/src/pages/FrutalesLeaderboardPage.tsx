import { useState, useEffect, useMemo } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { leaderboardService } from '../services/leaderboardService';
import { scorecardService } from '../services/scorecardService';
import { courseService } from '../services/courseService';
import { inscriptionService } from '../services/inscriptionService';
import { Tournament, FrutalesScore, Scorecard, CourseTee, LeaderboardEntry, InscriptionResponse } from '../types';
import Table from '../components/Table';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';

const FrutalesLeaderboardPage = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isFinal = searchParams.get('final') === 'true';

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [frutalesScores, setFrutalesScores] = useState<FrutalesScore[]>([]);
  const [loading, setLoading] = useState(true);
  const [calculating, setCalculating] = useState(false);
  const [error, setError] = useState('');
  const [editingScorecardId, setEditingScorecardId] = useState<number | null>(null);
  const [editingScorecard, setEditingScorecard] = useState<Scorecard | null>(null);
  const [savingScorecard, setSavingScorecard] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [showCopyLinkModal, setShowCopyLinkModal] = useState(false);
  const [markAsDelivered, setMarkAsDelivered] = useState(false);
  const [showEnableScorecardModal, setShowEnableScorecardModal] = useState(false);
  const [enableScorecardPlayer, setEnableScorecardPlayer] = useState<FrutalesScore | null>(null);
  const [courseTees, setCourseTees] = useState<CourseTee[]>([]);
  const [selectedTeeId, setSelectedTeeId] = useState<number | ''>('');
  const [enablingScorecard, setEnablingScorecard] = useState(false);
  const [finalizing, setFinalizing] = useState(false);

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
      setFrutalesScores(mergeScoresWithInscriptions(scores, entries, inscriptions));
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error al calcular puntos');
    } finally {
      setCalculating(false);
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

  const getPositionClass = (position: number) => {
    if (position === 1) return 'position-first';
    if (position === 2) return 'position-second';
    if (position === 3) return 'position-third';
    return '';
  };

  const handleEnableScorecard = async (entry: FrutalesScore) => {
    setEnableScorecardPlayer(entry);
    setSelectedTeeId('');
    try {
      if (tournament?.courseId) {
        const tees = await courseService.getTees(tournament.courseId);
        const activeTees = tees.filter((t: CourseTee) => t.active);
        setCourseTees(activeTees);
        if (activeTees.length === 1) {
          setSelectedTeeId(activeTees[0].id);
        }
      }
    } catch (err) {
      console.error('Error loading tees:', err);
    }
    setShowEnableScorecardModal(true);
  };

  const handleConfirmEnableScorecard = async () => {
    if (!enableScorecardPlayer || !tournament || !selectedTeeId) return;
    try {
      setEnablingScorecard(true);
      const scorecard = await scorecardService.getOrCreate(
        tournament.id,
        enableScorecardPlayer.playerId,
        selectedTeeId as number
      );
      setShowEnableScorecardModal(false);
      setEnableScorecardPlayer(null);
      setMarkAsDelivered(false);
      setEditingScorecard(scorecard);
      setEditingScorecardId(scorecard.id);
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error al habilitar la tarjeta');
    } finally {
      setEnablingScorecard(false);
    }
  };

  const handleEditScorecard = async (scorecardId: number) => {
    try {
      const scorecard = await scorecardService.getById(scorecardId);
      setMarkAsDelivered(scorecard.status === 'DELIVERED');
      setEditingScorecard(scorecard);
      setEditingScorecardId(scorecardId);
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

  const handleSaveScorecard = async () => {
    if (!editingScorecard) return;
    try {
      setSavingScorecard(true);
      const holeScores = editingScorecard.holeScores.map(hs => ({
        holeId: hs.holeId,
        golpesPropio: hs.golpesPropio || undefined,
        golpesMarcador: hs.golpesMarcador || undefined
      }));

      await scorecardService.updateScorecard(editingScorecard.id, { holeScores });

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

  const getResultsLink = (codigo: string) => {
    return `${window.location.origin}/frutales-results/${codigo}`;
  };

  const copyResultsLink = () => {
    if (!tournament?.codigo) return;
    navigator.clipboard.writeText(getResultsLink(tournament.codigo));
    setShowCopyLinkModal(true);
  };

  const getStatusLabel = (status: string, hasScorecard: boolean) => {
    if (status === 'DISQUALIFIED') return 'DS';
    if (!hasScorecard) return null;
    if (status !== 'DELIVERED') return 'NM';
    return null;
  };

  const filteredScores = searchQuery
    ? frutalesScores.filter((entry: FrutalesScore) =>
        `${entry.playerName} ${entry.matricula}`.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : frutalesScores;

  const daPlayerIds = useMemo(() => {
    const byNeto = new Map<string, number[]>();
    for (const entry of frutalesScores) {
      if (entry.status !== 'DELIVERED' || entry.scoreNeto == null) continue;
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
        if (row.status === 'DISQUALIFIED') return <span style={{ color: '#e74c3c', fontWeight: 'bold' }}>DS</span>;
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
      accessor: (row: FrutalesScore) => {
        const label = getStatusLabel(row.status, Boolean(row.scorecardId));
        if (label) return <span style={{ color: label === 'DS' ? '#e74c3c' : '#f39c12', fontWeight: 'bold' }}>{label}</span>;
        return row.scoreGross || '-';
      },
      width: '6%',
    },
    {
      header: 'Neto',
      accessor: (row: FrutalesScore) => {
        const label = getStatusLabel(row.status, Boolean(row.scorecardId));
        if (label) return <span style={{ color: label === 'DS' ? '#e74c3c' : '#f39c12', fontWeight: 'bold' }}>{label}</span>;
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
    {
      header: 'Acciones',
      accessor: (row: FrutalesScore) => (
        row.scorecardId != null ? (
          <button
            onClick={() => {
              if (row.scorecardId != null) {
                handleEditScorecard(row.scorecardId);
              }
            }}
            className="btn-edit"
          >
            Editar
          </button>
        ) : tournament?.estado !== 'FINALIZED' ? (
          <button
            onClick={() => handleEnableScorecard(row)}
            className="btn-edit"
            style={{ backgroundColor: '#27ae60', borderColor: '#27ae60' }}
          >
            Habilitar Tarjeta
          </button>
        ) : (
          <span style={{ color: '#95a5a6', fontSize: '0.9rem' }}>-</span>
        )
      ),
      width: '10%',
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
          <button onClick={loadData} className="btn-refresh" disabled={loading}>
            {loading ? '⟳ Actualizando...' : '⟳ Actualizar'}
          </button>
          {tournament?.estado === 'IN_PROGRESS' && (
            <button
              onClick={handleFinalizeTournament}
              disabled={finalizing}
              style={{
                backgroundColor: '#e67e22',
                color: 'white',
                padding: '10px 20px',
                border: 'none',
                borderRadius: '4px',
                cursor: finalizing ? 'not-allowed' : 'pointer',
                fontWeight: 'bold',
                opacity: finalizing ? 0.7 : 1,
              }}
            >
              {finalizing ? 'Finalizando...' : 'Finalizar Torneo'}
            </button>
          )}
          <button
            onClick={handleCalculateScores}
            disabled={calculating}
            style={{
              backgroundColor: '#8e44ad',
              color: 'white',
              padding: '10px 20px',
              border: 'none',
              borderRadius: '4px',
              cursor: calculating ? 'not-allowed' : 'pointer',
              fontWeight: 'bold',
              opacity: calculating ? 0.7 : 1,
            }}
          >
            {calculating ? 'Calculando...' : 'Calcular Puntos'}
          </button>
          {tournament?.estado === 'FINALIZED' && (
            <button
              onClick={copyResultsLink}
              className="btn-copy-link"
              style={{
                backgroundColor: '#3498db',
                color: 'white',
                padding: '10px 20px',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 'bold'
              }}
            >
              Link de Resultados
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

      {/* Copy Link Modal */}
      {showCopyLinkModal && (
        <div className="modal-overlay" onClick={() => setShowCopyLinkModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '400px', textAlign: 'center' }}>
            <div style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>✓</div>
            <h3 style={{ marginBottom: '0.5rem' }}>Link Copiado</h3>
            <p style={{ color: '#7f8c8d', marginBottom: '1.5rem' }}>
              El link de resultados ha sido copiado al portapapeles
            </p>
            <button
              onClick={() => setShowCopyLinkModal(false)}
              className="btn-primary"
              style={{ backgroundColor: '#3498db', color: 'white', padding: '10px 20px', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}
            >
              Cerrar
            </button>
          </div>
        </div>
      )}

      {/* Enable Scorecard Modal */}
      {showEnableScorecardModal && enableScorecardPlayer && (
        <div className="modal-overlay" onClick={() => setShowEnableScorecardModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '450px' }}>
            <div className="modal-header">
              <h2>Habilitar Tarjeta</h2>
              <button className="modal-close" onClick={() => setShowEnableScorecardModal(false)}>×</button>
            </div>
            <div className="modal-body">
              <p style={{ marginBottom: '1rem' }}>
                Se creará una tarjeta vacía para <strong>{enableScorecardPlayer.playerName}</strong>.
              </p>
              <div className="form-group">
                <label>Tee *</label>
                <select
                  value={selectedTeeId}
                  onChange={(e) => setSelectedTeeId(parseInt(e.target.value))}
                  required
                  style={{ width: '100%', padding: '0.75rem', fontSize: '1rem', border: '1px solid #e0e0e0', borderRadius: '4px' }}
                >
                  <option value="">Seleccionar tee</option>
                  {courseTees.map((tee) => (
                    <option key={tee.id} value={tee.id}>
                      {tee.nombre} {tee.grupo ? `(${tee.grupo})` : ''}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="modal-footer">
              <button onClick={() => setShowEnableScorecardModal(false)} className="btn-cancel">Cancelar</button>
              <button
                onClick={handleConfirmEnableScorecard}
                className="btn-save"
                disabled={!selectedTeeId || enablingScorecard}
              >
                {enablingScorecard ? 'Habilitando...' : 'Confirmar'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Scorecard Modal */}
      {editingScorecardId && editingScorecard && (
        <div className="modal-overlay" onClick={handleCloseModal}>
          <div className="modal-content scorecard-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h2>Editar Tarjeta - {editingScorecard.playerName}</h2>
                <p className="scorecard-info">
                  HCP: {editingScorecard.handicapCourse?.toFixed(1)} |
                  Score: {editingScorecard.holeScores.reduce((sum, hs) => sum + (hs.golpesPropio || 0), 0) || '-'}
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
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', color: '#e74c3c' }}>
                  <input
                    type="checkbox"
                    checked={editingScorecard.status === 'DISQUALIFIED'}
                    onChange={async (e) => {
                      try {
                        if (e.target.checked) {
                          await scorecardService.disqualifyScorecard(editingScorecard.id);
                        } else {
                          await scorecardService.undoDisqualifyScorecard(editingScorecard.id);
                        }
                        const updated = await scorecardService.getById(editingScorecard.id);
                        setEditingScorecard(updated);
                        await loadData();
                      } catch (err: any) {
                        setError(err.response?.data?.message || 'Error al cambiar estado');
                      }
                    }}
                    style={{ width: '18px', height: '18px', cursor: 'pointer' }}
                  />
                  Descalificado
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
