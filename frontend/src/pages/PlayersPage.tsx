import { useState, useEffect } from 'react';
import { playerService } from '../services/playerService';
import { Player } from '../types';
import Table from '../components/Table';
import Modal from '../components/Modal';
import '../components/Form.css';

const PlayersPage = () => {
  const [players, setPlayers] = useState<Player[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingPlayer, setEditingPlayer] = useState<Player | null>(null);
  const [formData, setFormData] = useState<Partial<Player>>({
    nombre: '',
    apellido: '',
    email: '',
    matricula: '',
    fechaNacimiento: '',
    handicapIndex: 0,
    telefono: '',
    clubOrigen: '',
  });

  useEffect(() => {
    loadPlayers();
  }, []);

  const loadPlayers = async () => {
    try {
      setLoading(true);
      const data = await playerService.getAll();
      setPlayers(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading players');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingPlayer(null);
    setFormData({
      nombre: '',
      apellido: '',
      email: '',
      matricula: '',
      fechaNacimiento: '',
      handicapIndex: 0,
      telefono: '',
      clubOrigen: '',
    });
    setShowModal(true);
  };

  const handleEdit = (player: Player) => {
    setEditingPlayer(player);
    setFormData({
      nombre: player.nombre,
      apellido: player.apellido,
      email: player.email || '',
      matricula: player.matricula,
      fechaNacimiento: player.fechaNacimiento || '',
      handicapIndex: player.handicapIndex,
      telefono: player.telefono || '',
      clubOrigen: player.clubOrigen || '',
    });
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingPlayer) {
        await playerService.update(editingPlayer.id, formData);
      } else {
        await playerService.create(formData);
      }
      setShowModal(false);
      loadPlayers();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error guardando jugador');
    }
  };

  const handleDelete = async (player: Player) => {
    if (!confirm(`¿Estás seguro de querer eliminar ${player.nombre} ${player.apellido}?`)) return;
    try {
      await playerService.delete(player.id);
      loadPlayers();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error eliminando jugador');
    }
  };

  const columns = [
    { header: 'Nombre', accessor: (row: Player) => `${row.nombre} ${row.apellido}` },
    { header: 'Matricula', accessor: 'matricula' as keyof Player },
    { header: 'Handicap', accessor: 'handicapIndex' as keyof Player },
    { header: 'Club', accessor: 'clubOrigen' as keyof Player },
    { header: 'Email', accessor: 'email' as keyof Player },
  ];

  if (loading) return <div className="loading">Cargando jugadores...</div>;

  return (
    <div>
      <div className="page-header">
        <h1>Jugadores</h1>
        <button onClick={handleCreate} className="btn btn-primary">
          Crear Jugador
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}

      <Table data={players} columns={columns} onEdit={handleEdit} onDelete={handleDelete} />

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingPlayer ? 'Editar Jugador' : 'Crear Jugador'}
      >
        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <div className="form-group">
              <label>Nombre *</label>
              <input
                type="text"
                value={formData.nombre}
                onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label>Apellido *</label>
              <input
                type="text"
                value={formData.apellido}
                onChange={(e) => setFormData({ ...formData, apellido: e.target.value })}
                required
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Matricula *</label>
              <input
                type="text"
                value={formData.matricula}
                onChange={(e) => setFormData({ ...formData, matricula: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label>Handicap Index *</label>
              <input
                type="number"
                step="0.1"
                value={formData.handicapIndex}
                onChange={(e) => setFormData({ ...formData, handicapIndex: parseFloat(e.target.value) })}
                required
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Email</label>
              <input
                type="email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label>Teléfono</label>
              <input
                type="tel"
                value={formData.telefono}
                onChange={(e) => setFormData({ ...formData, telefono: e.target.value })}
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Fecha de Nacimiento</label>
              <input
                type="date"
                value={formData.fechaNacimiento}
                onChange={(e) => setFormData({ ...formData, fechaNacimiento: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label>Club</label>
              <input
                type="text"
                value={formData.clubOrigen}
                onChange={(e) => setFormData({ ...formData, clubOrigen: e.target.value })}
              />
            </div>
          </div>

          <div className="form-actions">
            <button type="button" onClick={() => setShowModal(false)} className="btn btn-cancel">
              Cancelar
            </button>
            <button type="submit" className="btn btn-primary">
              {editingPlayer ? 'Actualizar' : 'Crear'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default PlayersPage;
