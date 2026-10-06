import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  DndContext,
  DragEndEvent,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import { TransformWrapper, TransformComponent } from 'react-zoom-pan-pinch';
import Modal from '../components/Modal';
import Tabs, { Tab } from '../components/Tabs';
import {
  tournamentAdminPlayoffBracketService,
  SlotAssignment,
} from '../services/tournamentAdminPlayoffBracketService';
import { tournamentAdminPlayoffMatchService } from '../services/tournamentAdminPlayoffMatchService';
import { tournamentAdminService } from '../services/tournamentAdminService';
import { courseService } from '../services/courseService';
import {
  CourseTee,
  PlayoffMatchSummary,
  PlayoffRoundSession,
  PlayoffScoreType,
  StartPlayoffRoundRequest,
  TournamentAdminPlayoffBracket,
  TournamentAdminPlayoffBrackets,
  TournamentAdminPlayoffBracketSlot,
} from '../types';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';
import './TournamentAdminBracketsPage.css';

type DragSourceData =
  | { type: 'slot'; slotId: number; playerId: number }
  | { type: 'unassigned'; playerId: number };

type DragTargetData = { type: 'slot'; slotId: number } | { type: 'unassigned' };

type PendingAction = { type: 'confirm' | 'revert' | 'reset'; bracketId: number };

// El "step" de zoom-pan-pinch se suma directo a la escala (1 = 100%), así que
// 0.05 equivale a variar el zoom de a 5 unidades por click en +/-.
const ZOOM_STEP = 0.05;

/**
 * Orden de los cruces de primera ronda para cabezas de serie.
 * Devuelve, de arriba hacia abajo, la mejor semilla de cada partido.
 * El rival es (tamaño + 1 − esa semilla): 1 vs último, 2 vs anteúltimo, etc.
 * El 1 queda arriba y el 2 abajo, así solo se pueden cruzar en la final.
 * 8 jugadores: 1, 4, 3, 2. 16: 1, 8, 5, 4, 3, 6, 7, 2.
 */
const seededMatchFavorites = (size: number): number[] => {
  if (size <= 2) return [1];
  const previous = seededMatchFavorites(size / 2);
  const favorites: number[] = [];
  for (let i = 0; i < previous.length; i += 2) {
    const top = previous[i];
    favorites.push(top, size / 2 + 1 - top);
    const bottom = previous[i + 1];
    if (bottom !== undefined) {
      favorites.push(size / 2 + 1 - bottom, bottom);
    }
  }
  return favorites;
};

const formatPlayerLabel = (name: string, handicapIndex: number | null | undefined) =>
  handicapIndex !== null && handicapIndex !== undefined ? name : name;

const DraggablePlayerChip = ({
  dragId,
  data,
  playerName,
  handicapIndex,
  scale = 1,
}: {
  dragId: string;
  data: DragSourceData;
  playerName: string;
  handicapIndex: number | null;
  scale?: number;
}) => {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: dragId,
    data,
  });
  // El chip vive dentro de un contenedor con zoom (CSS transform: scale);
  // dividimos el delta del puntero por el zoom actual para que el arrastre
  // siga al cursor 1:1 en pantalla, sin importar el nivel de zoom.
  const style: React.CSSProperties = {
    transform: transform ? `translate3d(${transform.x / scale}px, ${transform.y / scale}px, 0)` : undefined,
    opacity: isDragging ? 0.4 : 1,
    zIndex: isDragging ? 50 : undefined,
  };
  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      className="bracket-player-chip bracket-no-pan"
      style={style}
    >
      {formatPlayerLabel(playerName, handicapIndex)}
    </div>
  );
};

const DroppableArea = ({
  dropId,
  data,
  className,
  children,
}: {
  dropId: string;
  data: DragTargetData;
  className: string;
  children: React.ReactNode;
}) => {
  const { setNodeRef, isOver } = useDroppable({ id: dropId, data });
  return (
    <div ref={setNodeRef} className={`${className} ${isOver ? 'is-drop-over' : ''}`}>
      {children}
    </div>
  );
};

const TournamentAdminBracketsPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const tournamentAdminId = Number(id);

  const [data, setData] = useState<TournamentAdminPlayoffBrackets | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<PlayoffScoreType>('HCP');
  const [generating, setGenerating] = useState(false);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [showCopyLinkModal, setShowCopyLinkModal] = useState(false);
  const [zoomScale, setZoomScale] = useState(1);

  // Match Play: rondas de partidos iniciadas, tees disponibles y modales asociados
  const [roundSessionsByBracket, setRoundSessionsByBracket] = useState<Record<number, PlayoffRoundSession[]>>({});
  const [tees, setTees] = useState<CourseTee[]>([]);
  const [startRoundModal, setStartRoundModal] = useState<{ bracketId: number; roundNumber: number } | null>(null);
  const [startRoundForm, setStartRoundForm] = useState({
    teeMasculinoId: '',
    teeFemeninoId: '',
    cantidadHoyosJuego: '18',
  });
  const [startingRound, setStartingRound] = useState(false);
  const [newRoundCode, setNewRoundCode] = useState<string | null>(null);
  const [resetRoundTarget, setResetRoundTarget] = useState<{ bracketId: number; roundNumber: number } | null>(null);
  const [winnerOverrideWarning, setWinnerOverrideWarning] = useState<{ bracketId: number; slotId: number } | null>(
    null
  );

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }));

  const bracket = data?.brackets.find((b) => b.scoreType === activeTab) ?? null;
  const roundSessions = bracket ? roundSessionsByBracket[bracket.bracketId] ?? [] : [];

  useEffect(() => {
    if (!Number.isFinite(tournamentAdminId)) {
      setError('Parámetros inválidos');
      setLoading(false);
      return;
    }
    loadData();
  }, [tournamentAdminId]);

  useEffect(() => {
    if (!Number.isFinite(tournamentAdminId)) return;
    (async () => {
      try {
        const admin = await tournamentAdminService.getById(tournamentAdminId);
        if (admin.courseId) {
          const teesData = await courseService.getTees(admin.courseId);
          setTees((teesData as CourseTee[]).filter((t) => t.active));
        }
      } catch {
        // No bloquea la vista de llaves si no se pueden cargar los tees.
      }
    })();
  }, [tournamentAdminId]);

  useEffect(() => {
    setZoomScale(1);
  }, [activeTab]);

  useEffect(() => {
    if (!bracket) return;
    const bracketId = bracket.bracketId;
    void loadRoundSessions(bracketId);
    const interval = window.setInterval(() => {
      void loadRoundSessions(bracketId);
    }, 30000);
    return () => window.clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bracket?.bracketId, bracket?.status]);

  const loadRoundSessions = async (bracketId: number) => {
    try {
      const sessions = await tournamentAdminPlayoffMatchService.getRoundSessions(tournamentAdminId, bracketId);
      setRoundSessionsByBracket((prev) => ({ ...prev, [bracketId]: sessions }));
    } catch (err) {
      console.error('Error cargando rondas de partidos', err);
    }
  };

  const loadData = async () => {
    try {
      setLoading(true);
      const result = await tournamentAdminPlayoffBracketService.get(tournamentAdminId);
      setData(result);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando las llaves de Playoff');
    } finally {
      setLoading(false);
    }
  };

  const replaceData = (result: TournamentAdminPlayoffBrackets) => {
    setData(result);
    setError('');
  };

  const handleGenerate = async (scoreType: PlayoffScoreType) => {
    try {
      setGenerating(true);
      const result = await tournamentAdminPlayoffBracketService.generate(tournamentAdminId, scoreType);
      replaceData(result);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error generando la llave');
    } finally {
      setGenerating(false);
    }
  };

  const persistSlots = async (bracketId: number, assignments: SlotAssignment[]) => {
    try {
      const result = await tournamentAdminPlayoffBracketService.saveSlots(tournamentAdminId, bracketId, assignments);
      replaceData(result);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error guardando la asignación de jugadores');
    }
  };

  const handleMarkWinner = async (bracketId: number, slotId: number) => {
    try {
      const result = await tournamentAdminPlayoffBracketService.markWinner(tournamentAdminId, bracketId, slotId);
      replaceData(result);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error marcando el Vencedor');
    }
  };

  const handleUndoWinner = async (bracketId: number, slotId: number) => {
    try {
      const result = await tournamentAdminPlayoffBracketService.undoWinner(tournamentAdminId, bracketId, slotId);
      replaceData(result);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error deshaciendo el Vencedor');
    }
  };

  const handleConfirmAction = async () => {
    if (!pendingAction) return;
    const { type, bracketId } = pendingAction;
    try {
      let result: TournamentAdminPlayoffBrackets;
      if (type === 'confirm') {
        result = await tournamentAdminPlayoffBracketService.confirm(tournamentAdminId, bracketId);
      } else if (type === 'revert') {
        result = await tournamentAdminPlayoffBracketService.revert(tournamentAdminId, bracketId);
      } else {
        result = await tournamentAdminPlayoffBracketService.reset(tournamentAdminId, bracketId);
      }
      replaceData(result);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error procesando la acción sobre la llave');
    } finally {
      setPendingAction(null);
    }
  };

  const handleShuffle = (bracket: TournamentAdminPlayoffBracket) => {
    const round1 = bracket.rounds.find((r) => r.roundNumber === 1);
    if (!round1) return;

    const assignedPlayerIds = round1.slots
      .filter((s) => s.playerId !== null)
      .map((s) => s.playerId as number);
    const allPlayerIds = [...assignedPlayerIds, ...bracket.unassignedPlayers.map((p) => p.playerId)];

    const shuffled = [...allPlayerIds];
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }

    const assignments: SlotAssignment[] = round1.slots.map((slot, idx) => ({
      slotId: slot.slotId,
      playerId: shuffled[idx] ?? null,
    }));
    void persistSlots(bracket.bracketId, assignments);
  };

  const handleSeedByRanking = (bracket: TournamentAdminPlayoffBracket) => {
    const round1 = bracket.rounds.find((r) => r.roundNumber === 1);
    if (!round1) return;

    // El campo "seed"/"playerSeed" es la posición del jugador en la Tabla de Play Off, que en
    // torneos con clasificación por categoría es un ranking GLOBAL y puede tener saltos entre
    // los clasificados (ej: 1,2,3...13,16,17,20). Por eso no se puede usar ese número directo
    // como cabeza de serie: hay que recalcular el orden RELATIVO (1..Q) entre los clasificados
    // de esta llave. Cada cruce sigue siendo k vs (tamaño+1−k); lo que cambia es el orden de
    // los partidos, para que el 1 quede arriba y el 2 abajo.
    const placed = round1.slots
      .filter((s) => s.playerId !== null && s.playerSeed !== null)
      .map((s) => ({ playerId: s.playerId as number, position: s.playerSeed as number }));
    const unplaced = bracket.unassignedPlayers.map((p) => ({ playerId: p.playerId, position: p.seed }));

    const orderedPlayerIds = [...placed, ...unplaced]
      .sort((a, b) => a.position - b.position)
      .map((p) => p.playerId);

    const playerIdByRelativeSeed = new Map<number, number>();
    orderedPlayerIds.forEach((playerId, idx) => playerIdByRelativeSeed.set(idx + 1, playerId));

    const size = round1.slots.length;
    const favorites = seededMatchFavorites(size);
    const playerIdBySlotIndex = new Map<number, number | null>();
    favorites.forEach((topSeed, pairIdx) => {
      const bottomSeed = size + 1 - topSeed;
      playerIdBySlotIndex.set(pairIdx * 2, playerIdByRelativeSeed.get(topSeed) ?? null);
      playerIdBySlotIndex.set(pairIdx * 2 + 1, playerIdByRelativeSeed.get(bottomSeed) ?? null);
    });

    const assignments: SlotAssignment[] = round1.slots.map((slot) => ({
      slotId: slot.slotId,
      playerId: playerIdBySlotIndex.get(slot.slotIndex) ?? null,
    }));
    void persistSlots(bracket.bracketId, assignments);
  };

  const handleDragEnd = (bracket: TournamentAdminPlayoffBracket) => (event: DragEndEvent) => {
    if (bracket.status !== 'DRAFT' || roundSessions.length > 0) return;
    const { active, over } = event;
    if (!over) return;
    const activeData = active.data.current as DragSourceData | undefined;
    const overData = over.data.current as DragTargetData | undefined;
    if (!activeData || !overData) return;

    if (activeData.type === 'slot' && overData.type === 'slot' && activeData.slotId === overData.slotId) {
      return;
    }
    if (activeData.type === 'unassigned' && overData.type === 'unassigned') {
      return;
    }

    const round1 = bracket.rounds.find((r) => r.roundNumber === 1);
    if (!round1) return;

    const map = new Map<number, number | null>(round1.slots.map((s) => [s.slotId, s.playerId]));

    if (overData.type === 'slot') {
      const destSlotId = overData.slotId;
      const destPlayerId = map.get(destSlotId) ?? null;
      if (activeData.type === 'slot') {
        map.set(activeData.slotId, destPlayerId);
      }
      map.set(destSlotId, activeData.playerId);
    } else if (activeData.type === 'slot') {
      map.set(activeData.slotId, null);
    } else {
      return;
    }

    const assignments: SlotAssignment[] = Array.from(map.entries()).map(([slotId, playerId]) => ({
      slotId,
      playerId,
    }));
    void persistSlots(bracket.bracketId, assignments);
  };

  const getPublicBracketsLink = () => `${window.location.origin}/playoff-brackets/${tournamentAdminId}`;

  const copyPublicBracketsLink = () => {
    navigator.clipboard.writeText(getPublicBracketsLink());
    setShowCopyLinkModal(true);
  };

  const getRoundLink = (code: string) => `${window.location.origin}/playoff-match/${code}`;

  const copyRoundLink = (code: string) => {
    navigator.clipboard.writeText(getRoundLink(code));
    setShowCopyLinkModal(true);
  };

  const findMatchForSlot = (slotId: number): PlayoffMatchSummary | undefined => {
    for (const session of roundSessions) {
      const match = session.matches.find((m) => m.topSlotId === slotId || m.bottomSlotId === slotId);
      if (match) return match;
    }
    return undefined;
  };

  const onClickMarkWinner = (bracketId: number, slotId: number) => {
    const match = findMatchForSlot(slotId);
    if (match && match.status === 'IN_PROGRESS') {
      setWinnerOverrideWarning({ bracketId, slotId });
      return;
    }
    void handleMarkWinner(bracketId, slotId);
  };

  const openStartRoundModal = (bracketId: number, roundNumber: number) => {
    setStartRoundForm({ teeMasculinoId: '', teeFemeninoId: '', cantidadHoyosJuego: '18' });
    setStartRoundModal({ bracketId, roundNumber });
  };

  const submitStartRound = async () => {
    if (!startRoundModal) return;
    try {
      setStartingRound(true);
      const request: StartPlayoffRoundRequest = {
        teeMasculinoId: startRoundForm.teeMasculinoId ? Number(startRoundForm.teeMasculinoId) : null,
        teeFemeninoId: startRoundForm.teeFemeninoId ? Number(startRoundForm.teeFemeninoId) : null,
        cantidadHoyosJuego: Number(startRoundForm.cantidadHoyosJuego),
      };
      const session = await tournamentAdminPlayoffMatchService.startRound(
        tournamentAdminId,
        startRoundModal.bracketId,
        startRoundModal.roundNumber,
        request
      );
      setRoundSessionsByBracket((prev) => ({
        ...prev,
        [startRoundModal.bracketId]: [
          ...(prev[startRoundModal.bracketId] ?? []).filter((s) => s.roundNumber !== session.roundNumber),
          session,
        ].sort((a, b) => a.roundNumber - b.roundNumber),
      }));
      setStartRoundModal(null);
      setNewRoundCode(session.code);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error iniciando la ronda de partidos');
    } finally {
      setStartingRound(false);
    }
  };

  const confirmResetRound = async () => {
    if (!resetRoundTarget) return;
    try {
      await tournamentAdminPlayoffMatchService.resetRound(
        tournamentAdminId,
        resetRoundTarget.bracketId,
        resetRoundTarget.roundNumber
      );
      setRoundSessionsByBracket((prev) => ({
        ...prev,
        [resetRoundTarget.bracketId]: (prev[resetRoundTarget.bracketId] ?? []).filter(
          (s) => s.roundNumber !== resetRoundTarget.roundNumber
        ),
      }));
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error reiniciando la ronda de partidos');
    } finally {
      setResetRoundTarget(null);
    }
  };

  if (loading) return <div className="loading">Cargando llaves de Playoff...</div>;
  if (!data) return <div className="error-message">No se encontraron datos</div>;

  const tabs: Tab[] = [{ id: 'HCP', label: 'Con HCP' }];
  if (data.scratchApplicable) {
    tabs.push({ id: 'SCRATCH', label: 'SCRATCH' });
  }

  const renderSlot = (
    activeBracket: TournamentAdminPlayoffBracket,
    slot: TournamentAdminPlayoffBracketSlot,
    pair: TournamentAdminPlayoffBracketSlot[],
    roundNumber: number,
    isRound1: boolean
  ) => {
    const opponent = pair.find((s) => s.slotId !== slot.slotId);
    const isLoser = Boolean(opponent?.isWinner);
    const statusClass = slot.isWinner ? 'winner' : isLoser ? 'loser' : slot.playerId === null ? 'empty' : '';
    const canShowActions = activeBracket.status === 'CONFIRMED' && slot.playerId !== null;
    const canDragPositions = activeBracket.status === 'DRAFT' && roundSessions.length === 0;
    const laterRoundLocked = roundSessions.some((session) => session.roundNumber > roundNumber);
    const laterRoundLockTitle =
      'La etapa siguiente ya tiene código. Reiniciá esa ronda para cambiar el vencedor.';

    const inner = (
      <>
        {canDragPositions && isRound1 && slot.playerId !== null ? (
          <DraggablePlayerChip
            dragId={`drag-slot-${slot.slotId}`}
            data={{ type: 'slot', slotId: slot.slotId, playerId: slot.playerId }}
            playerName={slot.playerName ?? ''}
            handicapIndex={slot.playerHandicapIndex}
            scale={zoomScale}
          />
        ) : (
          <span className="bracket-slot-player-name">
            {slot.playerName ? formatPlayerLabel(slot.playerName, slot.playerHandicapIndex) : 'Sin asignar'}
          </span>
        )}
        {canShowActions && (
          <div className="bracket-slot-actions bracket-no-pan">
            {!slot.isWinner ? (
              <button
                type="button"
                className="bracket-slot-btn btn-winner"
                disabled={laterRoundLocked}
                title={laterRoundLocked ? laterRoundLockTitle : undefined}
                onClick={() => onClickMarkWinner(activeBracket.bracketId, slot.slotId)}
              >
                Vencedor
              </button>
            ) : (
              <button
                type="button"
                className="bracket-slot-btn btn-undo"
                disabled={laterRoundLocked}
                title={laterRoundLocked ? laterRoundLockTitle : undefined}
                onClick={() => handleUndoWinner(activeBracket.bracketId, slot.slotId)}
              >
                Deshacer
              </button>
            )}
          </div>
        )}
      </>
    );

    if (canDragPositions && isRound1) {
      return (
        <DroppableArea
          key={slot.slotId}
          dropId={`drop-slot-${slot.slotId}`}
          data={{ type: 'slot', slotId: slot.slotId }}
          className={`bracket-slot-box is-droppable-round1 ${statusClass}`}
        >
          {inner}
        </DroppableArea>
      );
    }

    return (
      <div key={slot.slotId} className={`bracket-slot-box ${statusClass}`}>
        {inner}
      </div>
    );
  };

  const renderBracket = (activeBracket: TournamentAdminPlayoffBracket) => {
    const unassigned = activeBracket.unassignedPlayers;
    const championSlot = activeBracket.rounds
      .find((r) => r.roundNumber === activeBracket.rounds.length)
      ?.slots.find((s) => s.isWinner);

    return (
      <>
        {championSlot?.playerName && (
          <div className="bracket-champion-panel bracket-champion-panel-top">
            <span className="trophy">🏆</span>
            <span>Campeón: {championSlot.playerName}</span>
          </div>
        )}

        <div className="bracket-toolbar">
          <div className="bracket-toolbar-status">
            <span className={`bracket-status-badge status-${activeBracket.status.toLowerCase()}`}>
              {activeBracket.status === 'DRAFT' ? 'Armando' : 'Confirmada'}
            </span>
            {activeBracket.status === 'CONFIRMED' && (
              <span className="bracket-lock-hint">
                {roundSessions.length > 0
                  ? 'Las posiciones de las etapas con código no se pueden cambiar. Reiniciá la ronda (↺) para volver a editarlas.'
                  : 'Las posiciones quedan fijas al confirmar. Usá Revertir a edición para volver a ubicar jugadores.'}
              </span>
            )}
            {activeBracket.status === 'DRAFT' && (
              <button
                type="button"
                className="btn-compact btn-compact-secondary"
                onClick={() => handleShuffle(activeBracket)}
              >
                🎲 Sortear Llaves
              </button>
            )}
            {activeBracket.status === 'DRAFT' && (
              <button
                type="button"
                className="btn-compact btn-compact-secondary"
                onClick={() => handleSeedByRanking(activeBracket)}
                title="Ubica a los clasificados según la Tabla de Play Off. El 1° queda arriba y el 2° abajo, y solo se pueden cruzar en la final."
              >
                🌱 Cabezas de Serie
              </button>
            )}
          </div>
          <div className="bracket-toolbar-actions">
            {activeBracket.status === 'DRAFT' && (
              <button
                type="button"
                className="btn-compact btn-compact-primary"
                disabled={unassigned.length > 0}
                title={unassigned.length > 0 ? 'Ubicá a todos los clasificados antes de confirmar' : undefined}
                onClick={() => setPendingAction({ type: 'confirm', bracketId: activeBracket.bracketId })}
              >
                Confirmar Llave
              </button>
            )}
            {activeBracket.canRevertToDraft && (
              <button
                type="button"
                className="btn-compact btn-compact-secondary"
                onClick={() => setPendingAction({ type: 'revert', bracketId: activeBracket.bracketId })}
              >
                Revertir a edición
              </button>
            )}
            <button
              type="button"
              className="btn-refresh"
              onClick={() => setPendingAction({ type: 'reset', bracketId: activeBracket.bracketId })}
            >
              Reiniciar llave
            </button>
          </div>
        </div>

        <DndContext sensors={sensors} onDragEnd={handleDragEnd(activeBracket)}>
          <div className="bracket-layout">
            <DroppableArea dropId="drop-unassigned" data={{ type: 'unassigned' }} className="unassigned-panel">
              <div className="unassigned-panel-title">Sin ubicar ({unassigned.length})</div>
              {unassigned.length === 0 ? (
                <div className="unassigned-panel-empty">Todos los clasificados están ubicados</div>
              ) : (
                unassigned.map((p) =>
                  activeBracket.status === 'DRAFT' && roundSessions.length === 0 ? (
                    <DraggablePlayerChip
                      key={p.playerId}
                      dragId={`drag-unassigned-${p.playerId}`}
                      data={{ type: 'unassigned', playerId: p.playerId }}
                      playerName={p.playerName}
                      handicapIndex={p.playerHandicapIndex}
                    />
                  ) : (
                    <span key={p.playerId} className="bracket-slot-player-name">
                      {formatPlayerLabel(p.playerName, p.playerHandicapIndex)}
                    </span>
                  )
                )
              )}
            </DroppableArea>

            <div className="bracket-zoom-area">
              <TransformWrapper
                key={activeBracket.bracketId}
                initialScale={1}
                minScale={0.35}
                maxScale={2.5}
                limitToBounds={false}
                centerOnInit={false}
                wheel={{ disabled: true }}
                pinch={{ step: 8, excluded: ['bracket-no-pan'] }}
                doubleClick={{ disabled: true }}
                panning={{ excluded: ['bracket-no-pan'] }}
                onTransform={(_, state) => setZoomScale(state.scale)}
              >
                {({ zoomIn, zoomOut, resetTransform }) => (
                  <>
                    <div className="bracket-zoom-toolbar bracket-no-pan">
                      <button type="button" className="btn-zoom" onClick={() => zoomOut(ZOOM_STEP)} title="Alejar">
                        −
                      </button>
                      <span className="bracket-zoom-level">{Math.round(zoomScale * 100)}%</span>
                      <button type="button" className="btn-zoom" onClick={() => zoomIn(ZOOM_STEP)} title="Acercar">
                        +
                      </button>
                      <button
                        type="button"
                        className="btn-zoom btn-zoom-reset"
                        onClick={() => resetTransform()}
                        title="Restablecer zoom y posición"
                      >
                        Restablecer vista
                      </button>
                      <span className="bracket-zoom-hint">Arrastrá el fondo para mover la llave</span>
                    </div>
                    <TransformComponent
                      wrapperClass="bracket-transform-wrapper"
                      contentClass="bracket-transform-content"
                    >
                      <div className="bracket-columns">
                        {activeBracket.rounds.map((round) => {
                          const isLast = round.roundNumber === activeBracket.rounds.length;
                          const isRound1 = round.roundNumber === 1;
                          const pairs: TournamentAdminPlayoffBracketSlot[][] = [];
                          for (let i = 0; i < round.slots.length; i += 2) {
                            pairs.push(round.slots.slice(i, i + 2));
                          }
                          const session = roundSessions.find((s) => s.roundNumber === round.roundNumber);
                          const hasEligiblePair = pairs.some((pair) =>
                            pair.every((s) => s.playerId !== null)
                          );
                          const canStartRound = activeBracket.status === 'CONFIRMED' && !session && hasEligiblePair;
                          return (
                            <div key={round.roundNumber} className="bracket-round">
                              <div className="bracket-round-title">{round.roundName}</div>
                              {canStartRound && (
                                <button
                                  type="button"
                                  className="btn-compact btn-compact-primary bracket-round-action bracket-no-pan"
                                  onClick={() => openStartRoundModal(activeBracket.bracketId, round.roundNumber)}
                                >
                                  ▶ Iniciar Ronda
                                </button>
                              )}
                              {session && (
                                <div className="bracket-round-session-info bracket-no-pan">
                                  <span className="bracket-round-code">Código: {session.code}</span>
                                  <button
                                    type="button"
                                    className="btn-zoom btn-zoom-reset"
                                    onClick={() => copyRoundLink(session.code)}
                                    title="Copiar link para jugadores"
                                  >
                                    Copiar link
                                  </button>
                                  <button
                                    type="button"
                                    className="btn-zoom"
                                    onClick={() =>
                                      setResetRoundTarget({
                                        bracketId: activeBracket.bracketId,
                                        roundNumber: round.roundNumber,
                                      })
                                    }
                                    title="Reiniciar ronda. Quita el código y permite volver a editar las posiciones."
                                  >
                                    ↺
                                  </button>
                                </div>
                              )}
                              <div className="bracket-round-body">
                                {pairs.map((pair, pairIdx) => {
                                  const match = session?.matches.find((m) =>
                                    pair.some((s) => s.slotId === m.topSlotId || s.slotId === m.bottomSlotId)
                                  );
                                  return (
                                    <div key={pairIdx} className="bracket-match-group">
                                      <div
                                        className={`bracket-match-pair ${!isLast ? 'has-connector' : ''}`}
                                      >
                                        {pair.map((slot) =>
                                          renderSlot(activeBracket, slot, pair, round.roundNumber, isRound1)
                                        )}
                                      </div>
                                      {match && (
                                        <div
                                          className={`bracket-match-chip bracket-no-pan ${
                                            match.status === 'FINISHED' ? 'is-finished' : ''
                                          }`}
                                        >
                                          {match.status === 'FINISHED'
                                            ? `✔ ${match.winnerPlayerId === match.playerAId ? match.playerAName : match.playerBName} — ${match.resultSummary}`
                                            : match.liveStatusLabel}
                                        </div>
                                      )}
                                    </div>
                                  );
                                })}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </TransformComponent>
                  </>
                )}
              </TransformWrapper>
            </div>
          </div>
        </DndContext>
      </>
    );
  };

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button onClick={() => navigate(`/administration/${tournamentAdminId}/stages`)} className="btn-back">
            ← Volver a Etapas
          </button>
          <button onClick={loadData} className="btn-refresh">
            ⟳ Actualizar
          </button>
          <button type="button" onClick={copyPublicBracketsLink} className="btn-compact btn-compact-primary">
            Link llaves públicas
          </button>
        </div>
        <div className="tournament-info">
          <h1>Llaves de Playoff</h1>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <Tabs tabs={tabs} activeTab={activeTab} onTabChange={(tabId) => setActiveTab(tabId as PlayoffScoreType)} />

      {!bracket ? (
        <div className="brackets-generate-panel">
          <p>
            Todavía no se generó la llave {activeTab === 'HCP' ? 'Con HCP' : 'SCRATCH'} de este torneo. Se armará
            con los jugadores clasificados según la Tabla de Play Off (asegurate de haber calculado los puntos
            antes).
          </p>
          <button
            onClick={() => handleGenerate(activeTab)}
            className="btn-compact btn-compact-primary"
            disabled={generating}
          >
            {generating ? 'Generando...' : 'Generar Llave'}
          </button>
        </div>
      ) : (
        renderBracket(bracket)
      )}

      <Modal
        isOpen={pendingAction !== null}
        onClose={() => setPendingAction(null)}
        onConfirm={handleConfirmAction}
        title={
          pendingAction?.type === 'confirm'
            ? 'Confirmar llave'
            : pendingAction?.type === 'revert'
            ? 'Revertir a edición'
            : 'Reiniciar llave'
        }
        message={
          pendingAction?.type === 'confirm'
            ? 'Se va a confirmar la llave y quedará habilitada para jugar los partidos. Después vas a poder seguir moviendo jugadores si hace falta.'
            : pendingAction?.type === 'revert'
            ? 'La llave va a volver a estado de edición para poder reasignar jugadores.'
            : 'Se va a borrar por completo la llave, incluyendo los partidos ya jugados. Esta acción no se puede deshacer. ¿Confirmás?'
        }
        type="confirm"
        confirmText="Confirmar"
        cancelText="Cancelar"
      />

      <Modal isOpen={showCopyLinkModal} onClose={() => setShowCopyLinkModal(false)} title="Link copiado" size="medium">
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>✓</div>
          <p style={{ marginBottom: '1rem' }}>El link fue copiado al portapapeles</p>
          <button type="button" onClick={() => setShowCopyLinkModal(false)} className="btn btn-primary">
            Cerrar
          </button>
        </div>
      </Modal>

      <Modal
        isOpen={startRoundModal !== null}
        onClose={() => (startingRound ? undefined : setStartRoundModal(null))}
        title="Iniciar Ronda"
        size="medium"
      >
        <div style={{ padding: '0.5rem 0' }}>
          <p style={{ marginBottom: '1rem', color: '#555' }}>
            Se va a generar un código único para que los jugadores de esta ronda carguen sus tarjetas de
            Match Play. Elegí los tees de salida y la cantidad de hoyos a jugar.
          </p>
          <div className="form-group">
            <label>Tee de Salida Masculino</label>
            <select
              value={startRoundForm.teeMasculinoId}
              onChange={(e) => setStartRoundForm({ ...startRoundForm, teeMasculinoId: e.target.value })}
            >
              <option value="">Sin definir</option>
              {tees
                .filter((t) => (t.genero ?? 'M') === 'M')
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.nombre} {t.grupo ? `(${t.grupo})` : ''}
                  </option>
                ))}
            </select>
          </div>
          <div className="form-group">
            <label>Tee de Salida Femenino</label>
            <select
              value={startRoundForm.teeFemeninoId}
              onChange={(e) => setStartRoundForm({ ...startRoundForm, teeFemeninoId: e.target.value })}
            >
              <option value="">Sin definir</option>
              {tees
                .filter((t) => t.genero === 'F')
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.nombre} {t.grupo ? `(${t.grupo})` : ''}
                  </option>
                ))}
            </select>
          </div>
          <div className="form-group">
            <label>Cantidad de Hoyos</label>
            <select
              value={startRoundForm.cantidadHoyosJuego}
              onChange={(e) => setStartRoundForm({ ...startRoundForm, cantidadHoyosJuego: e.target.value })}
            >
              <option value="9">9 hoyos</option>
              <option value="18">18 hoyos</option>
            </select>
          </div>
          <div className="form-actions">
            <button
              type="button"
              className="btn btn-cancel"
              onClick={() => setStartRoundModal(null)}
              disabled={startingRound}
            >
              Cancelar
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={submitStartRound}
              disabled={startingRound}
            >
              {startingRound ? 'Iniciando...' : 'Iniciar Ronda'}
            </button>
          </div>
        </div>
      </Modal>

      <Modal
        isOpen={newRoundCode !== null}
        onClose={() => setNewRoundCode(null)}
        title="Ronda iniciada"
        size="medium"
      >
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>✓</div>
          <p style={{ marginBottom: '0.5rem' }}>Compartí este link con los jugadores para que carguen sus tarjetas:</p>
          <p style={{ fontSize: '1.1rem', fontWeight: 700, wordBreak: 'break-all', marginBottom: '0.5rem' }}>
            {newRoundCode && getRoundLink(newRoundCode)}
          </p>
          <p style={{ fontSize: '0.85rem', color: '#7f8c8d', marginBottom: '1rem' }}>
            Código de la ronda: <strong>{newRoundCode}</strong>
          </p>
          <button
            type="button"
            className="btn btn-primary"
            style={{ marginRight: '0.5rem' }}
            onClick={() => newRoundCode && copyRoundLink(newRoundCode)}
          >
            Copiar link
          </button>
          <button type="button" onClick={() => setNewRoundCode(null)} className="btn btn-cancel">
            Cerrar
          </button>
        </div>
      </Modal>

      <Modal
        isOpen={resetRoundTarget !== null}
        onClose={() => setResetRoundTarget(null)}
        onConfirm={confirmResetRound}
        title="Reiniciar ronda"
        message="Se va a borrar el código y los partidos de esta ronda. Después vas a poder volver a editar las posiciones. Solo se puede hacer si todavía nadie cargó ningún hoyo. Esta acción no se puede deshacer. ¿Confirmás?"
        type="confirm"
        confirmText="Confirmar"
        cancelText="Cancelar"
      />

      <Modal
        isOpen={winnerOverrideWarning !== null}
        onClose={() => setWinnerOverrideWarning(null)}
        onConfirm={() => {
          if (winnerOverrideWarning) {
            void handleMarkWinner(winnerOverrideWarning.bracketId, winnerOverrideWarning.slotId);
          }
          setWinnerOverrideWarning(null);
        }}
        title="Hay un partido en curso"
        message="Hay un partido de Match Play en curso para este cruce. Si marcás el vencedor a mano, el resultado de la tarjeta que se está jugando quedará sin efecto. ¿Confirmás?"
        type="confirm"
        confirmText="Confirmar"
        cancelText="Cancelar"
      />
    </div>
  );
};

export default TournamentAdminBracketsPage;
