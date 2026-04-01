import { useState, useEffect, useMemo } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { leaderboardService } from '../services/leaderboardService';
import { scorecardService } from '../services/scorecardService';
import { inscriptionService } from '../services/inscriptionService';
import { Tournament, LeaderboardEntry, Scorecard } from '../types';
import Table, { TableAction } from '../components/Table';
import Tabs, { Tab } from '../components/Tabs';
import ManualInscriptionModal from '../components/ManualInscriptionModal';
import Modal from '../components/Modal';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';
import './TournamentScorecardPage.css';

const TournamentLeaderboardPage = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isFinal = searchParams.get('final') === 'true';

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [activeTab, setActiveTab] = useState<string>('general'); // 'general', categoryId, or 'sin-categoria'
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editingScorecardId, setEditingScorecardId] = useState<number | null>(null);
  const [editingScorecard, setEditingScorecard] = useState<Scorecard | null>(null);
  const [savingScorecard, setSavingScorecard] = useState(false);
  const [paymentChanges, setPaymentChanges] = useState<Map<number, boolean>>(new Map());
  const [savingPayments, setSavingPayments] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [showCopyLinkModal, setShowCopyLinkModal] = useState(false);
  const [markAsDelivered, setMarkAsDelivered] = useState(false);
  const [showInscriptionModal, setShowInscriptionModal] = useState(false);
  const [prizeConfirmation, setPrizeConfirmation] = useState<{
    prizeType: string;
    prizeLabel: string;
    row: LeaderboardEntry;
  } | null>(null);
  const [assigningPrize, setAssigningPrize] = useState(false);
  const [confirmDialog, setConfirmDialog] = useState<
    | null
    | { kind: 'finalize' }
    | { kind: 'reopen' }
    | { kind: 'removeInscription'; entry: LeaderboardEntry }
  >(null);
  const [confirmActionLoading, setConfirmActionLoading] = useState(false);

  useEffect(() => {
    loadData();
  }, [id]); // Removed selectedCategory from dependencies since we filter in frontend

  const loadData = async (options?: { silent?: boolean }) => {
    if (!id) return;
    try {
      if (!options?.silent) setLoading(true);
      const tournamentData = await tournamentService.getById(parseInt(id));
      setTournament(tournamentData);

      // Always fetch all players - filtering is done in frontend
      const leaderboardData = await leaderboardService.getLeaderboard(parseInt(id));
      setLeaderboard(leaderboardData);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading leaderboard');
    } finally {
      if (!options?.silent) setLoading(false);
    }
  };

  const openFinalizeConfirm = () => {
    if (!tournament || !id) return;
    setConfirmDialog({ kind: 'finalize' });
  };

  const openReopenConfirm = () => {
    if (!tournament || !id) return;
    setConfirmDialog({ kind: 'reopen' });
  };

  const executeConfirmDialog = async () => {
    if (!confirmDialog || !id) return;
    try {
      setConfirmActionLoading(true);
      if (confirmDialog.kind === 'finalize') {
        await tournamentService.finalize(parseInt(id, 10));
        await loadData({ silent: true });
        navigate(`/tournaments/${id}/leaderboard?final=true`, { replace: true });
      } else if (confirmDialog.kind === 'reopen') {
        await tournamentService.reopen(parseInt(id, 10));
        await loadData({ silent: true });
        navigate(`/tournaments/${id}/leaderboard`, { replace: true });
      } else if (confirmDialog.kind === 'removeInscription') {
        await inscriptionService.removeInscription(confirmDialog.entry.inscriptionId);
        await loadData();
        setError('');
      }
      setConfirmDialog(null);
    } catch (err: any) {
      const msg =
        err.response?.data?.message ||
        (confirmDialog.kind === 'finalize'
          ? 'Error al finalizar el torneo'
          : confirmDialog.kind === 'reopen'
            ? 'Error al habilitar el torneo'
            : 'Error dando de baja al jugador');
      setError(msg);
    } finally {
      setConfirmActionLoading(false);
    }
  };

  // Build tabs based on tournament categories
  const tabs = useMemo((): Tab[] => {
    if (!tournament || !tournament.categories) return [];

    const tabsList: Tab[] = [];
    
    // Always add "General" tab first
    tabsList.push({
      id: 'general',
      label: 'General',
      count: leaderboard.length,
    });

    // Sort categories by handicap range (min to max)
    const sortedCategories = [...tournament.categories].sort((a, b) => 
      a.handicapMin - b.handicapMin
    );

    // Add tab for each category
    sortedCategories.forEach(category => {
      if (!category.id) return; // Skip categories without id
      
      const count = leaderboard.filter(entry => entry.categoryId === category.id).length;
      tabsList.push({
        id: category.id.toString(),
        label: `${category.nombre} (${category.handicapMin}-${category.handicapMax})`,
        count,
      });
    });

    // Add "Scratch" tab: all delivered players ranked by Score Gross
    const deliveredCount = leaderboard.filter(entry => entry.status === 'DELIVERED').length;
    tabsList.push({
      id: 'scratch',
      label: 'Scratch',
      count: deliveredCount,
    });

    return tabsList;
  }, [tournament, leaderboard]);

  // Filter leaderboard based on active tab and recalculate positions per category
  const filteredByCategory = useMemo(() => {
    let filtered: LeaderboardEntry[] = [];
    
    // Filter by active tab
    if (activeTab === 'general') {
      filtered = [...leaderboard];
    } else if (activeTab === 'scratch') {
      // All delivered players, sorted by Score Gross
      const delivered = leaderboard
        .filter(entry => entry.status === 'DELIVERED')
        .sort((a, b) => (a.scoreGross ?? 0) - (b.scoreGross ?? 0));
      return delivered.map((entry, index) => ({ ...entry, position: index + 1 }));
    } else {
      // Filter by specific category ID
      const categoryId = parseInt(activeTab);
      filtered = leaderboard.filter(entry => entry.categoryId === categoryId);
    }
    
    // Separate players with delivered scorecards from those without
    const withDeliveredScores = filtered.filter(entry => entry.status === 'DELIVERED');
    const withoutDeliveredScores = filtered.filter(entry => entry.status !== 'DELIVERED');
    
    // Assign positions ONLY for players with delivered scorecards
    const withDeliveredAndPositions = withDeliveredScores.map((entry, index) => ({
      ...entry,
      position: index + 1
    }));
    
    // Combine: delivered first (sorted by score), then undelivered (sorted by name)
    return [...withDeliveredAndPositions, ...withoutDeliveredScores];
  }, [leaderboard, activeTab]);

  const getScoreToPar = (scoreToPar: number) => {
    if (scoreToPar === 0) return 'E';
    if (scoreToPar > 0) return `+${scoreToPar}`;
    return `${scoreToPar}`;
  };

  const getPositionClass = (position: number) => {
    if (position === 1) return 'position-first';
    if (position === 2) return 'position-second';
    if (position === 3) return 'position-third';
    return '';
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

  const handleScoreChange = (holeScoreId: number, value: string) => {
    if (!editingScorecard) return;
    const parsed = value === '' ? undefined : parseInt(value, 10);
    const newScore = parsed === undefined || Number.isNaN(parsed) ? undefined : parsed;

    setEditingScorecard({
      ...editingScorecard,
      holeScores: editingScorecard.holeScores.map((hs) =>
        hs.id === holeScoreId ? { ...hs, golpesPropio: newScore } : hs
      ),
    });
  };

  const scorecardModalLayout = useMemo(() => {
    if (!editingScorecard?.holeScores?.length) return null;
    const sorted = [...editingScorecard.holeScores].sort((a, b) => a.numeroHoyo - b.numeroHoyo);
    const hasBackNine = sorted.some((h) => h.numeroHoyo > 9);
    const frontNine = sorted.filter((h) => h.numeroHoyo <= 9);
    const backNine = sorted.filter((h) => h.numeroHoyo > 9);
    const sumGolpes = (holes: typeof sorted) =>
      holes.reduce((s, h) => s + (h.golpesPropio ?? 0), 0);
    const sumPar = (holes: typeof sorted) => holes.reduce((s, h) => s + h.par, 0);
    const totalGross = sumGolpes(sorted);
    const totalParSum = sumPar(sorted);
    const frontGross = sumGolpes(frontNine);
    const backGross = sumGolpes(backNine);
    const frontPar = sumPar(frontNine);
    const backPar = sumPar(backNine);
    const hc = editingScorecard.handicapCourse;
    const neto =
      totalGross > 0 && hc != null && !Number.isNaN(Number(hc)) ? totalGross - Number(hc) : null;
    return {
      sorted,
      hasBackNine,
      frontNine,
      backNine,
      frontGross,
      backGross,
      frontPar,
      backPar,
      totalGross,
      totalParSum,
      neto,
    };
  }, [editingScorecard]);

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

  const handlePaymentChange = (inscriptionId: number, pagado: boolean) => {
    // Encontrar el entry original en el leaderboard para comparar con el valor del servidor
    const originalEntry = leaderboard.find(entry => entry.inscriptionId === inscriptionId);
    const originalPagado = originalEntry?.pagado || false;
    
    setPaymentChanges((prev: Map<number, boolean>) => {
      const newMap = new Map(prev);
      
      // Si el nuevo valor es igual al original del servidor, remover el cambio
      if (pagado === originalPagado) {
        newMap.delete(inscriptionId);
      } else {
        // Si es diferente, agregar/actualizar el cambio
        newMap.set(inscriptionId, pagado);
      }
      
      return newMap;
    });
  };

  const getPaymentStatus = (entry: LeaderboardEntry): boolean => {
    // Si hay un cambio pendiente, usar ese valor
    if (paymentChanges.has(entry.inscriptionId)) {
      return paymentChanges.get(entry.inscriptionId)!;
    }
    // Si no hay cambio, usar el valor actual del servidor
    return entry.pagado || false;
  };

  const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('es-AR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount);
  };
  
  const calculateTotalPaid = (): number => {
    if (!tournament?.valorInscripcion) return 0;
    
    let total = 0;
    leaderboard.forEach((entry: LeaderboardEntry) => {
      const isPaid = getPaymentStatus(entry);
      if (isPaid) {
        total += tournament.valorInscripcion || 0;
      }
    });
    
    return total;
  };

  const handleSavePayments = async () => {
    if (!id || paymentChanges.size === 0) return;

    try {
      setSavingPayments(true);
      
      // Convertir el Map a un array de PaymentUpdate
      const payments = Array.from(paymentChanges.entries()).map(([inscriptionId, pagado]) => ({
        inscriptionId,
        pagado
      }));

      await leaderboardService.updatePayments(parseInt(id), payments);

      // Limpiar los cambios pendientes
      setPaymentChanges(new Map());

      // Recargar datos
      await loadData();
      
      setError('');
    } catch (err: any) {
      console.error('Error saving payments:', err);
      setError(err.response?.data?.message || 'Error al guardar los pagos');
    } finally {
      setSavingPayments(false);
    }
  };

  const handleRemoveInscription = (entry: LeaderboardEntry) => {
    setConfirmDialog({ kind: 'removeInscription', entry });
  };

  const getResultsLink = (codigo: string) => {
    return `${window.location.origin}/results/${codigo}`;
  };

  const copyResultsLink = () => {
    if (!tournament?.codigo) return;
    navigator.clipboard.writeText(getResultsLink(tournament.codigo));
    setShowCopyLinkModal(true);
  };

  // Apply search filter to the category-filtered leaderboard
  const filteredLeaderboard = searchQuery
    ? filteredByCategory.filter((entry: LeaderboardEntry) =>
        `${entry.playerName} ${entry.matricula} ${entry.clubOrigen || ''}`.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : filteredByCategory;

  const columns = [
    {
      header: 'Pos',
      accessor: (row: LeaderboardEntry) => (
        row.status === 'DELIVERED' && row.position && row.position > 0 ? (
          <span className={`position ${getPositionClass(row.position)}`}>{row.position}</span>
        ) : (
          <span>-</span>
        )
      ),
      width: '60px',
    },
    { header: 'Jugador', accessor: 'playerName' as keyof LeaderboardEntry, width: '15%' },
    { header: 'Matrícula', accessor: 'matricula' as keyof LeaderboardEntry, width: '10%' },
    {
      header: 'HCP I',
      accessor: (row: LeaderboardEntry) => row.handicapIndex?.toFixed(1) || '-',
      width: '8%',
    },
    {
      header: 'HCP C',
      accessor: (row: LeaderboardEntry) => row.handicapCourse?.toFixed(1) || '-',
      width: '8%',
    },
    { 
      header: 'Score Gross', 
      accessor: (row: LeaderboardEntry) => row.status === 'DELIVERED' ? <strong>{row.scoreGross}</strong> : '-',
      width: '10%' 
    },
    { 
      header: 'Score Neto', 
      accessor: (row: LeaderboardEntry) => row.status === 'DELIVERED' ? <strong>{row.scoreNeto}</strong> : '-', 
      width: '10%' 
    },
    {
      header: 'To Par',
      accessor: (row: LeaderboardEntry) => (
        row.status === 'DELIVERED' ? (
          <span className={`score-to-par ${row.scoreToPar < 0 ? 'under-par' : row.scoreToPar > 0 ? 'over-par' : 'even-par'}`}>
            {getScoreToPar(row.scoreToPar)}
          </span>
        ) : (
          <span>-</span>
        )
      ),
      width: '8%',
    },
    { header: 'Club', accessor: (row: LeaderboardEntry) => row.clubOrigen || '-', width: '10%' },
    {
      header: 'Pagado',
      accessor: (row: LeaderboardEntry) => (
        <input
          type="checkbox"
          checked={getPaymentStatus(row)}
          onChange={(e) => handlePaymentChange(row.inscriptionId, e.target.checked)}
          style={{ cursor: 'pointer', width: '18px', height: '18px' }}
        />
      ),
      width: '7%',
    },
  ];

  const PRIZE_LABELS: Record<string, string> = {
    LONG_DRIVER: 'Long Driver',
    BEST_DRIVER: 'Best Driver',
    BEST_APPROACH: 'Best Approach',
  };

  const prizeActions: TableAction<LeaderboardEntry>[] = (tournament?.prizes || []).map((prize) => ({
    label: PRIZE_LABELS[prize.prizeType] || prize.prizeType,
    onClick: (row) => {
      setPrizeConfirmation({
        prizeType: prize.prizeType,
        prizeLabel: PRIZE_LABELS[prize.prizeType] || prize.prizeType,
        row,
      });
    },
    variant: 'primary' as const,
    show: (row) => prize.winnerInscriptionId !== row.inscriptionId,
  }));

  const actions: TableAction<LeaderboardEntry>[] = [
    {
      label: 'Editar',
      onClick: (row) => {
        if (row.scorecardId) {
          handleEditScorecard(row.scorecardId);
        }
      },
      variant: 'primary',
      show: (row) => Boolean(row.scorecardId),
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
    ...prizeActions,
  ];

  if (loading) return <div className="loading">Cargando leaderboard...</div>;

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button onClick={() => navigate('/tournaments')} className="btn-back">
            ← Volver a Torneos
          </button>
          <button onClick={() => loadData()} className="btn-refresh" disabled={loading}>
            {loading ? '⟳ Actualizando...' : '⟳ Actualizar'}
          </button>
          {tournament && (tournament.estado === 'PENDING' || tournament.estado === 'IN_PROGRESS') && (
            <button onClick={() => setShowInscriptionModal(true)} className="btn-refresh">
              Inscribir
            </button>
          )}
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
              📋 Link Resultados
            </button>
          )}
          {tournament?.estado === 'IN_PROGRESS' && (
            <button
              type="button"
              onClick={openFinalizeConfirm}
              className="btn-finalize-tournament"
              style={{
                backgroundColor: '#c0392b',
                color: 'white',
                padding: '10px 20px',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 'bold',
              }}
            >
              Finalizar
            </button>
          )}
          {tournament?.estado === 'FINALIZED' && (
            <button
              type="button"
              onClick={openReopenConfirm}
              className="btn-reopen-tournament"
              style={{
                backgroundColor: '#27ae60',
                color: 'white',
                padding: '10px 20px',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 'bold',
              }}
            >
              Habilitar
            </button>
          )}
          <button 
            onClick={handleSavePayments} 
            className="btn-save-payments" 
            disabled={savingPayments || paymentChanges.size === 0}
            style={{
              backgroundColor: paymentChanges.size > 0 ? '#4CAF50' : '#ccc',
              color: 'white',
              padding: '10px 20px',
              border: 'none',
              borderRadius: '4px',
              cursor: paymentChanges.size > 0 ? 'pointer' : 'not-allowed',
              fontWeight: 'bold'
            }}
          >
            {savingPayments ? 'Guardando...' : `Guardar Pagos ${paymentChanges.size > 0 ? `(${paymentChanges.size})` : ''}`}
          </button>
        </div>
        
        <div className="tournament-info">
          <h1>{tournament?.nombre}</h1>
          {isFinal && <span className="final-badge">FINAL RESULTS</span>}
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
            <span className="detail-item">
              <strong>Total Recaudado:</strong> <span style={{ color: '#4CAF50', fontWeight: 'bold' }}>
                ${formatCurrency(calculateTotalPaid())}
              </span>
            </span>
          </div>
          {(tournament?.prizes || []).length > 0 && (
            <div style={{ marginTop: '0.75rem', color: '#7f8c8d', fontSize: '0.95rem' }}>
              {(tournament?.prizes || []).map((prize) => {
                const labels: Record<string, string> = {
                  LONG_DRIVER: 'Long Driver',
                  BEST_DRIVER: 'Best Driver',
                  BEST_APPROACH: 'Best Approach',
                };
                return (
                  <div key={prize.id} style={{ display: 'flex', gap: '0.4rem', marginBottom: '0.50rem' }}>
                    <strong style={{ color: '#2c3e50' }}>{labels[prize.prizeType] || prize.prizeType}:</strong>
                    {prize.winnerName
                      ? <span>{prize.winnerName}</span>
                      : <em>Pendiente</em>
                    }
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {tournament && tabs.length > 0 && (
        <Tabs 
          tabs={tabs} 
          activeTab={activeTab} 
          onTabChange={setActiveTab} 
        />
      )}

      <div className="search-container" style={{ marginBottom: '1.5rem' }}>
        <div className="search-input-wrapper" style={{ width: '50%' }}>
          <input
            type="text"
            placeholder="Buscar jugadores por nombre, matrícula o club"
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
          <p style={{ 
            marginTop: '0.5rem', 
            fontSize: '0.875rem', 
            color: '#7f8c8d' 
          }}>
            Mostrando {filteredLeaderboard.length} de {filteredByCategory.length} jugadores
          </p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {leaderboard.length === 0 ? (
        <div className="empty-state">
          <h2>No hay Jugadores Inscritos</h2>
          <p>No hay jugadores inscriptos en el torneo</p>
        </div>
      ) : (
        <>
          <div className="leaderboard-container">
            <Table 
              data={filteredLeaderboard} 
              columns={columns} 
              actions={actions}
              emptyMessage="No hay jugadores que coincidan con la búsqueda"
              getRowKey={(row) => row.playerId}
            />
          </div>
          <div className="update-info">
            {filteredByCategory.filter(entry => entry.status === 'DELIVERED').length > 0 && (
              <span style={{ marginLeft: '20px' }}>
                • Tarjetas entregadas: {filteredByCategory.filter(entry => entry.status === 'DELIVERED').length} de {filteredByCategory.length}
              </span>
            )}
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
              Cerrar
            </button>
          </div>
        </div>
      )}

      {tournament && (
        <ManualInscriptionModal
          isOpen={showInscriptionModal}
          onClose={() => setShowInscriptionModal(false)}
          tournament={tournament}
          onSuccess={async () => {
            setShowInscriptionModal(false);
            await loadData();
          }}
        />
      )}

      <Modal
        isOpen={confirmDialog !== null}
        onClose={() => {
          if (!confirmActionLoading) setConfirmDialog(null);
        }}
        title={
          confirmDialog?.kind === 'finalize'
            ? 'Finalizar torneo'
            : confirmDialog?.kind === 'reopen'
              ? 'Habilitar torneo'
              : 'Dar de baja'
        }
        size="medium"
        footer={
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: '0.75rem',
              width: '100%',
            }}
          >
            <button
              type="button"
              className="modal-btn modal-btn-cancel"
              onClick={() => !confirmActionLoading && setConfirmDialog(null)}
              disabled={confirmActionLoading}
            >
              Cancelar
            </button>
            <button
              type="button"
              className="modal-btn modal-btn-primary"
              onClick={executeConfirmDialog}
              disabled={confirmActionLoading}
            >
              {confirmActionLoading
                ? 'Procesando…'
                : confirmDialog?.kind === 'removeInscription'
                  ? 'Dar de baja'
                  : 'Confirmar'}
            </button>
          </div>
        }
      >
        <p style={{ margin: 0, lineHeight: 1.6, color: '#5a6c7d' }}>
          {confirmDialog?.kind === 'finalize' && tournament && (
            <>
              ¿Finalizar «{tournament.nombre}»? Se cancelarán las tarjetas aún en curso y el torneo
              quedará cerrado.
            </>
          )}
          {confirmDialog?.kind === 'reopen' && tournament && (
            <>
              ¿Habilitar «{tournament.nombre}»? El torneo volverá al estado En Proceso y se podrán cargar o
              corregir tarjetas.
            </>
          )}
          {confirmDialog?.kind === 'removeInscription' && (
            <>
              ¿Dar de baja a <strong>{confirmDialog.entry.playerName}</strong> de este torneo?
            </>
          )}
        </p>
      </Modal>

      {/* Prize Confirmation Modal */}
      {prizeConfirmation && (
        <div className="modal-overlay" onClick={() => setPrizeConfirmation(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '460px' }}>
            <div className="modal-header">
              <h2>Asignar Premio</h2>
              <button className="modal-close" onClick={() => setPrizeConfirmation(null)}>×</button>
            </div>
            <div className="modal-body">
              <p style={{ margin: 0 }}>
                ¿Está seguro que desea seleccionar a{' '}
                <strong>{prizeConfirmation.row.playerName}</strong> como ganador del{' '}
                <strong>{prizeConfirmation.prizeLabel}</strong>?
              </p>
            </div>
            <div className="modal-footer" style={{ justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                onClick={() => setPrizeConfirmation(null)}
                className="btn-cancel"
                disabled={assigningPrize}
              >
                Cancelar
              </button>
              <button
                onClick={async () => {
                  if (!tournament || !prizeConfirmation) return;
                  try {
                    setAssigningPrize(true);
                    await tournamentService.assignPrizeWinner(
                      tournament.id,
                      prizeConfirmation.prizeType,
                      prizeConfirmation.row.inscriptionId
                    );
                    setPrizeConfirmation(null);
                    await loadData();
                  } catch (err: any) {
                    setError(err.response?.data?.message || 'Error al asignar ganador');
                    setPrizeConfirmation(null);
                  } finally {
                    setAssigningPrize(false);
                  }
                }}
                className="btn-save"
                disabled={assigningPrize}
              >
                {assigningPrize ? 'Guardando...' : 'Confirmar'}
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
                  HCP: {editingScorecard.handicapCourse?.toFixed(1)} | Gross:{' '}
                  {scorecardModalLayout?.totalGross != null && scorecardModalLayout.totalGross > 0
                    ? scorecardModalLayout.totalGross
                    : '-'}
                  {scorecardModalLayout?.neto != null && (
                    <> | Neto: {Number.isInteger(scorecardModalLayout.neto) ? scorecardModalLayout.neto : scorecardModalLayout.neto.toFixed(1)}</>
                  )}
                </p>
              </div>
              <button className="modal-close" onClick={handleCloseModal}>×</button>
            </div>
            
            <div className="modal-body">
              <div className="scorecard-table-wrapper scorecard-modal-table-inner">
                {scorecardModalLayout && (
                  <div className="scorecard-table-container">
                    <table className="scorecard-table">
                      <tbody>
                        {scorecardModalLayout.hasBackNine ? (
                          <>
                            <tr className="handicap-row">
                              <td className="sticky-col label-cell">HCP</td>
                              {scorecardModalLayout.frontNine.map((holeScore) => (
                                <td key={holeScore.id} className="hcp-cell">
                                  -
                                </td>
                              ))}
                              <td className="subtotal-cell hcp-cell"></td>
                              {scorecardModalLayout.backNine.map((holeScore) => (
                                <td key={holeScore.id} className="hcp-cell">
                                  -
                                </td>
                              ))}
                              <td className="subtotal-cell hcp-cell"></td>
                              <td></td>
                              <td></td>
                            </tr>
                            <tr className="par-row">
                              <td className="sticky-col label-cell">PAR</td>
                              {scorecardModalLayout.frontNine.map((holeScore) => (
                                <td key={holeScore.id} className="par-cell">
                                  {holeScore.par}
                                </td>
                              ))}
                              <td className="subtotal-cell par-cell">{scorecardModalLayout.frontPar}</td>
                              {scorecardModalLayout.backNine.map((holeScore) => (
                                <td key={holeScore.id} className="par-cell">
                                  {holeScore.par}
                                </td>
                              ))}
                              <td className="subtotal-cell par-cell">{scorecardModalLayout.backPar}</td>
                              <td className="total-cell final-total-cell">{scorecardModalLayout.totalParSum}</td>
                              <td className="total-cell final-total-cell"></td>
                            </tr>
                            <tr className="hoyo-row">
                              <td className="sticky-col label-cell">HOYO</td>
                              {scorecardModalLayout.frontNine.map((holeScore) => (
                                <td key={holeScore.id} className="hoyo-cell">
                                  {holeScore.numeroHoyo}
                                </td>
                              ))}
                              <td className="subtotal-cell hoyo-cell">IDA</td>
                              {scorecardModalLayout.backNine.map((holeScore) => (
                                <td key={holeScore.id} className="hoyo-cell">
                                  {holeScore.numeroHoyo}
                                </td>
                              ))}
                              <td className="subtotal-cell hoyo-cell">VTA</td>
                              <td className="total-cell final-total-cell">GROSS</td>
                              <td className="total-cell final-total-cell">NETO</td>
                            </tr>
                            <tr className="score-row player-row">
                              <td className="sticky-col label-cell player-label">TU</td>
                              {scorecardModalLayout.frontNine.map((holeScore) => (
                                <td key={holeScore.id}>
                                  <input
                                    type="number"
                                    min="1"
                                    max="15"
                                    value={holeScore.golpesPropio ?? ''}
                                    onChange={(e) => handleScoreChange(holeScore.id, e.target.value)}
                                    className="score-input"
                                  />
                                </td>
                              ))}
                              <td className="subtotal-cell score-total">
                                {scorecardModalLayout.frontGross || '-'}
                              </td>
                              {scorecardModalLayout.backNine.map((holeScore) => (
                                <td key={holeScore.id}>
                                  <input
                                    type="number"
                                    min="1"
                                    max="15"
                                    value={holeScore.golpesPropio ?? ''}
                                    onChange={(e) => handleScoreChange(holeScore.id, e.target.value)}
                                    className="score-input"
                                  />
                                </td>
                              ))}
                              <td className="subtotal-cell score-total">
                                {scorecardModalLayout.backGross || '-'}
                              </td>
                              <td className="total-cell score-total">
                                {scorecardModalLayout.totalGross || '-'}
                              </td>
                              <td className="total-cell score-total neto-cell">
                                {scorecardModalLayout.neto != null ? scorecardModalLayout.neto : '-'}
                              </td>
                            </tr>
                          </>
                        ) : (
                          <>
                            <tr className="par-row">
                              <td className="sticky-col label-cell">PAR</td>
                              {scorecardModalLayout.sorted.map((holeScore) => (
                                <td key={holeScore.id} className="par-cell">
                                  {holeScore.par}
                                </td>
                              ))}
                              <td className="total-cell final-total-cell">{scorecardModalLayout.totalParSum}</td>
                              <td className="total-cell final-total-cell"></td>
                            </tr>
                            <tr className="hoyo-row">
                              <td className="sticky-col label-cell">HOYO</td>
                              {scorecardModalLayout.sorted.map((holeScore) => (
                                <td key={holeScore.id} className="hoyo-cell">
                                  {holeScore.numeroHoyo}
                                </td>
                              ))}
                              <td className="total-cell final-total-cell">GROSS</td>
                              <td className="total-cell final-total-cell">NETO</td>
                            </tr>
                            <tr className="score-row player-row">
                              <td className="sticky-col label-cell player-label">TU</td>
                              {scorecardModalLayout.sorted.map((holeScore) => (
                                <td key={holeScore.id}>
                                  <input
                                    type="number"
                                    min="1"
                                    max="15"
                                    value={holeScore.golpesPropio ?? ''}
                                    onChange={(e) => handleScoreChange(holeScore.id, e.target.value)}
                                    className="score-input"
                                  />
                                </td>
                              ))}
                              <td className="total-cell score-total">
                                {scorecardModalLayout.totalGross || '-'}
                              </td>
                              <td className="total-cell score-total neto-cell">
                                {scorecardModalLayout.neto != null ? scorecardModalLayout.neto : '-'}
                              </td>
                            </tr>
                          </>
                        )}
                      </tbody>
                    </table>
                  </div>
                )}
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
                <button onClick={handleCloseModal} className="btn-cancel">
                  Cancelar
                </button>
                <button 
                  onClick={handleSaveScorecard} 
                  className="btn-save"
                  disabled={savingScorecard}
                >
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

export default TournamentLeaderboardPage;
