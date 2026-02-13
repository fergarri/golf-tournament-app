import { useState, useEffect } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { leaderboardService } from '../services/leaderboardService';
import { scorecardService } from '../services/scorecardService';
import { Tournament, LeaderboardEntry, Scorecard } from '../types';
import Table from '../components/Table';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';

const TournamentLeaderboardPage = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isFinal = searchParams.get('final') === 'true';

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editingScorecardId, setEditingScorecardId] = useState<number | null>(null);
  const [editingScorecard, setEditingScorecard] = useState<Scorecard | null>(null);
  const [savingScorecard, setSavingScorecard] = useState(false);
  const [paymentChanges, setPaymentChanges] = useState<Map<number, boolean>>(new Map());
  const [savingPayments, setSavingPayments] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    loadData();
    
    // Poll for updates every 10 seconds for real-time updates
    const interval = setInterval(() => {
      loadData();
    }, 100000); // 100 seconds

    return () => clearInterval(interval);
  }, [id, selectedCategory]);

  const loadData = async () => {
    if (!id) return;
    try {
      setLoading(true);
      const tournamentData = await tournamentService.getById(parseInt(id));
      setTournament(tournamentData);

      const leaderboardData = await leaderboardService.getLeaderboard(
        parseInt(id),
        selectedCategory || undefined
      );
      setLeaderboard(leaderboardData);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading leaderboard');
    } finally {
      setLoading(false);
    }
  };

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
      
      // Update all hole scores in a single request
      const holeScores = editingScorecard.holeScores.map(hs => ({
        holeId: hs.holeId,
        golpesPropio: hs.golpesPropio || undefined,
        golpesMarcador: hs.golpesMarcador || undefined
      }));

      await scorecardService.updateScorecard(editingScorecard.id, {
        holeScores
      });

      // Reload leaderboard data
      await loadData();
      
      // Close modal
      handleCloseModal();
    } catch (err) {
      console.error('Error saving scorecard:', err);
      setError('Error al guardar los cambios');
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

  const filteredLeaderboard = searchQuery
    ? leaderboard.filter((entry: LeaderboardEntry) =>
        `${entry.playerName} ${entry.matricula} ${entry.clubOrigen || ''}`.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : leaderboard;

  const columns = [
    {
      header: 'Pos',
      accessor: (row: LeaderboardEntry) => (
        row.delivered ? (
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
      header: 'HCP',
      accessor: (row: LeaderboardEntry) => row.handicapCourse?.toFixed(1) || '-',
      width: '8%',
    },
    { 
      header: 'Score Gross', 
      accessor: (row: LeaderboardEntry) => row.delivered ? row.scoreGross : '-',
      width: '10%' 
    },
    { 
      header: 'Score Neto', 
      accessor: (row: LeaderboardEntry) => row.delivered ? <strong>{row.scoreNeto}</strong> : '-', 
      width: '10%' 
    },
    {
      header: 'To Par',
      accessor: (row: LeaderboardEntry) => (
        row.delivered ? (
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
      header: 'Categoría', 
      accessor: (row: LeaderboardEntry) => row.delivered ? (row.categoryName || '-') : '-',
      width: '10%' 
    },
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
    {
      header: 'Acciones',
      accessor: (row: LeaderboardEntry) => (
        row.delivered && row.scorecardId ? (
          <button
            onClick={() => handleEditScorecard(row.scorecardId)}
            className="btn-edit"
          >
            Editar
          </button>
        ) : (
          <span>-</span>
        )
      ),
      width: '8%',
    },
  ];

  if (loading) return <div className="loading">Cargando leaderboard...</div>;

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button onClick={() => navigate('/')} className="btn-back">
            ← Volver al Dashboard
          </button>
          <button onClick={loadData} className="btn-refresh" disabled={loading}>
            {loading ? '⟳ Actualizando...' : '⟳ Actualizar'}
          </button>
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
        </div>
      </div>

      {tournament && tournament.categories && tournament.categories.length > 1 && (
        <div className="category-filter">
          <label>Filtrar por Categoría:</label>
          <select
            value={selectedCategory || ''}
            onChange={(e) => setSelectedCategory(e.target.value ? parseInt(e.target.value) : null)}
            className="category-select"
          >
            <option value="">Todas las Categorías</option>
            {tournament.categories.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.nombre} (HCP {cat.handicapMin}-{cat.handicapMax})
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="search-container" style={{ marginBottom: '1.5rem' }}>
        <input
          type="text"
          placeholder="Buscar jugadores por nombre, matrícula o club"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="search-input"
          style={{
            width: '50%',
            padding: '0.75rem 1rem',
            fontSize: '1rem',
            border: '1px solid #e0e0e0',
            borderRadius: '4px',
            transition: 'border-color 0.3s',
          }}
          onFocus={(e) => e.target.style.borderColor = '#3498db'}
          onBlur={(e) => e.target.style.borderColor = '#e0e0e0'}
        />
        {searchQuery && (
          <p style={{ 
            marginTop: '0.5rem', 
            fontSize: '0.875rem', 
            color: '#7f8c8d' 
          }}>
            Mostrando {filteredLeaderboard.length} de {leaderboard.length} jugadores
          </p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {leaderboard.length === 0 ? (
        <div className="empty-state">
          <h2>No hay Jugadores Inscritos</h2>
          <p>No hay jugadores inscritos en este torneo</p>
        </div>
      ) : (
        <>
          <div className="leaderboard-container">
            <Table 
              data={filteredLeaderboard} 
              columns={columns} 
              emptyMessage="No hay jugadores que coincidan con la búsqueda"
              getRowKey={(row) => row.playerId}
            />
          </div>
          <div className="update-info">
            <span className="live-indicator"></span>
            <span>Actualizando en tiempo real cada 100 segundos</span>
            {leaderboard.filter(entry => entry.delivered).length > 0 && (
              <span style={{ marginLeft: '20px' }}>
                • Tarjetas entregadas: {leaderboard.filter(entry => entry.delivered).length} de {leaderboard.length}
              </span>
            )}
          </div>
        </>
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
                          <td key={holeScore.id} className="scorecard-par-cell">
                            {holeScore.par}
                          </td>
                        ))}
                      <td className="scorecard-total-cell">
                        {editingScorecard.holeScores.reduce((sum, hs) => sum + hs.par, 0)}
                      </td>
                    </tr>
                    <tr className="scorecard-score-row">
                      <td className="scorecard-sticky-col scorecard-label scorecard-player-label">
                        SCORE
                      </td>
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
            
            <div className="modal-footer">
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
      )}
    </div>
  );
};

export default TournamentLeaderboardPage;
