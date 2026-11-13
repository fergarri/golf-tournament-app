import { useEffect, useMemo, useState } from 'react';
import { Course, CourseTee, Player } from '../types';
import { courseService } from '../services/courseService';
import { scorecardService } from '../services/scorecardService';
import { useAuth } from '../hooks/useAuth';
import Modal from './Modal';
import './ManualInscriptionModal.css';
import './PrintScorecardsModal.css';
import './Form.css';

interface PrintClubScorecardsModalProps {
  isOpen: boolean;
  onClose: () => void;
  players: Player[];
  courses: Course[];
}

type PrintMode = 'JUGADORES' | 'VACIAS';
type PlayerScope = 'club' | 'all';

const fileSlug = (value: string) =>
  value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');

const errorMessage = (err: any, fallback: string): string => {
  const data = err?.response?.data;
  if (data instanceof ArrayBuffer) {
    try {
      const parsed = JSON.parse(new TextDecoder().decode(data));
      if (parsed?.message) return parsed.message;
    } catch {
      return fallback;
    }
  }
  return data?.message || fallback;
};

const PrintClubScorecardsModal = ({ isOpen, onClose, players, courses }: PrintClubScorecardsModalProps) => {
  const { user, isSuperAdmin } = useAuth();
  const lockedCourseId = !isSuperAdmin && user?.courseId ? user.courseId : null;

  const [mode, setMode] = useState<PrintMode>('JUGADORES');
  const [courseId, setCourseId] = useState<number | ''>(lockedCourseId ?? '');
  const [cantidadHoyos, setCantidadHoyos] = useState<9 | 18>(18);
  const [tees, setTees] = useState<CourseTee[]>([]);
  const [teesLoading, setTeesLoading] = useState(false);
  const [teeId, setTeeId] = useState<number | ''>('');
  const [teeMasculinoId, setTeeMasculinoId] = useState<number | ''>('');
  const [teeFemeninoId, setTeeFemeninoId] = useState<number | ''>('');
  const [cantidad, setCantidad] = useState(1);
  const [playerScope, setPlayerScope] = useState<PlayerScope>('club');
  const [selectedPlayers, setSelectedPlayers] = useState<Set<number>>(new Set());
  const [searchQuery, setSearchQuery] = useState('');
  const [printing, setPrinting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    setMode('JUGADORES');
    setCourseId(lockedCourseId ?? '');
    setCantidadHoyos(18);
    setTeeId('');
    setTeeMasculinoId('');
    setTeeFemeninoId('');
    setCantidad(1);
    setPlayerScope('club');
    setSelectedPlayers(new Set());
    setSearchQuery('');
    setError('');
  }, [isOpen, lockedCourseId]);

  useEffect(() => {
    if (!isOpen || courseId === '') {
      setTees([]);
      return;
    }
    let cancelled = false;
    setTeesLoading(true);
    courseService.getTees(courseId)
      .then((data: CourseTee[]) => {
        if (cancelled) return;
        setTees((data || []).filter((tee) => tee.active));
        setTeeId('');
        setTeeMasculinoId('');
        setTeeFemeninoId('');
      })
      .catch((err: any) => {
        if (!cancelled) setError(err.response?.data?.message || 'Error al cargar las salidas del club');
      })
      .finally(() => {
        if (!cancelled) setTeesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen, courseId]);

  const selectedCourse = courses.find((course) => course.id === courseId);
  const courseSupports18 = !selectedCourse || selectedCourse.cantidadHoyos !== 9;
  const activeHoles: 9 | 18 = courseSupports18 ? cantidadHoyos : 9;

  const maleTees = tees.filter((tee) => tee.genero !== 'F');
  const femaleTees = tees.filter((tee) => tee.genero === 'F');

  const scopePlayers = useMemo(() => {
    if (playerScope === 'club' && courseId !== '') {
      return players.filter((player) => player.courseId === courseId);
    }
    return players;
  }, [playerScope, courseId, players]);

  const filteredPlayers = useMemo(() => {
    if (!searchQuery.trim()) return scopePlayers;
    const q = searchQuery.toLowerCase().trim();
    return scopePlayers.filter((player) => {
      const haystack = [`${player.nombre} ${player.apellido}`, player.matricula, player.clubOrigen ?? '']
        .join(' ')
        .toLowerCase();
      return haystack.includes(q);
    });
  }, [scopePlayers, searchQuery]);

  const selectedList = players.filter((player) => selectedPlayers.has(player.id));
  const needsMaleTee = selectedList.some((player) => player.sexo !== 'F');
  const needsFemaleTee = selectedList.some((player) => player.sexo === 'F');

  const togglePlayer = (playerId: number) => {
    const next = new Set(selectedPlayers);
    if (next.has(playerId)) next.delete(playerId);
    else next.add(playerId);
    setSelectedPlayers(next);
  };

  const toggleAll = () => {
    const ids = filteredPlayers.map((player) => player.id);
    const allSelected = ids.length > 0 && ids.every((id) => selectedPlayers.has(id));
    const next = new Set(selectedPlayers);
    ids.forEach((id) => (allSelected ? next.delete(id) : next.add(id)));
    setSelectedPlayers(next);
  };

  const allFilteredSelected = filteredPlayers.length > 0 && filteredPlayers.every((player) => selectedPlayers.has(player.id));

  const handlePrint = async () => {
    if (courseId === '') {
      setError('Debe seleccionar un club');
      return;
    }
    if (mode === 'VACIAS') {
      if (teeId === '') {
        setError('Debe seleccionar el tee de salida');
        return;
      }
      if (cantidad < 1 || cantidad > 150) {
        setError('La cantidad de tarjetas debe estar entre 1 y 150');
        return;
      }
    } else {
      if (selectedPlayers.size === 0) {
        setError('Debe seleccionar al menos un jugador');
        return;
      }
      if (needsMaleTee && teeMasculinoId === '') {
        setError('Debe seleccionar el tee de caballeros');
        return;
      }
      if (needsFemaleTee && teeFemeninoId === '') {
        setError('Debe seleccionar el tee de damas');
        return;
      }
    }

    const courseName = selectedCourse?.nombre || user?.courseName || 'club';
    const filename = mode === 'VACIAS'
      ? `tarjetas_en_blanco_${fileSlug(courseName)}.pdf`
      : `tarjetas_${fileSlug(courseName)}.pdf`;

    try {
      setPrinting(true);
      setError('');
      await scorecardService.printClubScorecards(
        mode === 'VACIAS'
          ? { courseId, cantidadHoyos: activeHoles, teeId: Number(teeId), cantidad }
          : {
              courseId,
              cantidadHoyos: activeHoles,
              teeMasculinoId: teeMasculinoId === '' ? undefined : Number(teeMasculinoId),
              teeFemeninoId: teeFemeninoId === '' ? undefined : Number(teeFemeninoId),
              playerIds: Array.from(selectedPlayers),
            },
        filename
      );
      onClose();
    } catch (err: any) {
      setError(errorMessage(err, 'Error al generar el PDF de tarjetas'));
    } finally {
      setPrinting(false);
    }
  };

  const teeLabel = (tee: CourseTee) => (tee.grupo ? `${tee.nombre} (${tee.grupo})` : tee.nombre);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Imprimir Tarjetas"
      size="large"
      footer={
        <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
          <button type="button" onClick={onClose} className="btn btn-cancel" disabled={printing}>
            Cancelar
          </button>
          <button type="button" onClick={handlePrint} className="btn btn-primary" disabled={printing || teesLoading}>
            {printing
              ? 'Generando PDF...'
              : mode === 'VACIAS'
                ? `Imprimir ${cantidad} tarjeta${cantidad === 1 ? '' : 's'} en blanco`
                : `Imprimir ${selectedPlayers.size} tarjeta${selectedPlayers.size === 1 ? '' : 's'}`}
          </button>
        </div>
      }
    >
      <div className="manual-inscription">
        <div className="print-mode-selector">
          <label className={`print-mode-option ${mode === 'JUGADORES' ? 'active' : ''}`}>
            <input type="radio" name="club-print-mode" checked={mode === 'JUGADORES'} onChange={() => setMode('JUGADORES')} />
            <span>Jugadores</span>
          </label>
          <label className={`print-mode-option ${mode === 'VACIAS' ? 'active' : ''}`}>
            <input type="radio" name="club-print-mode" checked={mode === 'VACIAS'} onChange={() => setMode('VACIAS')} />
            <span>Tarjetas vacías</span>
          </label>
        </div>

        <div className="form-row">
          {isSuperAdmin && (
            <div className="form-group">
              <label>Club</label>
              <select value={courseId} onChange={(e) => setCourseId(e.target.value ? Number(e.target.value) : '')}>
                <option value="">Seleccionar club</option>
                {courses.map((course) => (
                  <option key={course.id} value={course.id}>{course.nombre}</option>
                ))}
              </select>
            </div>
          )}
          <div className="form-group">
            <label>Cantidad de hoyos</label>
            <select
              value={activeHoles}
              onChange={(e) => setCantidadHoyos(Number(e.target.value) === 9 ? 9 : 18)}
              disabled={!courseSupports18}
            >
              <option value={18}>18 hoyos</option>
              <option value={9}>9 hoyos</option>
            </select>
          </div>
          {mode === 'VACIAS' ? (
            <>
              <div className="form-group">
                <label>Tee de salida</label>
                <select value={teeId} onChange={(e) => setTeeId(e.target.value ? Number(e.target.value) : '')} disabled={courseId === '' || teesLoading}>
                  <option value="">{teesLoading ? 'Cargando...' : 'Seleccionar tee'}</option>
                  {tees.map((tee) => (
                    <option key={tee.id} value={tee.id}>
                      {teeLabel(tee)} ({tee.genero === 'F' ? 'Damas' : 'Caballeros'})
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Cantidad de tarjetas</label>
                <input
                  type="number"
                  min={1}
                  max={150}
                  value={cantidad}
                  onChange={(e) => setCantidad(Math.max(1, Math.min(150, Number(e.target.value) || 1)))}
                />
              </div>
            </>
          ) : (
            <>
              <div className="form-group">
                <label>Tee de caballeros</label>
                <select
                  value={teeMasculinoId}
                  onChange={(e) => setTeeMasculinoId(e.target.value ? Number(e.target.value) : '')}
                  disabled={courseId === '' || teesLoading}
                >
                  <option value="">{teesLoading ? 'Cargando...' : 'Seleccionar tee'}</option>
                  {maleTees.map((tee) => (
                    <option key={tee.id} value={tee.id}>{teeLabel(tee)}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Tee de damas</label>
                <select
                  value={teeFemeninoId}
                  onChange={(e) => setTeeFemeninoId(e.target.value ? Number(e.target.value) : '')}
                  disabled={courseId === '' || teesLoading}
                >
                  <option value="">{teesLoading ? 'Cargando...' : 'Seleccionar tee'}</option>
                  {femaleTees.map((tee) => (
                    <option key={tee.id} value={tee.id}>{teeLabel(tee)}</option>
                  ))}
                </select>
              </div>
            </>
          )}
        </div>

        {error && <div className="error-message">{error}</div>}

        {mode === 'VACIAS' ? (
          <p className="text-sm text-slate-500">
            Las tarjetas salen con el campo, el par, el handicap y las distancias del tee. El jugador, la matrícula, los handicaps, la fecha y la categoría quedan en blanco para completar a mano.
          </p>
        ) : (
          <>
            <div className="print-mode-selector">
              <label className={`print-mode-option ${playerScope === 'club' ? 'active' : ''}`}>
                <input type="radio" name="player-scope" checked={playerScope === 'club'} onChange={() => setPlayerScope('club')} />
                <span>{isSuperAdmin ? 'Jugadores del club' : 'Jugadores de mi club'}</span>
              </label>
              <label className={`print-mode-option ${playerScope === 'all' ? 'active' : ''}`}>
                <input type="radio" name="player-scope" checked={playerScope === 'all'} onChange={() => setPlayerScope('all')} />
                <span>Todos los jugadores</span>
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
                <p><strong>Jugadores:</strong> {scopePlayers.length}</p>
                <p><strong>Seleccionados:</strong> {selectedPlayers.size}</p>
              </div>
            </div>

            {courseId === '' ? (
              <div className="empty-state"><p>Seleccione un club para ver los jugadores</p></div>
            ) : filteredPlayers.length === 0 ? (
              <div className="empty-state">
                <p>No hay jugadores para mostrar</p>
                {searchQuery && <p>Intenta con un término de búsqueda diferente</p>}
              </div>
            ) : (
              <>
                <div className="select-all-row">
                  <label>
                    <input type="checkbox" checked={allFilteredSelected} onChange={toggleAll} />
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
                          <div className="player-name truncated-label" title={`${player.nombre} ${player.apellido}`}>
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

export default PrintClubScorecardsModal;
