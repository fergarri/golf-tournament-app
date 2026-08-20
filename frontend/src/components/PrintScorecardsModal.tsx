import { useState, useEffect, useMemo } from 'react';
import { Tournament, Player, InscriptionResponse } from '../types';
import { playerService } from '../services/playerService';
import { inscriptionService } from '../services/inscriptionService';
import { scorecardService } from '../services/scorecardService';
import Modal from './Modal';
import './ManualInscriptionModal.css';
import './PrintScorecardsModal.css';

interface PrintScorecardsModalProps {
  isOpen: boolean;
  onClose: () => void;
  tournament: Tournament;
}

type PrintMode = 'INSCRIPTOS' | 'SELECCION';

const PrintScorecardsModal = ({ isOpen, onClose, tournament }: PrintScorecardsModalProps) => {
  const [mode, setMode] = useState<PrintMode>('INSCRIPTOS');
  const [allPlayers, setAllPlayers] = useState<Player[]>([]);
  const [inscriptions, setInscriptions] = useState<InscriptionResponse[]>([]);
  const [selectedPlayers, setSelectedPlayers] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    if (isOpen) {
      loadData();
      setMode('INSCRIPTOS');
      setSearchQuery('');
      setError('');
    }
  }, [isOpen, tournament.id]);

  const loadData = async () => {
    try {
      setLoading(true);
      const [players, tournamentInscriptions] = await Promise.all([
        playerService.getAll(),
        inscriptionService.getTournamentInscriptions(tournament.id),
      ]);
      setAllPlayers(players);
      setInscriptions(tournamentInscriptions);
      setSelectedPlayers(new Set(tournamentInscriptions.map((i) => i.player.id)));
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error al cargar los jugadores');
    } finally {
      setLoading(false);
    }
  };

  const handleModeChange = (newMode: PrintMode) => {
    setMode(newMode);
    setSearchQuery('');
    if (newMode === 'INSCRIPTOS') {
      setSelectedPlayers(new Set(inscriptions.map((i) => i.player.id)));
    } else {
      setSelectedPlayers(new Set());
    }
  };

  const sourcePlayers: Player[] = useMemo(() => {
    if (mode === 'INSCRIPTOS') {
      return inscriptions.map((i) => i.player);
    }
    return allPlayers;
  }, [mode, inscriptions, allPlayers]);

  const filteredPlayers = useMemo(() => {
    if (!searchQuery.trim()) return sourcePlayers;
    const q = searchQuery.toLowerCase().trim();
    return sourcePlayers.filter((p) => {
      const haystack = [`${p.nombre} ${p.apellido}`, p.matricula, p.clubOrigen ?? '']
        .join(' ')
        .toLowerCase();
      return haystack.includes(q);
    });
  }, [sourcePlayers, searchQuery]);

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
    const filteredIds = filteredPlayers.map((p) => p.id);
    const allSelected = filteredIds.length > 0 && filteredIds.every((id) => selectedPlayers.has(id));
    const newSelected = new Set(selectedPlayers);
    if (allSelected) {
      filteredIds.forEach((id) => newSelected.delete(id));
    } else {
      filteredIds.forEach((id) => newSelected.add(id));
    }
    setSelectedPlayers(newSelected);
  };

  const handlePrint = async () => {
    if (selectedPlayers.size === 0) {
      setError('Debe seleccionar al menos un jugador');
      return;
    }
    try {
      setPrinting(true);
      setError('');
      await scorecardService.printScorecards(tournament.id, Array.from(selectedPlayers), tournament.nombre);
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error al generar el PDF de tarjetas');
    } finally {
      setPrinting(false);
    }
  };

  const allFilteredSelected =
    filteredPlayers.length > 0 && filteredPlayers.every((p) => selectedPlayers.has(p.id));

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Imprimir Tarjetas - ${tournament.nombre}`}
      size="large"
      footer={
        !loading && (
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            <button type="button" onClick={onClose} className="btn btn-cancel" disabled={printing}>
              Cancelar
            </button>
            <button
              type="button"
              onClick={handlePrint}
              className="btn btn-primary"
              disabled={printing || selectedPlayers.size === 0}
            >
              {printing ? 'Generando PDF...' : `Imprimir ${selectedPlayers.size} Tarjeta(s)`}
            </button>
          </div>
        )
      }
    >
      <div className="manual-inscription">
        {loading ? (
          <div className="loading">Cargando jugadores...</div>
        ) : (
          <>
            <div className="print-mode-selector">
              <label className={`print-mode-option ${mode === 'INSCRIPTOS' ? 'active' : ''}`}>
                <input
                  type="radio"
                  name="print-mode"
                  checked={mode === 'INSCRIPTOS'}
                  onChange={() => handleModeChange('INSCRIPTOS')}
                />
                <span>Imprimir Inscriptos</span>
              </label>
              <label className={`print-mode-option ${mode === 'SELECCION' ? 'active' : ''}`}>
                <input
                  type="radio"
                  name="print-mode"
                  checked={mode === 'SELECCION'}
                  onChange={() => handleModeChange('SELECCION')}
                />
                <span>Imprimir Selección</span>
              </label>
            </div>

            <div className="inscription-header">
              <div className="search-box">
                <div className="search-input-wrapper" style={{ width: '100%' }}>
                  <input
                    type="text"
                    placeholder="Buscar por nombre, matrícula o club..."
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
                <p><strong>Jugadores:</strong> {sourcePlayers.length}</p>
                <p><strong>Seleccionados:</strong> {selectedPlayers.size}</p>
              </div>
            </div>

            {error && <div className="error-message">{error}</div>}

            {filteredPlayers.length === 0 ? (
              <div className="empty-state">
                <p>
                  {mode === 'INSCRIPTOS'
                    ? 'No hay jugadores inscriptos en este torneo'
                    : 'No hay jugadores registrados'}
                </p>
                {searchQuery && <p>Intenta con un término de búsqueda diferente</p>}
              </div>
            ) : (
              <>
                <div className="select-all-row">
                  <label>
                    <input
                      type="checkbox"
                      checked={allFilteredSelected}
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
          </>
        )}
      </div>
    </Modal>
  );
};

export default PrintScorecardsModal;
