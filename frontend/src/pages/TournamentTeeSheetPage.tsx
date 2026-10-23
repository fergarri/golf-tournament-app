import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  CollisionDetection,
  DndContext,
  DragEndEvent,
  DragOverlay,
  DragStartEvent,
  PointerSensor,
  pointerWithin,
  useDndContext,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import Modal from '../components/Modal';
import { teeSheetService } from '../services/teeSheetService';
import { TeeSheetGroup, TeeSheetPlayer, TournamentTeeSheet } from '../types';
import { formatDateSafe } from '../utils/dateUtils';
import {
  PlayerOrigin,
  anyPlayerPlaced,
  emptyGroup,
  fillGroups,
  movePlayer,
  moveRow,
  shufflePlayers,
  sortPlayersByHandicap,
  unassignedPlayers,
} from '../utils/teeSheetLayout';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';
import './TournamentTeeSheetPage.css';

type PendingAction = 'confirm' | 'random' | 'hcp';
type DragPreview =
  | { kind: 'player'; name: string }
  | { kind: 'row'; names: (string | null)[] };

type PlayerDragData = { type: 'player'; playerId: number; from: PlayerOrigin };
type RowDragData = { type: 'row'; rowIndex: number };

const collisionDetection: CollisionDetection = (args) => {
  const hits = pointerWithin(args);
  const activeType = args.active.data.current?.type;
  return hits.filter((hit) => {
    const container = args.droppableContainers.find((item) => item.id === hit.id);
    const type = container?.data.current?.type;
    if (activeType === 'row') return type === 'row';
    if (activeType === 'player') return type === 'cell' || type === 'unassigned';
    return false;
  });
};

const errorMessage = (err: unknown, fallback: string) => {
  if (typeof err === 'object' && err && 'response' in err) {
    const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message;
    if (message) return message;
  }
  return fallback;
};

const PlayerChip = ({ player, from }: { player: TeeSheetPlayer; from: PlayerOrigin }) => {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `player-${player.playerId}`,
    data: { type: 'player', playerId: player.playerId, from } satisfies PlayerDragData,
  });
  const style: CSSProperties = { opacity: isDragging ? 0.35 : 1 };
  return (
    <div ref={setNodeRef} {...listeners} {...attributes} className="tee-sheet-player-chip" style={style} title={player.playerName}>
      {player.playerName}
    </div>
  );
};

const TeeSheetCell = ({
  rowIndex,
  slot,
  player,
  editable,
  onClear,
}: {
  rowIndex: number;
  slot: number;
  player: TeeSheetPlayer | undefined;
  editable: boolean;
  onClear: () => void;
}) => {
  const { active } = useDndContext();
  const { setNodeRef, isOver } = useDroppable({
    id: `cell-${rowIndex}-${slot}`,
    data: { type: 'cell', rowIndex, slot },
    disabled: !editable,
  });
  const highlight = isOver && active?.data.current?.type === 'player';
  return (
    <div ref={setNodeRef} className={`tee-sheet-cell ${highlight ? 'is-drop-over' : ''}`}>
      {player ? (
        editable ? (
          <PlayerChip player={player} from={{ kind: 'cell', rowIndex, slot }} />
        ) : (
          <span className="tee-sheet-player-static" title={player.playerName}>{player.playerName}</span>
        )
      ) : (
        editable && <span className="tee-sheet-cell-placeholder">Soltar jugador</span>
      )}
      {player && editable && (
        <button
          type="button"
          className="tee-sheet-cell-remove"
          title="Quitar jugador"
          onPointerDown={(event) => event.stopPropagation()}
          onClick={onClear}
        >
          ×
        </button>
      )}
    </div>
  );
};

const TeeSheetRow = ({
  rowIndex,
  group,
  playersById,
  editable,
  holeCount,
  onHoleChange,
  onTimeChange,
  rowCount,
  onRemove,
  onClearPlayer,
  onMoveUp,
  onMoveDown,
}: {
  rowIndex: number;
  group: TeeSheetGroup;
  playersById: Map<number, TeeSheetPlayer>;
  editable: boolean;
  holeCount: number;
  onHoleChange: (value: number | null) => void;
  onTimeChange: (value: string | null) => void;
  rowCount: number;
  onRemove: () => void;
  onClearPlayer: (slot: number) => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
}) => {
  const { active } = useDndContext();
  const { setNodeRef: setDragRef, attributes, listeners, isDragging } = useDraggable({
    id: `row-drag-${rowIndex}`,
    data: { type: 'row', rowIndex } satisfies RowDragData,
    disabled: !editable,
  });
  const { setNodeRef: setDropRef, isOver } = useDroppable({
    id: `row-drop-${rowIndex}`,
    data: { type: 'row', rowIndex },
    disabled: !editable,
  });
  const highlight = isOver && active?.data.current?.type === 'row';
  const slots = [group.player1Id, group.player2Id, group.player3Id, group.player4Id];

  return (
    <div
      ref={(node) => {
        setDragRef(node);
        setDropRef(node);
      }}
      className={`tee-sheet-row ${isDragging ? 'is-dragging' : ''} ${highlight ? 'is-drop-over' : ''}`}
    >
      <div className="tee-sheet-meta">
        <div className="tee-sheet-meta-main">
          {editable && (
            <button
              type="button"
              className="tee-sheet-handle"
              title="Arrastrar fila"
              {...listeners}
              {...attributes}
            >
              ⋮⋮
            </button>
          )}
          {editable && (
            <div className="tee-sheet-move">
              <button
                type="button"
                className="tee-sheet-move-btn"
                title="Subir fila"
                aria-label="Subir fila"
                disabled={rowIndex === 0}
                onClick={onMoveUp}
              >
                ↑
              </button>
              <button
                type="button"
                className="tee-sheet-move-btn"
                title="Bajar fila"
                aria-label="Bajar fila"
                disabled={rowIndex >= rowCount - 1}
                onClick={onMoveDown}
              >
                ↓
              </button>
            </div>
          )}
          <div className="tee-sheet-fields">
            <select
              className="tee-sheet-select"
              aria-label="Hoyo"
              value={group.holeNumber ?? ''}
              disabled={!editable}
              onChange={(event) => onHoleChange(event.target.value === '' ? null : Number(event.target.value))}
            >
              <option value="">Hoyo</option>
              {Array.from({ length: holeCount }, (_, index) => index + 1).map((hole) => (
                <option key={hole} value={hole}>
                  Hoyo {hole}
                </option>
              ))}
            </select>
            <input
              type="time"
              className="tee-sheet-time"
              aria-label="Horario"
              value={group.teeTime ?? ''}
              disabled={!editable}
              onPointerDown={(event) => {
                // Vacío, el selector nativo abre en la hora actual. Se fija 00:00 antes de que se abra.
                if (!event.currentTarget.value) {
                  event.currentTarget.value = '00:00';
                }
              }}
              onChange={(event) => onTimeChange(event.target.value === '' ? null : event.target.value.slice(0, 5))}
              onBlur={(event) => {
                if (!group.teeTime) {
                  event.currentTarget.value = '';
                }
              }}
            />
          </div>
        </div>
        {editable && (
          <button type="button" className="tee-sheet-remove" title="Quitar fila" onClick={onRemove}>
            ×
          </button>
        )}
      </div>
      {slots.map((playerId, slot) => (
        <TeeSheetCell
          key={slot}
          rowIndex={rowIndex}
          slot={slot}
          player={playerId != null ? playersById.get(playerId) : undefined}
          editable={editable}
          onClear={() => onClearPlayer(slot)}
        />
      ))}
    </div>
  );
};

const UnassignedPanel = ({
  players,
  editable,
}: {
  players: TeeSheetPlayer[];
  editable: boolean;
}) => {
  const { active } = useDndContext();
  const { setNodeRef, isOver } = useDroppable({
    id: 'drop-unassigned',
    data: { type: 'unassigned' },
    disabled: !editable,
  });
  const highlight = isOver && active?.data.current?.type === 'player';
  return (
    <div ref={setNodeRef} className={`tee-sheet-unassigned ${highlight ? 'is-drop-over' : ''}`}>
      <div className="tee-sheet-unassigned-title">Sin ubicar ({players.length})</div>
      {players.length === 0 ? (
        <div className="tee-sheet-unassigned-empty">
          {editable ? 'Todos los jugadores están ubicados' : 'No hay jugadores sin ubicar'}
        </div>
      ) : (
        players.map((player) =>
          editable ? (
            <PlayerChip key={player.playerId} player={player} from={{ kind: 'unassigned' }} />
          ) : (
            <span key={player.playerId} className="tee-sheet-player-static" title={player.playerName}>
              {player.playerName}
            </span>
          )
        )
      )}
    </div>
  );
};

const TournamentTeeSheetPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const tournamentId = Number(id);

  const [sheet, setSheet] = useState<TournamentTeeSheet | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [showCopyModal, setShowCopyModal] = useState(false);
  const [dragPreview, setDragPreview] = useState<DragPreview | null>(null);
  const savingRef = useRef(false);

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }));

  const load = useCallback(async (initial = false) => {
    if (!Number.isFinite(tournamentId)) {
      setError('Torneo inválido');
      setLoading(false);
      return;
    }
    if (initial) setLoading(true);
    try {
      const data = await teeSheetService.get(tournamentId);
      setSheet(data);
      setError('');
    } catch (err) {
      setError(errorMessage(err, 'No se pudieron cargar las salidas'));
    } finally {
      if (initial) setLoading(false);
    }
  }, [tournamentId]);

  useEffect(() => {
    void load(true);
  }, [load]);

  const playersById = useMemo(() => {
    const map = new Map<number, TeeSheetPlayer>();
    sheet?.players.forEach((player) => map.set(player.playerId, player));
    return map;
  }, [sheet?.players]);

  const unassigned = sheet ? unassignedPlayers(sheet.players, sheet.groups) : [];
  const editable = Boolean(sheet && !sheet.locked && !saving);

  const persist = async (groups: TeeSheetGroup[]) => {
    if (!sheet || sheet.locked || savingRef.current) return;
    savingRef.current = true;
    setSheet({ ...sheet, groups });
    setSaving(true);
    try {
      const updated = await teeSheetService.save(sheet.tournamentId, groups);
      setSheet(updated);
      setError('');
    } catch (err) {
      setError(errorMessage(err, 'No se pudieron guardar las salidas'));
      try {
        const fresh = await teeSheetService.get(sheet.tournamentId);
        setSheet(fresh);
      } catch {
        // Si el reintento falla, queda el mensaje de error.
      }
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const applyFill = async (mode: 'random' | 'hcp') => {
    if (!sheet || sheet.locked) return;
    const ordered = mode === 'hcp' ? sortPlayersByHandicap(sheet.players) : shufflePlayers(sheet.players);
    await persist(fillGroups(sheet.groups, ordered.map((player) => player.playerId)));
  };

  const confirmSheet = async () => {
    if (!sheet || savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const updated = await teeSheetService.confirm(sheet.tournamentId);
      setSheet(updated);
      setError('');
    } catch (err) {
      setError(errorMessage(err, 'No se pudieron confirmar las salidas'));
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const unlockSheet = async () => {
    if (!sheet || savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const updated = await teeSheetService.edit(sheet.tournamentId);
      setSheet(updated);
      setError('');
    } catch (err) {
      setError(errorMessage(err, 'No se pudieron habilitar las salidas para edición'));
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const copyPublicLink = async () => {
    if (!sheet?.published) return;
    const link = `${window.location.origin}/salidas/${sheet.codigo}`;
    try {
      await navigator.clipboard.writeText(link);
      setShowCopyModal(true);
    } catch {
      setError('No se pudo copiar el link');
    }
  };

  const handleDragStart = (event: DragStartEvent) => {
    const data = event.active.data.current as PlayerDragData | RowDragData | undefined;
    if (!sheet || !data) return;
    if (data.type === 'player') {
      const player = playersById.get(data.playerId);
      setDragPreview({ kind: 'player', name: player?.playerName ?? '' });
      return;
    }
    const group = sheet.groups[data.rowIndex];
    if (!group) return;
    const names = [group.player1Id, group.player2Id, group.player3Id, group.player4Id].map(
      (playerId) => (playerId != null ? playersById.get(playerId)?.playerName ?? null : null),
    );
    setDragPreview({ kind: 'row', names });
  };

  const handleDragEnd = (event: DragEndEvent) => {
    setDragPreview(null);
    if (!sheet || !editable) return;
    const { active, over } = event;
    if (!over) return;
    const activeData = active.data.current as PlayerDragData | RowDragData | undefined;
    const overData = over.data.current as
      | { type: 'cell'; rowIndex: number; slot: number }
      | { type: 'unassigned' }
      | { type: 'row'; rowIndex: number }
      | undefined;
    if (!activeData || !overData) return;

    if (activeData.type === 'row' && overData.type === 'row') {
      const groups = moveRow(sheet.groups, activeData.rowIndex, overData.rowIndex);
      if (groups !== sheet.groups) void persist(groups);
      return;
    }
    if (activeData.type === 'player' && (overData.type === 'cell' || overData.type === 'unassigned')) {
      const target: PlayerOrigin =
        overData.type === 'unassigned'
          ? { kind: 'unassigned' }
          : { kind: 'cell', rowIndex: overData.rowIndex, slot: overData.slot };
      const groups = movePlayer(sheet.groups, activeData.playerId, activeData.from, target);
      if (groups !== sheet.groups) void persist(groups);
    }
  };

  const requestFill = (mode: 'random' | 'hcp') => {
    if (!sheet || !editable || sheet.players.length === 0) return;
    if (anyPlayerPlaced(sheet.groups)) {
      setPending(mode);
      return;
    }
    void applyFill(mode);
  };

  if (loading) return <div className="loading">Cargando salidas...</div>;
  if (!sheet) return <div className="error-message">{error || 'No se encontraron las salidas'}</div>;

  const confirmDisabled = saving || unassigned.length > 0 || sheet.players.length === 0;
  const confirmTitle = sheet.players.length === 0
    ? 'No hay jugadores inscriptos'
    : unassigned.length > 0
      ? 'Ubicá a todos los jugadores antes de confirmar'
      : undefined;

  const pendingCopy = pending === 'confirm'
    ? {
        title: 'Confirmar salidas',
        message: '¿Desea confirmar las salidas?',
        confirmText: 'Confirmar',
      }
    : {
        title: pending === 'hcp' ? 'Salidas por HCP' : 'Salidas Aleatorias',
        message: '¿Reubicar a los jugadores? Se conservan hoyo y horario de cada fila.',
        confirmText: 'Reubicar',
      };

  const hint = sheet.locked
    ? 'Salidas confirmadas. La grilla está bloqueada.'
    : sheet.published
      ? 'En edición. La vista pública sigue mostrando la última grilla confirmada.'
      : 'Borrador. Confirmá las salidas para publicar el link.';

  return (
    <div className="tee-sheet-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button type="button" onClick={() => navigate('/tournaments')} className="btn-back">
            ← Volver a Torneos
          </button>
          <button type="button" onClick={() => void load(false)} className="btn-refresh" disabled={saving}>
            ⟳ Actualizar
          </button>
        </div>
        <div className="tournament-info">
          <h1>Salidas</h1>
          <div className="tournament-details">
            <span className="detail-item">
              <strong>Torneo:</strong> {sheet.tournamentName}
            </span>
            <span className="detail-item">
              <strong>Campo:</strong> {sheet.courseName}
            </span>
            <span className="detail-item">
              <strong>Fecha:</strong> {formatDateSafe(sheet.fechaInicio)}
            </span>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="tee-sheet-toolbar">
        <div className="tee-sheet-toolbar-group">
          <button
            type="button"
            className="btn-compact btn-compact-secondary"
            disabled={!editable || sheet.players.length === 0}
            onClick={() => requestFill('random')}
          >
            Salidas Aleatorias
          </button>
          <button
            type="button"
            className="btn-compact btn-compact-secondary"
            disabled={!editable || sheet.players.length === 0}
            onClick={() => requestFill('hcp')}
          >
            Salidas por HCP
          </button>
        </div>
        <div className="tee-sheet-toolbar-group">
          {sheet.locked ? (
            <button
              type="button"
              className="btn-compact btn-compact-primary"
              disabled={saving}
              onClick={() => void unlockSheet()}
            >
              Editar Salidas
            </button>
          ) : (
            <button
              type="button"
              className="btn-compact btn-compact-primary"
              disabled={confirmDisabled}
              title={confirmTitle}
              onClick={() => setPending('confirm')}
            >
              Confirmar Salidas
            </button>
          )}
          <button
            type="button"
            className="btn-compact btn-compact-secondary"
            disabled={!sheet.published || saving}
            title={sheet.published ? undefined : 'Confirmá las salidas para copiar el link público'}
            onClick={() => void copyPublicLink()}
          >
            Copiar Link Público
          </button>
        </div>
      </div>
      <p className="tee-sheet-hint">
        {hint}
        {saving ? ' Guardando…' : ''}
      </p>

      <DndContext
        sensors={sensors}
        collisionDetection={collisionDetection}
        onDragStart={handleDragStart}
        onDragCancel={() => setDragPreview(null)}
        onDragEnd={handleDragEnd}
      >
        <div className="tee-sheet-layout">
          <UnassignedPanel players={unassigned} editable={editable} />
          <div className="tee-sheet-grid-wrap">
            <div className={`tee-sheet-grid ${sheet.locked ? 'is-locked' : ''}`}>
              <div className="tee-sheet-row tee-sheet-header">
                <div />
                <div>Jugador 1</div>
                <div>Jugador 2</div>
                <div>Jugador 3</div>
                <div>Jugador 4</div>
              </div>
              {sheet.groups.map((group, rowIndex) => (
                <TeeSheetRow
                  key={rowIndex}
                  rowIndex={rowIndex}
                  group={group}
                  playersById={playersById}
                  editable={editable}
                  holeCount={sheet.cantidadHoyosJuego}
                  rowCount={sheet.groups.length}
                  onHoleChange={(holeNumber) =>
                    void persist(sheet.groups.map((item, index) => (index === rowIndex ? { ...item, holeNumber } : item)))
                  }
                  onTimeChange={(teeTime) =>
                    void persist(sheet.groups.map((item, index) => (index === rowIndex ? { ...item, teeTime } : item)))
                  }
                  onRemove={() => void persist(sheet.groups.filter((_, index) => index !== rowIndex))}
                  onMoveUp={() => {
                    const groups = moveRow(sheet.groups, rowIndex, rowIndex - 1);
                    if (groups !== sheet.groups) void persist(groups);
                  }}
                  onMoveDown={() => {
                    const groups = moveRow(sheet.groups, rowIndex, rowIndex + 1);
                    if (groups !== sheet.groups) void persist(groups);
                  }}
                  onClearPlayer={(slot) => {
                    const playerId = [group.player1Id, group.player2Id, group.player3Id, group.player4Id][slot];
                    if (playerId == null) return;
                    const groups = movePlayer(
                      sheet.groups,
                      playerId,
                      { kind: 'cell', rowIndex, slot },
                      { kind: 'unassigned' },
                    );
                    if (groups !== sheet.groups) void persist(groups);
                  }}
                />
              ))}
              {editable && (
                <div className="tee-sheet-row">
                  <button
                    type="button"
                    className="tee-sheet-add-btn"
                    onClick={() => void persist([...sheet.groups, emptyGroup()])}
                  >
                    +
                  </button>
                  <div className="tee-sheet-cell" />
                  <div className="tee-sheet-cell" />
                  <div className="tee-sheet-cell" />
                  <div className="tee-sheet-cell" />
                </div>
              )}
            </div>
          </div>
        </div>
        <DragOverlay>
          {dragPreview?.kind === 'player' && (
            <div className="tee-sheet-player-chip tee-sheet-overlay-chip">{dragPreview.name}</div>
          )}
          {dragPreview?.kind === 'row' && (
            <div className="tee-sheet-overlay-row">
              {dragPreview.names.map((name, index) => (
                <span key={index}>{name ?? ''}</span>
              ))}
            </div>
          )}
        </DragOverlay>
      </DndContext>

      <Modal
        isOpen={pending !== null}
        onClose={() => setPending(null)}
        onConfirm={() => {
          if (pending === 'confirm') void confirmSheet();
          if (pending === 'random' || pending === 'hcp') void applyFill(pending);
        }}
        title={pendingCopy.title}
        message={pendingCopy.message}
        type="confirm"
        confirmText={pendingCopy.confirmText}
        cancelText="Cancelar"
      />

      <Modal
        isOpen={showCopyModal}
        onClose={() => setShowCopyModal(false)}
        title="Link copiado"
        message="El link público de salidas fue copiado al portapapeles."
        type="success"
      />
    </div>
  );
};

export default TournamentTeeSheetPage;
