import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { createPortal } from 'react-dom';
import { tournamentAdminService } from '../services/tournamentAdminService';
import { tournamentService } from '../services/tournamentService';
import { playerService } from '../services/playerService';
import { TournamentAdmin, Tournament, Player, TournamentRelationOption } from '../types';
import Table, { TableAction } from '../components/Table';
import Modal from '../components/Modal';
import { formatDateSafe } from '../utils/dateUtils';
import { formatCurrency, parseCurrency } from '../utils/currencyUtils';
import '../components/Form.css';

const AdministrationPage = () => {
  const navigate = useNavigate();
  const [admins, setAdmins] = useState<TournamentAdmin[]>([]);
  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Create/Edit modal
  const [showModal, setShowModal] = useState(false);
  const [editingAdmin, setEditingAdmin] = useState<TournamentAdmin | null>(null);
  const [formData, setFormData] = useState({
    nombre: '',
    fecha: '',
    relatedTournamentIds: [] as number[],
    valorInscripcion: '',
    cantidadCuotas: '1',
  });
  const [relationOptions, setRelationOptions] = useState<TournamentRelationOption[]>([]);
  const [showRelationsDropdown, setShowRelationsDropdown] = useState(false);
  const relationInputRef = useRef<HTMLDivElement | null>(null);
  const [relationsDropdownStyle, setRelationsDropdownStyle] = useState<React.CSSProperties>({});

  // Inscription modal
  const [showInscriptionModal, setShowInscriptionModal] = useState(false);
  const [selectedAdmin, setSelectedAdmin] = useState<TournamentAdmin | null>(null);
  const [allPlayers, setAllPlayers] = useState<Player[]>([]);
  const [inscribedPlayerIds, setInscribedPlayerIds] = useState<Set<number>>(new Set());
  const [selectedPlayers, setSelectedPlayers] = useState<Set<number>>(new Set());
  const [searchQuery, setSearchQuery] = useState('');
  const [loadingPlayers, setLoadingPlayers] = useState(false);
  const [savingInscription, setSavingInscription] = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [adminsData, tournamentsData] = await Promise.all([
        tournamentAdminService.getAll(),
        tournamentService.getAll(),
      ]);
      setAdmins(adminsData);
      setTournaments(tournamentsData);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando datos');
    } finally {
      setLoading(false);
    }
  };

  const handleValorInscripcionChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const input = e.target.value;
    const cleaned = input.replace(/[^\d,]/g, '');
    if (!cleaned) {
      setFormData({ ...formData, valorInscripcion: '' });
      return;
    }
    const parts = cleaned.split(',');
    let integerPart = parts[0];
    let decimalPart = parts[1] || '';
    if (decimalPart.length > 2) {
      decimalPart = decimalPart.substring(0, 2);
    }
    integerPart = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    let formatted = integerPart;
    if (parts.length > 1) {
      formatted += ',' + decimalPart;
    }
    setFormData({ ...formData, valorInscripcion: formatted });
  };

  const loadRelationOptions = async (adminId?: number) => {
    const options = await tournamentAdminService.getRelationOptions(adminId);
    setRelationOptions(options);
  };

  const handleSelectRelatedTournament = (tournamentId: number) => {
    setShowRelationsDropdown(false);
    setRelationOptions(prev => prev.map(opt =>
      opt.id === tournamentId ? { ...opt, related: true } : opt
    ));

    setFormData(prev => {
      if (prev.relatedTournamentIds.includes(tournamentId)) return prev;
      const tournament = tournaments.find(t => t.id === tournamentId);
      const shouldSetDefaultValue = !prev.valorInscripcion && tournament?.valorInscripcion;
      return {
        ...prev,
        relatedTournamentIds: [...prev.relatedTournamentIds, tournamentId],
        valorInscripcion:
          shouldSetDefaultValue && tournament?.valorInscripcion
            ? formatCurrency(tournament.valorInscripcion)
            : prev.valorInscripcion,
      };
    });
  };

  const handleRemoveRelatedTournament = (tournamentId: number) => {
    setRelationOptions(prev => prev.map(opt =>
      opt.id === tournamentId ? { ...opt, related: false } : opt
    ));
    setFormData(prev => ({
      ...prev,
      relatedTournamentIds: prev.relatedTournamentIds.filter(id => id !== tournamentId),
    }));
  };

  const handleCreate = async () => {
    setEditingAdmin(null);
    setFormData({
      nombre: '',
      fecha: '',
      relatedTournamentIds: [],
      valorInscripcion: '',
      cantidadCuotas: '1',
    });
    setShowRelationsDropdown(false);
    await loadRelationOptions();
    setShowModal(true);
  };

  useEffect(() => {
    if (!showRelationsDropdown || !relationInputRef.current) return;

    const updatePosition = () => {
      if (!relationInputRef.current) return;
      const rect = relationInputRef.current.getBoundingClientRect();
      const availableAbove = Math.max(80, rect.top - 16);
      const maxHeight = Math.min(220, availableAbove);
      const bottom = Math.max(8, window.innerHeight - rect.top + 6);
      setRelationsDropdownStyle({
        position: 'fixed',
        left: rect.left,
        width: rect.width,
        bottom,
        maxHeight,
        zIndex: 20000,
      });
    };

    updatePosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [showRelationsDropdown]);

  const handleEdit = async (admin: TournamentAdmin) => {
    setEditingAdmin(admin);
    setFormData({
      nombre: admin.nombre,
      fecha: admin.fecha,
      relatedTournamentIds: admin.relatedTournamentIds || [],
      valorInscripcion: formatCurrency(admin.valorInscripcion),
      cantidadCuotas: String(admin.cantidadCuotas),
    });
    setShowRelationsDropdown(false);
    await loadRelationOptions(admin.id);
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        nombre: formData.nombre,
        fecha: formData.fecha,
        relatedTournamentIds: formData.relatedTournamentIds,
        valorInscripcion: parseCurrency(formData.valorInscripcion),
        cantidadCuotas: parseInt(formData.cantidadCuotas),
      };

      if (editingAdmin) {
        await tournamentAdminService.update(editingAdmin.id, payload);
      } else {
        await tournamentAdminService.create(payload);
      }
      setShowModal(false);
      loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error guardando torneo');
    }
  };

  const handleDelete = async (admin: TournamentAdmin) => {
    if (!confirm(`¿Estás seguro de querer eliminar "${admin.nombre}"?`)) return;
    try {
      await tournamentAdminService.delete(admin.id);
      loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error eliminando torneo');
    }
  };

  const handleFinalize = async (admin: TournamentAdmin) => {
    if (!confirm(`¿Estás seguro de querer finalizar "${admin.nombre}"?`)) return;
    try {
      await tournamentAdminService.finalize(admin.id);
      loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error finalizando torneo');
    }
  };

  const handleAdminister = (admin: TournamentAdmin) => {
    navigate(`/administration/${admin.id}`);
  };

  // Inscription logic
  const handleInscribe = async (admin: TournamentAdmin) => {
    setSelectedAdmin(admin);
    setShowInscriptionModal(true);
    setSearchQuery('');
    setSelectedPlayers(new Set());
    try {
      setLoadingPlayers(true);
      const [players, detail] = await Promise.all([
        playerService.getAll(),
        tournamentAdminService.getDetail(admin.id),
      ]);
      setAllPlayers(players);
      const inscribedIds = new Set<number>(detail.inscriptions.map(i => i.playerId));
      setInscribedPlayerIds(inscribedIds);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando jugadores');
    } finally {
      setLoadingPlayers(false);
    }
  };

  const availablePlayers = allPlayers.filter(p => !inscribedPlayerIds.has(p.id));

  const filteredPlayers = searchQuery
    ? availablePlayers.filter(p =>
        `${p.nombre} ${p.apellido} ${p.matricula}`.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : availablePlayers;

  const togglePlayer = (playerId: number) => {
    const newSelected = new Set(selectedPlayers);
    if (newSelected.has(playerId)) {
      newSelected.delete(playerId);
    } else {
      newSelected.add(playerId);
    }
    setSelectedPlayers(newSelected);
  };

  const toggleAll = () => {
    if (selectedPlayers.size === filteredPlayers.length) {
      setSelectedPlayers(new Set());
    } else {
      setSelectedPlayers(new Set(filteredPlayers.map(p => p.id)));
    }
  };

  const handleSaveInscriptions = async () => {
    if (!selectedAdmin || selectedPlayers.size === 0) return;
    try {
      setSavingInscription(true);
      const playerIds = Array.from(selectedPlayers.values()) as number[];
      const promises = playerIds.map((playerId) =>
        tournamentAdminService.inscribePlayer(selectedAdmin.id, playerId)
      );
      await Promise.all(promises);
      alert(`${selectedPlayers.size} jugador(es) inscripto(s) exitosamente`);
      setShowInscriptionModal(false);
      setSelectedAdmin(null);
      loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error inscribiendo jugadores');
    } finally {
      setSavingInscription(false);
    }
  };

  const handleManageStages = (admin: TournamentAdmin) => {
    navigate(`/administration/${admin.id}/stages`);
  };

  const columns = [
    { header: 'Nombre', accessor: 'nombre' as keyof TournamentAdmin },
    { header: 'Fecha', accessor: (row: TournamentAdmin) => formatDateSafe(row.fecha) },
    { header: 'Jugadores', accessor: (row: TournamentAdmin) => row.currentInscriptos },
    { header: 'Valor Inscripción', accessor: (row: TournamentAdmin) => `$${formatCurrency(row.valorInscripcion)}` },
    { header: 'T. Recaudado', accessor: (row: TournamentAdmin) => (
      <span style={{ color: '#4CAF50', fontWeight: 'bold' }}>${formatCurrency(row.totalRecaudado)}</span>
    )},
    { header: 'Cuotas', accessor: 'cantidadCuotas' as keyof TournamentAdmin },
    {
      header: 'Estado',
      accessor: (row: TournamentAdmin) => (
        <span className={`status-badge status-${row.estado?.toLowerCase()}`}>
          {row.estado === 'ACTIVE' ? 'Activo' : 'Finalizado'}
        </span>
      ),
    },
  ];

  const actions: TableAction<TournamentAdmin>[] = [
    {
      label: 'Inscribir',
      onClick: handleInscribe,
      variant: 'secondary',
      show: (admin) => admin.estado === 'ACTIVE',
    },
    {
      label: 'Administrar',
      onClick: handleAdminister,
      variant: 'primary',
    },
    {
      label: 'Administrar Etapas',
      onClick: handleManageStages,
      variant: 'secondary',
      show: (admin) => admin.estado === 'ACTIVE',
    },
    {
      label: 'Editar',
      onClick: handleEdit,
      variant: 'default',
      show: (admin) => admin.estado === 'ACTIVE',
    },
    {
      label: 'Finalizar',
      onClick: handleFinalize,
      variant: 'danger',
      show: (admin) => admin.estado === 'ACTIVE',
    },
    {
      label: 'Eliminar',
      onClick: handleDelete,
      variant: 'danger',
    },
  ];

  if (loading) return <div className="loading">Cargando administración...</div>;

  return (
    <div>
      <div className="page-header">
        <h1>Administración de Torneos</h1>
        <button onClick={handleCreate} className="btn btn-primary">
          Crear Torneo
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}

      <Table data={admins} columns={columns} actions={actions} emptyMessage="No hay torneos administrativos creados" />

      {/* Create/Edit Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => {
          setShowModal(false);
          setShowRelationsDropdown(false);
        }}
        title={editingAdmin ? 'Editar Torneo' : 'Crear Torneo'}
        size="large"
        footer={
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            <button
              type="button"
              onClick={() => {
                setShowModal(false);
                setShowRelationsDropdown(false);
              }}
              className="btn btn-cancel"
            >
              Cancelar
            </button>
            <button type="submit" form="admin-tournament-form" className="btn btn-primary">
              {editingAdmin ? 'Actualizar' : 'Crear'}
            </button>
          </div>
        }
      >
        <form id="admin-tournament-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Nombre del Torneo *</label>
            <input
              type="text"
              value={formData.nombre}
              onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
              required
            />
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Fecha del Torneo *</label>
              <input
                type="date"
                value={formData.fecha}
                onChange={(e) => setFormData({ ...formData, fecha: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label>Torneos Relacionados</label>
              <div ref={relationInputRef} style={{ position: 'relative', zIndex: showRelationsDropdown ? 2000 : 'auto' }}>
                <button
                  type="button"
                  onClick={() => setShowRelationsDropdown(prev => !prev)}
                  style={{
                    width: '100%',
                    padding: '0.75rem',
                    border: '2px solid #e0e0e0',
                    borderRadius: '6px',
                    fontSize: '1rem',
                    textAlign: 'left',
                    background: 'white',
                    cursor: 'pointer',
                  }}
                >
                  {relationOptions.filter(o => !o.related).length > 0
                    ? 'Seleccionar torneos...'
                    : 'No hay torneos disponibles'}
                </button>

                {showRelationsDropdown && createPortal(
                  <div
                    style={{
                      ...relationsDropdownStyle,
                      background: 'white',
                      border: '1px solid #e0e0e0',
                      borderRadius: '6px',
                      overflowY: 'auto',
                      boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
                    }}
                  >
                    {relationOptions.filter(o => !o.related).length === 0 ? (
                      <div style={{ padding: '0.75rem', color: '#666' }}>
                        No hay torneos disponibles
                      </div>
                    ) : (
                      relationOptions
                        .filter(o => !o.related)
                        .map(option => (
                          <button
                            key={option.id}
                            type="button"
                            onClick={() => handleSelectRelatedTournament(option.id)}
                            style={{
                              width: '100%',
                              textAlign: 'left',
                              padding: '0.65rem 0.75rem',
                              border: 'none',
                              background: 'white',
                              cursor: 'pointer',
                            }}
                          >
                            {option.nombre}
                          </button>
                        ))
                    )}
                  </div>,
                  document.body
                )}
              </div>

              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginTop: '0.6rem' }}>
                {relationOptions
                  .filter(o => o.related)
                  .map(option => (
                    <span
                      key={option.id}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '0.4rem',
                        background: '#eef4ff',
                        color: '#1f4b99',
                        border: '1px solid #d8e6ff',
                        borderRadius: '999px',
                        padding: '0.2rem 0.6rem',
                        fontSize: '0.85rem',
                      }}
                    >
                      {option.nombre}
                      <button
                        type="button"
                        onClick={() => handleRemoveRelatedTournament(option.id)}
                        style={{
                          border: 'none',
                          background: 'transparent',
                          cursor: 'pointer',
                          color: '#1f4b99',
                          fontWeight: 700,
                          lineHeight: 1,
                        }}
                      >
                        ×
                      </button>
                    </span>
                  ))}
              </div>
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Valor de Inscripción *</label>
              <input
                type="text"
                value={formData.valorInscripcion}
                onChange={handleValorInscripcionChange}
                placeholder="Ej: 1.500,00"
                inputMode="decimal"
                required
              />
            </div>
            <div className="form-group">
              <label>Cantidad de Cuotas *</label>
              <input
                type="number"
                min="1"
                value={formData.cantidadCuotas}
                onChange={(e) => setFormData({ ...formData, cantidadCuotas: e.target.value })}
                required
              />
            </div>
          </div>
        </form>
      </Modal>

      {/* Inscription Modal */}
      <Modal
        isOpen={showInscriptionModal}
        onClose={() => {
          setShowInscriptionModal(false);
          setSelectedAdmin(null);
        }}
        title={`Inscribir Jugadores - ${selectedAdmin?.nombre || ''}`}
        size="large"
        footer={
          !loadingPlayers ? (
            <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
              <button
                type="button"
                onClick={() => { setShowInscriptionModal(false); setSelectedAdmin(null); }}
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
                <div className="search-box">
                  <div className="search-input-wrapper" style={{ width: '100%' }}>
                    <input
                      type="text"
                      placeholder="Buscar jugadores por nombre o matrícula..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="search-input"
                      style={{ paddingRight: '2rem' }}
                    />
                    {searchQuery && (
                      <button className="search-clear-btn" onClick={() => setSearchQuery('')} type="button">×</button>
                    )}
                  </div>
                </div>
                <div className="inscription-stats">
                  <p><strong>Jugadores disponibles:</strong> {availablePlayers.length}</p>
                  <p><strong>Seleccionados:</strong> {selectedPlayers.size}</p>
                </div>
              </div>

              {filteredPlayers.length === 0 ? (
                <div className="empty-state">
                  <p>No hay jugadores disponibles para inscripción</p>
                  {searchQuery && <p>Intenta con un término de búsqueda diferente</p>}
                </div>
              ) : (
                <>
                  <div className="select-all-row">
                    <label>
                      <input
                        type="checkbox"
                        checked={selectedPlayers.size === filteredPlayers.length && filteredPlayers.length > 0}
                        onChange={toggleAll}
                      />
                      <span>Seleccionar Todos ({filteredPlayers.length})</span>
                    </label>
                  </div>

                  <div className="players-list">
                    {filteredPlayers.map((player) => (
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

export default AdministrationPage;
