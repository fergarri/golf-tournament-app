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
  const [showBulkUpdateModal, setShowBulkUpdateModal] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [bulkUpdateResult, setBulkUpdateResult] = useState<any>(null);
  const [searchQuery, setSearchQuery] = useState('');

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

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const handleBulkUpdate = async () => {
    if (!selectedFile) {
      setError('Por favor seleccione un archivo');
      return;
    }
    
    try {
      setIsProcessing(true);
      setError('');
      const result = await playerService.bulkUpdate(selectedFile);
      setBulkUpdateResult(result);
      setSelectedFile(null);
      loadPlayers();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error procesando archivo');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleCloseBulkModal = () => {
    setShowBulkUpdateModal(false);
    setSelectedFile(null);
    setBulkUpdateResult(null);
    setError('');
  };

  const filteredPlayers = searchQuery
    ? players.filter(p =>
        `${p.nombre} ${p.apellido} ${p.matricula}`.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : players;

  const columns = [
    { header: 'Nombre', accessor: (row: Player) => `${row.apellido} ${row.nombre}` },
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
        <div style={{ display: 'flex', gap: '10px' }}>
          <button onClick={() => setShowBulkUpdateModal(true)} className="btn btn-secondary" id="update-players">
            Actualizar Jugadores
          </button>
          <button onClick={handleCreate} className="btn btn-primary">
            Crear Jugador
          </button>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="search-container" style={{ marginBottom: '1.5rem' }}>
        <input
          type="text"
          placeholder="Buscar jugadores por nombre, apellido o matrícula"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="search-input"
          style={{
            width: '50%',
            padding: '0.75rem 1rem',
            fontSize: '1rem',
            border: '2px solid #e0e0e0',
            borderRadius: '8px',
            outline: 'none',
            transition: 'border-color 0.2s',
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
            Mostrando {filteredPlayers.length} de {players.length} jugadores
          </p>
        )}
      </div>

      <Table data={filteredPlayers} columns={columns} onEdit={handleEdit} onDelete={handleDelete} />

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingPlayer ? 'Editar Jugador' : 'Crear Jugador'}
        footer={
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            <button type="button" onClick={() => setShowModal(false)} className="btn btn-cancel">
              Cancelar
            </button>
            <button type="submit" form="player-form" className="btn btn-primary">
              {editingPlayer ? 'Actualizar' : 'Crear'}
            </button>
          </div>
        }
      >
        <form id="player-form" onSubmit={handleSubmit}>
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
        </form>
      </Modal>

      <Modal
        isOpen={showBulkUpdateModal}
        onClose={handleCloseBulkModal}
        title="Actualizar Jugadores"
        footer={
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            {!bulkUpdateResult ? (
              <>
                <button 
                  onClick={handleCloseBulkModal} 
                  className="btn btn-cancel"
                  disabled={isProcessing}
                >
                  Cancelar
                </button>
                <button 
                  onClick={handleBulkUpdate} 
                  className="btn btn-primary"
                  disabled={!selectedFile || isProcessing}
                >
                  Procesar
                </button>
              </>
            ) : (
              <button onClick={handleCloseBulkModal} className="btn btn-primary">
                Cerrar
              </button>
            )}
          </div>
        }
      >
        {!bulkUpdateResult ? (
          <div>
            <p style={{ marginBottom: '20px' }}>
              Seleccione el archivo para actualizar la lista de jugadores
            </p>
            
            <div className="form-group">
              <input
                type="file"
                accept=".xlsx"
                onChange={handleFileChange}
                disabled={isProcessing}
              />
            </div>
            
            {selectedFile && (
              <p style={{ marginTop: '10px', fontSize: '14px', color: '#666' }}>
                Archivo seleccionado: {selectedFile.name}
              </p>
            )}
            
            {isProcessing && (
              <div style={{ 
                textAlign: 'center', 
                padding: '20px',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '10px'
              }}>
                <div className="loading-spinner"></div>
                <p>Procesando archivo...</p>
              </div>
            )}
            
            {error && <div className="error-message">{error}</div>}
          </div>
        ) : (
          <div>
            <h3 style={{ color: '#28a745', marginBottom: '20px' }}>
              ✓ Actualización exitosa
            </h3>
            
            <div style={{ marginBottom: '15px' }}>
              <p><strong>Jugadores creados:</strong> {bulkUpdateResult.creados}</p>
              <p><strong>Jugadores actualizados:</strong> {bulkUpdateResult.actualizados}</p>
            </div>
            
            {bulkUpdateResult.matriculasNoProcesadas && 
             bulkUpdateResult.matriculasNoProcesadas.length > 0 && (
              <div style={{ marginTop: '20px' }}>
                <p style={{ color: '#dc3545', fontWeight: 'bold' }}>
                  Matrículas no procesadas:
                </p>
                <ul style={{ 
                  marginTop: '10px', 
                  paddingLeft: '20px',
                  maxHeight: '150px',
                  overflowY: 'auto'
                }}>
                  {bulkUpdateResult.matriculasNoProcesadas.map((mat: string, idx: number) => (
                    <li key={idx}>{mat}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default PlayersPage;
