import { useState, useEffect } from 'react';
import { playerService } from '../services/playerService';
import { BulkUpdateResult, Player } from '../types';
import Table from '../components/Table';
import Modal from '../components/Modal';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';
import { Users, Plus, Upload, Search, CheckCircle2, Loader2, X } from 'lucide-react';
import '../components/Form.css';

const formatHcpIndex = (value: number | null | undefined): string => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—';
  return Number(value).toFixed(1);
};

const PlayersPage = () => {
  const [players, setPlayers] = useState<Player[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingPlayer, setEditingPlayer] = useState<Player | null>(null);
  const [formData, setFormData] = useState<Partial<Player>>({
    nombre: '', apellido: '', email: '', matricula: '',
    fechaNacimiento: '', sexo: 'M', handicapIndex: 0, telefono: '', clubOrigen: '',
  });
  const [showBulkUpdateModal, setShowBulkUpdateModal] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [bulkUpdateResult, setBulkUpdateResult] = useState<BulkUpdateResult | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => { loadPlayers(); }, []);

  const loadPlayers = async () => {
    try {
      setLoading(true);
      const data = await playerService.getAll();
      setPlayers(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando jugadores');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingPlayer(null);
    setFormData({ nombre: '', apellido: '', email: '', matricula: '', fechaNacimiento: '', sexo: 'M', handicapIndex: 0, telefono: '', clubOrigen: '' });
    setShowModal(true);
  };

  const handleEdit = (player: Player) => {
    setEditingPlayer(player);
    setFormData({
      nombre: player.nombre, apellido: player.apellido, email: player.email || '',
      matricula: player.matricula, fechaNacimiento: player.fechaNacimiento || '',
      sexo: player.sexo, handicapIndex: player.handicapIndex,
      telefono: player.telefono || '', clubOrigen: player.clubOrigen || '',
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
    if (e.target.files && e.target.files[0]) setSelectedFile(e.target.files[0]);
  };

  const handleBulkUpdate = async () => {
    if (!selectedFile) { setError('Por favor seleccioná un archivo'); return; }
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
    ? players.filter(p => `${p.nombre} ${p.apellido} ${p.matricula}`.toLowerCase().includes(searchQuery.toLowerCase()))
    : players;

  const columns = [
    {
      header: 'Nombre',
      accessor: (row: Player) => <span className="font-medium">{row.apellido}, {row.nombre}</span>,
      sortValue: (row: Player) => `${row.apellido} ${row.nombre}`,
    },
    {
      header: 'Matrícula',
      accessor: (row: Player) => <span className="font-mono text-xs bg-slate-100 px-2 py-0.5 rounded">{row.matricula}</span>,
      sortValue: (row: Player) => row.matricula,
    },
    {
      header: 'Sexo',
      accessor: (row: Player) => (
        <Badge variant={row.sexo === 'M' ? 'info' : 'secondary'} className="text-xs">
          {row.sexo === 'M' ? 'Masculino' : 'Femenino'}
        </Badge>
      ),
      sortValue: (row: Player) => row.sexo,
    },
    {
      header: 'Handicap',
      accessor: (row: Player) => <span className="font-semibold text-slate-700">{formatHcpIndex(row.handicapIndex)}</span>,
      sortValue: (row: Player) => row.handicapIndex ?? 0,
    },
    { header: 'Club', accessor: 'clubOrigen' as keyof Player },
    { header: 'Email', accessor: 'email' as keyof Player },
  ];

  if (loading) return <div className="loading">Cargando jugadores...</div>;

  return (
    <div>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 tracking-tight">Jugadores</h1>
          <p className="text-slate-500 text-sm mt-0.5">{players.length} jugadores registrados</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setShowBulkUpdateModal(true)}>
            <Upload className="h-4 w-4" />
            Actualizar
          </Button>
          <Button size="sm" onClick={handleCreate}>
            <Plus className="h-4 w-4" />
            Crear Jugador
          </Button>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      {/* Search */}
      <div className="mb-5">
        <div className="relative w-full sm:max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <Input
            type="text"
            placeholder="Buscar por nombre, apellido o matrícula…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-9 pr-8"
          />
          {searchQuery && (
            <button
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              onClick={() => setSearchQuery('')}
              type="button"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
        {searchQuery && (
          <p className="mt-1.5 text-xs text-slate-500">
            Mostrando {filteredPlayers.length} de {players.length} jugadores
          </p>
        )}
      </div>

      <Table data={filteredPlayers} columns={columns} onEdit={handleEdit} onDelete={handleDelete} />

      {/* Modal editar/crear jugador */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingPlayer ? 'Editar Jugador' : 'Crear Jugador'}
        size="medium"
        footer={
          <div className="flex gap-3 justify-end">
            <Button variant="outline" type="button" onClick={() => setShowModal(false)}>
              Cancelar
            </Button>
            <Button type="submit" form="player-form">
              {editingPlayer ? 'Actualizar' : 'Crear'}
            </Button>
          </div>
        }
      >
        <form id="player-form" onSubmit={handleSubmit}>
          <div className="form-row">
            <div className="form-group">
              <label>Nombre *</label>
              <input type="text" value={formData.nombre} onChange={(e) => setFormData({ ...formData, nombre: e.target.value })} required />
            </div>
            <div className="form-group">
              <label>Apellido *</label>
              <input type="text" value={formData.apellido} onChange={(e) => setFormData({ ...formData, apellido: e.target.value })} required />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label>Matrícula *</label>
              <input type="text" value={formData.matricula} onChange={(e) => setFormData({ ...formData, matricula: e.target.value })} required />
            </div>
            <div className="form-group">
              <label>Sexo *</label>
              <select value={formData.sexo} onChange={(e) => setFormData({ ...formData, sexo: e.target.value as 'M' | 'F' })} required>
                <option value="M">M</option>
                <option value="F">F</option>
              </select>
            </div>
            <div className="form-group">
              <label>Handicap Index *</label>
              <input type="number" step="0.1" value={formData.handicapIndex} onChange={(e) => setFormData({ ...formData, handicapIndex: parseFloat(e.target.value) })} required />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label>Email</label>
              <input type="email" value={formData.email} onChange={(e) => setFormData({ ...formData, email: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Teléfono</label>
              <input type="tel" value={formData.telefono} onChange={(e) => setFormData({ ...formData, telefono: e.target.value })} />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label>Fecha de Nacimiento</label>
              <input type="date" value={formData.fechaNacimiento} onChange={(e) => setFormData({ ...formData, fechaNacimiento: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Club</label>
              <input type="text" value={formData.clubOrigen} onChange={(e) => setFormData({ ...formData, clubOrigen: e.target.value })} />
            </div>
          </div>
        </form>
      </Modal>

      {/* Modal actualización masiva */}
      <Modal
        isOpen={showBulkUpdateModal}
        onClose={handleCloseBulkModal}
        title="Actualizar Jugadores"
        size="medium"
        footer={
          <div className="flex gap-3 justify-end">
            {!bulkUpdateResult ? (
              <>
                <Button variant="outline" onClick={handleCloseBulkModal} disabled={isProcessing}>
                  Cancelar
                </Button>
                <Button onClick={handleBulkUpdate} disabled={!selectedFile || isProcessing}>
                  {isProcessing ? <><Loader2 className="h-4 w-4 animate-spin" /> Procesando…</> : 'Procesar'}
                </Button>
              </>
            ) : (
              <Button onClick={handleCloseBulkModal}>Cerrar</Button>
            )}
          </div>
        }
      >
        {!bulkUpdateResult ? (
          <div className="space-y-4">
            <p className="text-sm text-slate-600">
              Seleccioná el archivo <span className="font-semibold">.xlsx</span> para actualizar la lista de jugadores.
            </p>
            <div>
              <Label htmlFor="bulk-file" className="mb-1.5 block">Archivo Excel (.xlsx)</Label>
              <input
                id="bulk-file"
                type="file"
                accept=".xlsx"
                onChange={handleFileChange}
                disabled={isProcessing}
                className="block w-full text-sm text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-medium file:bg-primary file:text-primary-foreground hover:file:bg-primary/90 file:cursor-pointer cursor-pointer"
              />
              {selectedFile && (
                <p className="mt-1.5 text-xs text-slate-500 flex items-center gap-1.5">
                  <Upload className="h-3 w-3" />
                  {selectedFile.name}
                </p>
              )}
            </div>
            {isProcessing && (
              <div className="flex flex-col items-center gap-2 py-4">
                <Loader2 className="h-8 w-8 animate-spin text-primary" />
                <p className="text-sm text-slate-500">Procesando archivo…</p>
              </div>
            )}
            {error && <div className="error-message">{error}</div>}
          </div>
        ) : (
          <div className="space-y-5">
            <div className="flex items-center gap-2 text-emerald-700 bg-emerald-50 rounded-lg px-4 py-3">
              <CheckCircle2 className="h-5 w-5 shrink-0" />
              <span className="font-semibold">Actualización exitosa</span>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-lg bg-slate-50 border border-slate-200 p-4 text-center">
                <p className="text-2xl font-bold text-slate-800">{bulkUpdateResult.creados}</p>
                <p className="text-xs text-slate-500 mt-0.5">Jugadores creados</p>
              </div>
              <div className="rounded-lg bg-slate-50 border border-slate-200 p-4 text-center">
                <p className="text-2xl font-bold text-slate-800">{bulkUpdateResult.actualizados}</p>
                <p className="text-xs text-slate-500 mt-0.5">Jugadores actualizados</p>
              </div>
            </div>

            {(bulkUpdateResult.altas?.length ?? 0) > 0 && (
              <div>
                <p className="text-sm font-semibold text-slate-700 mb-2 flex items-center gap-1.5">
                  <Users className="h-4 w-4" /> Nuevos jugadores ({bulkUpdateResult.altas!.length})
                </p>
                <div className="max-h-44 overflow-y-auto rounded-lg border border-slate-200">
                  <table className="w-full text-xs">
                    <thead className="sticky top-0 bg-slate-50">
                      <tr>
                        <th className="text-left px-3 py-2 text-slate-600 font-semibold">Matrícula</th>
                        <th className="text-left px-3 py-2 text-slate-600 font-semibold">Apellido y nombre</th>
                      </tr>
                    </thead>
                    <tbody>
                      {bulkUpdateResult.altas!.map((a, idx) => (
                        <tr key={idx} className="border-t border-slate-100 hover:bg-slate-50">
                          <td className="px-3 py-2 font-mono">{a.matricula}</td>
                          <td className="px-3 py-2">{a.apellido}, {a.nombre}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {(bulkUpdateResult.cambiosHandicapIndex?.length ?? 0) > 0 && (
              <div>
                <p className="text-sm font-semibold text-slate-700 mb-2">
                  Cambios de HCP Index ({bulkUpdateResult.cambiosHandicapIndex!.length})
                </p>
                <div className="max-h-52 overflow-y-auto rounded-lg border border-slate-200">
                  <table className="w-full text-xs">
                    <thead className="sticky top-0 bg-slate-50">
                      <tr>
                        <th className="text-left px-3 py-2 text-slate-600 font-semibold">Matrícula</th>
                        <th className="text-left px-3 py-2 text-slate-600 font-semibold">Jugador</th>
                        <th className="text-right px-3 py-2 text-slate-600 font-semibold">Anterior</th>
                        <th className="text-right px-3 py-2 text-slate-600 font-semibold">Nuevo</th>
                      </tr>
                    </thead>
                    <tbody>
                      {bulkUpdateResult.cambiosHandicapIndex!.map((c, idx) => (
                        <tr key={idx} className="border-t border-slate-100 hover:bg-slate-50">
                          <td className="px-3 py-2 font-mono">{c.matricula}</td>
                          <td className="px-3 py-2">{c.apellido}, {c.nombre}</td>
                          <td className="px-3 py-2 text-right text-slate-400">{formatHcpIndex(c.handicapAnterior)}</td>
                          <td className="px-3 py-2 text-right font-semibold text-emerald-700">{formatHcpIndex(c.handicapNuevo)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {(bulkUpdateResult.matriculasNoProcesadas?.length ?? 0) > 0 && (
              <div>
                <p className="text-sm font-semibold text-red-600 mb-2">
                  Matrículas no procesadas ({bulkUpdateResult.matriculasNoProcesadas!.length})
                </p>
                <div className="max-h-36 overflow-y-auto rounded-lg bg-red-50 border border-red-200 p-3">
                  <ul className="text-xs text-red-700 space-y-0.5 list-disc list-inside">
                    {bulkUpdateResult.matriculasNoProcesadas!.map((mat: string, idx: number) => (
                      <li key={idx}>{mat}</li>
                    ))}
                  </ul>
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default PlayersPage;
