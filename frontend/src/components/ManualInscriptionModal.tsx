import { useState, useEffect } from 'react';
import { Tournament, Player } from '../types';
import { playerService } from '../services/playerService';
import { inscriptionService } from '../services/inscriptionService';
import Modal from './Modal';
import './ManualInscriptionModal.css';

interface ManualInscriptionModalProps {
  isOpen: boolean;
  onClose: () => void;
  tournament: Tournament;
  onSuccess: () => void;
}

const ManualInscriptionModal = ({ isOpen, onClose, tournament, onSuccess }: ManualInscriptionModalProps) => {
  const [allPlayers, setAllPlayers] = useState<Player[]>([]);
  const [inscribedPlayerIds, setInscribedPlayerIds] = useState<Set<number>>(new Set());
  const [selectedPlayers, setSelectedPlayers] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    if (isOpen) {
      loadData();
    }
  }, [isOpen, tournament.id]);

  const loadData = async () => {
    try {
      setLoading(true);
      const [players, inscriptions] = await Promise.all([
        playerService.getAll(),
        inscriptionService.getTournamentInscriptions(tournament.id),
      ]);

      setAllPlayers(players);
      const inscribedIds = new Set<number>(inscriptions.map((i: any) => i.player.id as number));
      setInscribedPlayerIds(inscribedIds);
      setSelectedPlayers(new Set());
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading players');
    } finally {
      setLoading(false);
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

  const handleSave = async () => {
    if (selectedPlayers.size === 0) {
      alert('Please select at least one player');
      return;
    }

    try {
      setSaving(true);
      const promises = Array.from(selectedPlayers).map(playerId =>
        inscriptionService.inscribePlayerManual(tournament.id, playerId)
      );
      await Promise.all(promises);
      alert(`${selectedPlayers.size} player(s) inscribed successfully`);
      onSuccess();
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error inscribing players');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`Inscribir Jugadores - ${tournament.nombre}`} size="large">
      <div className="manual-inscription">
        {loading ? (
          <div className="loading">Cargando jugadores...</div>
        ) : (
          <>
            <div className="inscription-header">
              <div className="search-box">
                <input
                  type="text"
                  placeholder="Buscar jugadores por nombre o matrícula..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="search-input"
                />
              </div>
              <div className="inscription-stats">
                <p><strong>Jugadores disponibles:</strong> {availablePlayers.length}</p>
                <p><strong>Seleccionados:</strong> {selectedPlayers.size}</p>
              </div>
            </div>

            {error && <div className="error-message">{error}</div>}

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
                            {player.nombre} {player.apellido}
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

            <div className="form-actions">
              <button type="button" onClick={onClose} className="btn btn-cancel" disabled={saving}>
                Cancelar
              </button>
              <button 
                type="button" 
                onClick={handleSave} 
                className="btn btn-primary" 
                disabled={saving || selectedPlayers.size === 0}
              >
                {saving ? 'Inscribiendo...' : `Inscribir ${selectedPlayers.size} Jugador(es)`}
              </button>
            </div>
          </>
        )}
      </div>
    </Modal>
  );
};

export default ManualInscriptionModal;
