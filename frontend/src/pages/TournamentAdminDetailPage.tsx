import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { tournamentAdminService } from '../services/tournamentAdminService';
import { playerService } from '../services/playerService';
import { TournamentAdminDetail, TournamentAdminInscriptionDetail, Player } from '../types';
import ActionMenu from '../components/ActionMenu';
import Modal from '../components/Modal';
import { formatDateSafe } from '../utils/dateUtils';
import { formatCurrency } from '../utils/currencyUtils';
import '../components/Form.css';
import '../components/Table.css';
import './TournamentLeaderboardPage.css';

const TournamentAdminDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [detail, setDetail] = useState<TournamentAdminDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [showInscriptionModal, setShowInscriptionModal] = useState(false);
  const [allPlayers, setAllPlayers] = useState<Player[]>([]);
  const [inscribedPlayerIds, setInscribedPlayerIds] = useState<Set<number>>(new Set());
  const [selectedPlayers, setSelectedPlayers] = useState<Set<number>>(new Set());
  const [loadingPlayers, setLoadingPlayers] = useState(false);
  const [savingInscription, setSavingInscription] = useState(false);
  const [importingInscriptions, setImportingInscriptions] = useState(false);

  // Track local payment changes: Map<paymentId, pagado>
  const [paymentChanges, setPaymentChanges] = useState<Map<number, boolean>>(new Map());

  useEffect(() => {
    loadData();
  }, [id]);

  const loadData = async () => {
    if (!id) return;
    try {
      setLoading(true);
      const data = await tournamentAdminService.getDetail(parseInt(id));
      setDetail(data);
      setPaymentChanges(new Map());
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando detalle');
    } finally {
      setLoading(false);
    }
  };

  const handlePaymentChange = (paymentId: number, pagado: boolean) => {
    setPaymentChanges(prev => {
      const newMap = new Map(prev);
      const originalPayment = findOriginalPayment(paymentId);
      if (originalPayment && originalPayment.pagado === pagado) {
        newMap.delete(paymentId);
      } else {
        newMap.set(paymentId, pagado);
      }
      return newMap;
    });
  };

  const findOriginalPayment = (paymentId: number) => {
    if (!detail) return null;
    for (const inscription of detail.inscriptions) {
      for (const payment of inscription.payments) {
        if (payment.paymentId === paymentId) return payment;
      }
    }
    return null;
  };

  const getPaymentStatus = (paymentId: number, originalPagado: boolean): boolean => {
    if (paymentChanges.has(paymentId)) {
      return paymentChanges.get(paymentId)!;
    }
    return originalPagado;
  };

  const calculatedTotalRecaudado = useMemo(() => {
    if (!detail) return 0;
    const cuotaValue = detail.cantidadCuotas > 0
      ? detail.valorInscripcion / detail.cantidadCuotas
      : 0;

    let paidCount = 0;
    for (const inscription of detail.inscriptions) {
      for (const payment of inscription.payments) {
        const isPaid = getPaymentStatus(payment.paymentId, payment.pagado);
        if (isPaid) paidCount++;
      }
    }
    return cuotaValue * paidCount;
  }, [detail, paymentChanges]);

  const handleSavePayments = async () => {
    if (!id || paymentChanges.size === 0) return;
    try {
      setSaving(true);
      const payments = Array.from(paymentChanges.entries()).map(([paymentId, pagado]) => ({
        paymentId,
        pagado,
      }));
      await tournamentAdminService.savePayments(parseInt(id), payments);
      setPaymentChanges(new Map());
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error guardando pagos');
    } finally {
      setSaving(false);
    }
  };

  const handleRemoveInscription = async (inscriptionId: number, playerName: string) => {
    if (!confirm(`¿Dar de baja a ${playerName} de este torneo administrativo?`)) return;
    try {
      await tournamentAdminService.removeInscription(inscriptionId);
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error dando de baja al jugador');
    }
  };

  const handleInscribe = async () => {
    if (!detail) return;
    try {
      setLoadingPlayers(true);
      setSelectedPlayers(new Set());
      const players = await playerService.getAll();
      setAllPlayers(players);
      const inscribedIds = new Set<number>(detail.inscriptions.map(i => i.playerId));
      setInscribedPlayerIds(inscribedIds);
      setShowInscriptionModal(true);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando jugadores');
    } finally {
      setLoadingPlayers(false);
    }
  };

  const availablePlayers = allPlayers.filter(p => !inscribedPlayerIds.has(p.id));

  const filteredAvailablePlayers = availablePlayers;

  const togglePlayer = (playerId: number) => {
    const updated = new Set(selectedPlayers);
    if (updated.has(playerId)) {
      updated.delete(playerId);
    } else {
      updated.add(playerId);
    }
    setSelectedPlayers(updated);
  };

  const toggleAllPlayers = () => {
    if (selectedPlayers.size === filteredAvailablePlayers.length) {
      setSelectedPlayers(new Set());
      return;
    }
    setSelectedPlayers(new Set(filteredAvailablePlayers.map(p => p.id)));
  };

  const handleSaveInscriptions = async () => {
    if (!id || selectedPlayers.size === 0) return;
    try {
      setSavingInscription(true);
      const requests = Array.from(selectedPlayers).map(playerId =>
        tournamentAdminService.inscribePlayer(parseInt(id), playerId)
      );
      await Promise.all(requests);
      setShowInscriptionModal(false);
      setSelectedPlayers(new Set());
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error inscribiendo jugadores');
    } finally {
      setSavingInscription(false);
    }
  };

  const handleImportInscriptions = async () => {
    if (!id) return;
    try {
      setImportingInscriptions(true);
      const result = await tournamentAdminService.importInscriptions(parseInt(id));
      alert(
        `Importación completada.\n` +
        `Torneos pendientes relacionados: ${result.relatedPendingTournaments}\n` +
        `Inscriptos importados: ${result.importedCount}\n` +
        `Saltados (ya inscriptos): ${result.skippedAlreadyInscribed}\n` +
        `Saltados (sin cupo): ${result.skippedByCapacity}`
      );
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error importando inscriptos');
    } finally {
      setImportingInscriptions(false);
    }
  };

  const filteredInscriptions = useMemo(() => {
    if (!detail) return [];
    if (!searchQuery) return detail.inscriptions;
    const q = searchQuery.toLowerCase();
    return detail.inscriptions.filter((i: TournamentAdminInscriptionDetail) =>
      `${i.playerName} ${i.email || ''} ${i.telefono || ''}`.toLowerCase().includes(q)
    );
  }, [detail, searchQuery]);

  if (loading) return <div className="loading">Cargando detalle...</div>;
  if (!detail) return <div className="error-message">Torneo no encontrado</div>;

  const cuotaColumns = Array.from({ length: detail.cantidadCuotas }, (_, i) => i + 1);

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button onClick={() => navigate('/administration')} className="btn-back">
            ← Volver a Administración
          </button>
          <button onClick={loadData} className="btn-refresh" disabled={loading}>
            {loading ? '⟳ Actualizando...' : '⟳ Actualizar'}
          </button>
          <button
            onClick={handleInscribe}
            className="btn-refresh"
          >
            Inscribir
          </button>
          <button
            onClick={handleImportInscriptions}
            className="btn-refresh"
            disabled={importingInscriptions}
          >
            {importingInscriptions ? 'Importando...' : 'Importar Inscriptos a Torneos'}
          </button>
          {detail.canManageStages && (
            <button
              onClick={() => navigate(`/administration/${detail.id}/stages`)}
              className="btn-admin-stages"
            >
              Administrar Etapas
            </button>
          )}
          <button
            onClick={handleSavePayments}
            className="btn-save-payments"
            disabled={saving || paymentChanges.size === 0}
            style={{
              backgroundColor: paymentChanges.size > 0 ? '#4CAF50' : '#ccc',
              color: 'white',
              padding: '10px 20px',
              border: 'none',
              borderRadius: '4px',
              cursor: paymentChanges.size > 0 ? 'pointer' : 'not-allowed',
              fontWeight: 'bold',
            }}
          >
            {saving ? 'Guardando...' : `Guardar Pagos ${paymentChanges.size > 0 ? `(${paymentChanges.size})` : ''}`}
          </button>
        </div>

        <div className="tournament-info">
          <h1>{detail.nombre}</h1>
          <div className="tournament-details">
            <span className="detail-item">
              <strong>Fecha:</strong> {formatDateSafe(detail.fecha)}
            </span>
            <span className="detail-item">
              <strong>Jugadores:</strong> {detail.currentInscriptos}
            </span>
            <span className="detail-item">
              <strong>Valor Inscripción:</strong> ${formatCurrency(detail.valorInscripcion)}
            </span>
            <span className="detail-item">
              <strong>Cuotas:</strong> {detail.cantidadCuotas}
            </span>
            <span className="detail-item">
              <strong>Total Recaudado:</strong>{' '}
              <span style={{ color: '#4CAF50', fontWeight: 'bold' }}>
                ${formatCurrency(calculatedTotalRecaudado)}
              </span>
            </span>
          </div>
        </div>
      </div>

      <div className="search-container" style={{ marginBottom: '1.5rem' }}>
        <div className="search-input-wrapper" style={{ width: '50%' }}>
          <input
            type="text"
            placeholder="Buscar jugadores por nombre, email o teléfono"
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
            onFocus={(e) => (e.target.style.borderColor = '#3498db')}
            onBlur={(e) => (e.target.style.borderColor = '#e0e0e0')}
          />
          {searchQuery && (
            <button className="search-clear-btn" onClick={() => setSearchQuery('')} type="button">×</button>
          )}
        </div>
        {searchQuery && (
          <p style={{ marginTop: '0.5rem', fontSize: '0.875rem', color: '#7f8c8d' }}>
            Mostrando {filteredInscriptions.length} de {detail.inscriptions.length} jugadores
          </p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {detail.inscriptions.length === 0 ? (
        <div className="empty-state">
          <h2>No hay Jugadores Inscriptos</h2>
          <p>No hay jugadores inscriptos en este torneo administrativo</p>
        </div>
      ) : (
        <div className="leaderboard-container">
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th style={{ width: '60px', textAlign: 'center' }}>#</th>
                  <th>Jugador</th>
                  <th>Teléfono</th>
                  <th>Email</th>
                  {cuotaColumns.map(n => (
                    <th key={n} style={{ textAlign: 'center', minWidth: '80px' }}>
                      Cuota {n}
                    </th>
                  ))}
                  <th style={{ textAlign: 'center', minWidth: '80px' }}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {filteredInscriptions.length === 0 ? (
                  <tr>
                    <td colSpan={5 + cuotaColumns.length} className="empty-row">
                      No hay jugadores que coincidan con la búsqueda
                    </td>
                  </tr>
                ) : (
                  filteredInscriptions.map((inscription, index) => (
                    <tr key={inscription.inscriptionId}>
                      <td style={{ textAlign: 'center', fontWeight: 600 }}>{index + 1}</td>
                      <td>{inscription.playerName}</td>
                      <td>{inscription.telefono || '-'}</td>
                      <td>{inscription.email || '-'}</td>
                      {cuotaColumns.map(n => {
                        const payment = inscription.payments.find(p => p.cuotaNumber === n);
                        if (!payment) return <td key={n} style={{ textAlign: 'center' }}>-</td>;
                        return (
                          <td key={n} style={{ textAlign: 'center' }}>
                            <input
                              type="checkbox"
                              checked={getPaymentStatus(payment.paymentId, payment.pagado)}
                              onChange={(e) => handlePaymentChange(payment.paymentId, e.target.checked)}
                              style={{ cursor: 'pointer', width: '18px', height: '18px' }}
                            />
                          </td>
                        );
                      })}
                      <td style={{ textAlign: 'center' }}>
                        <ActionMenu
                          items={[
                            {
                              label: 'Dar de baja',
                              variant: 'danger',
                              onClick: () => handleRemoveInscription(inscription.inscriptionId, inscription.playerName),
                            },
                          ]}
                        />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal
        isOpen={showInscriptionModal}
        onClose={() => setShowInscriptionModal(false)}
        title={`Inscribir Jugadores - ${detail.nombre}`}
        size="large"
        footer={
          !loadingPlayers ? (
            <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
              <button
                type="button"
                onClick={() => setShowInscriptionModal(false)}
                className="btn btn-cancel"
                disabled={savingInscription}
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={handleSaveInscriptions}
                className="btn btn-primary"
                disabled={savingInscription || selectedPlayers.size === 0}
              >
                {savingInscription ? 'Inscribiendo...' : `Inscribir ${selectedPlayers.size} Jugador(es)`}
              </button>
            </div>
          ) : undefined
        }
      >
        <div className="manual-inscription">
          {loadingPlayers ? (
            <div className="loading">Cargando jugadores...</div>
          ) : (
            <>
              <div className="inscription-header">
                <div className="inscription-stats">
                  <p><strong>Jugadores disponibles:</strong> {availablePlayers.length}</p>
                  <p><strong>Seleccionados:</strong> {selectedPlayers.size}</p>
                </div>
              </div>

              {filteredAvailablePlayers.length === 0 ? (
                <div className="empty-state">
                  <p>No hay jugadores disponibles para inscripción</p>
                </div>
              ) : (
                <>
                  <div className="select-all-row">
                    <label>
                      <input
                        type="checkbox"
                        checked={selectedPlayers.size === filteredAvailablePlayers.length && filteredAvailablePlayers.length > 0}
                        onChange={toggleAllPlayers}
                      />
                      <span>Seleccionar Todos ({filteredAvailablePlayers.length})</span>
                    </label>
                  </div>

                  <div className="players-list">
                    {filteredAvailablePlayers.map((player) => (
                      <div key={player.id} className="player-item">
                        <label>
                          <input
                            type="checkbox"
                            checked={selectedPlayers.has(player.id)}
                            onChange={() => togglePlayer(player.id)}
                          />
                          <div className="player-info">
                            <div className="player-name">
                              {player.apellido} {player.nombre}
                            </div>
                            <div className="player-details">
                              <span>Reg: {player.matricula}</span>
                              <span>HCP: {player.handicapIndex}</span>
                              {player.clubOrigen && <span>Club: {player.clubOrigen}</span>}
                            </div>
                          </div>
                        </label>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </>
          )}
        </div>
      </Modal>
    </div>
  );
};

export default TournamentAdminDetailPage;
