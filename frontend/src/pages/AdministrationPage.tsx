import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { tournamentAdminService } from '../services/tournamentAdminService';
import { tournamentService } from '../services/tournamentService';
import { playerService } from '../services/playerService';
import { TournamentAdmin, Tournament, Player } from '../types';
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
    tournamentId: '' as string,
    valorInscripcion: '',
    cantidadCuotas: '1',
  });

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

  const handleTournamentChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const tournamentId = e.target.value;
    setFormData(prev => {
      const newData = { ...prev, tournamentId };
      if (tournamentId) {
        const tournament = tournaments.find(t => t.id === parseInt(tournamentId));
        if (tournament?.valorInscripcion) {
          newData.valorInscripcion = formatCurrency(tournament.valorInscripcion);
        }
      }
      return newData;
    });
  };

  const handleCreate = () => {
    setEditingAdmin(null);
    setFormData({
      nombre: '',
      fecha: '',
      tournamentId: '',
      valorInscripcion: '',
      cantidadCuotas: '1',
    });
    setShowModal(true);
  };

  const handleEdit = (admin: TournamentAdmin) => {
    setEditingAdmin(admin);
    setFormData({
      nombre: admin.nombre,
      fecha: admin.fecha,
      tournamentId: admin.tournamentId ? String(admin.tournamentId) : '',
      valorInscripcion: formatCurrency(admin.valorInscripcion),
      cantidadCuotas: String(admin.cantidadCuotas),
    });
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        nombre: formData.nombre,
        fecha: formData.fecha,
        tournamentId: formData.tournamentId ? parseInt(formData.tournamentId) : null,
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
      const promises = Array.from(selectedPlayers).map(playerId =>
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

  const columns = [
    { header: 'Nombre', accessor: 'nombre' as keyof TournamentAdmin },
    { header: 'Fecha', accessor: (row: TournamentAdmin) => formatDateSafe(row.fecha) },
    { header: 'Jugadores', accessor: (row: TournamentAdmin) => row.currentInscriptos },
    { header: 'Torneo Relacionado', accessor: (row: TournamentAdmin) => row.tournamentNombre || '-' },
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
        onClose={() => setShowModal(false)}
        title={editingAdmin ? 'Editar Torneo' : 'Crear Torneo'}
        size="large"
        footer={
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            <button type="button" onClick={() => setShowModal(false)} className="btn btn-cancel">
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
              <label>Torneo Relacionado</label>
              <select
                value={formData.tournamentId}
                onChange={handleTournamentChange}
              >
                <option value="">Ninguno</option>
                {tournaments.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.nombre}
                  </option>
                ))}
              </select>
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
