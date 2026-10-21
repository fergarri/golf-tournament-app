import { TeeSheetGroup, TeeSheetPlayer } from '../types';

export const emptyGroup = (): TeeSheetGroup => ({
  holeNumber: null,
  teeTime: null,
  player1Id: null,
  player2Id: null,
  player3Id: null,
  player4Id: null,
});

export const playerSlotIds = (group: TeeSheetGroup): (number | null)[] => [
  group.player1Id,
  group.player2Id,
  group.player3Id,
  group.player4Id,
];

export const withSlot = (group: TeeSheetGroup, slot: number, playerId: number | null): TeeSheetGroup => {
  const ids = playerSlotIds(group);
  ids[slot] = playerId;
  return {
    ...group,
    player1Id: ids[0],
    player2Id: ids[1],
    player3Id: ids[2],
    player4Id: ids[3],
  };
};

export const groupHasPlayers = (group: TeeSheetGroup) => playerSlotIds(group).some((id) => id != null);

export const anyPlayerPlaced = (groups: TeeSheetGroup[]) => groups.some(groupHasPlayers);

export const unassignedPlayers = (players: TeeSheetPlayer[], groups: TeeSheetGroup[]): TeeSheetPlayer[] => {
  const placed = new Set<number>();
  groups.forEach((group) => {
    playerSlotIds(group).forEach((id) => {
      if (id != null) placed.add(id);
    });
  });
  return players.filter((player) => !placed.has(player.playerId));
};

/**
 * De a 4. Si sobra 1, tres threesomes; si sobra 2, dos. Con 5 jugadores, un threesome y un par.
 * Nunca queda un jugador solo, salvo que haya un único inscripto.
 */
const groupSizesFor = (playerCount: number): number[] => {
  if (playerCount <= 0) return [];
  if (playerCount === 1) return [1];
  if (playerCount === 2) return [2];
  if (playerCount === 5) return [3, 2];

  const remainder = playerCount % 4;
  const foursomes = Math.floor(playerCount / 4);
  if (remainder === 0) return Array(foursomes).fill(4);
  if (remainder === 3) return [...Array(foursomes).fill(4), 3];
  if (remainder === 2) return [...Array(foursomes - 1).fill(4), 3, 3];
  return [...Array(foursomes - 2).fill(4), 3, 3, 3];
};

/** Reubica a todos los jugadores desde la primera fila. Conserva hoyo y horario, y no borra filas de más. */
export const fillGroups = (groups: TeeSheetGroup[], playerIds: number[]): TeeSheetGroup[] => {
  const sizes = groupSizesFor(playerIds.length);
  const rowCount = Math.max(groups.length, sizes.length);
  const next: TeeSheetGroup[] = [];
  let offset = 0;
  for (let i = 0; i < rowCount; i += 1) {
    const existing = groups[i];
    const slice = playerIds.slice(offset, offset + (sizes[i] ?? 0));
    offset += sizes[i] ?? 0;
    next.push({
      holeNumber: existing?.holeNumber ?? null,
      teeTime: existing?.teeTime ?? null,
      player1Id: slice[0] ?? null,
      player2Id: slice[1] ?? null,
      player3Id: slice[2] ?? null,
      player4Id: slice[3] ?? null,
    });
  }
  return next;
};

export const sortPlayersByHandicap = (players: TeeSheetPlayer[]): TeeSheetPlayer[] =>
  [...players].sort((a, b) => {
    const aHandicap = a.handicapIndex;
    const bHandicap = b.handicapIndex;
    const aMissing = aHandicap === null || aHandicap === undefined;
    const bMissing = bHandicap === null || bHandicap === undefined;
    if (aMissing !== bMissing) return aMissing ? 1 : -1;
    if (aHandicap != null && bHandicap != null && aHandicap !== bHandicap) {
      return aHandicap - bHandicap;
    }
    const byLast = a.apellido.localeCompare(b.apellido, 'es', { sensitivity: 'base' });
    if (byLast !== 0) return byLast;
    return a.nombre.localeCompare(b.nombre, 'es', { sensitivity: 'base' });
  });

export const shufflePlayers = (players: TeeSheetPlayer[]): TeeSheetPlayer[] => {
  const next = [...players];
  for (let i = next.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [next[i], next[j]] = [next[j], next[i]];
  }
  return next;
};

export type PlayerOrigin =
  | { kind: 'unassigned' }
  | { kind: 'cell'; rowIndex: number; slot: number };

export const movePlayer = (
  groups: TeeSheetGroup[],
  playerId: number,
  from: PlayerOrigin,
  to: PlayerOrigin,
): TeeSheetGroup[] => {
  if (to.kind === 'unassigned' && from.kind === 'unassigned') return groups;
  if (
    to.kind === 'cell' &&
    from.kind === 'cell' &&
    from.rowIndex === to.rowIndex &&
    from.slot === to.slot
  ) {
    return groups;
  }

  const next = groups.map((group) => ({ ...group }));
  for (let rowIndex = 0; rowIndex < next.length; rowIndex += 1) {
    const ids = playerSlotIds(next[rowIndex]);
    for (let slot = 0; slot < ids.length; slot += 1) {
      if (ids[slot] === playerId) {
        next[rowIndex] = withSlot(next[rowIndex], slot, null);
      }
    }
  }

  if (to.kind === 'unassigned') return next;

  const displacedId = playerSlotIds(next[to.rowIndex])[to.slot];
  next[to.rowIndex] = withSlot(next[to.rowIndex], to.slot, playerId);
  if (from.kind === 'cell' && displacedId != null && displacedId !== playerId) {
    next[from.rowIndex] = withSlot(next[from.rowIndex], from.slot, displacedId);
  }
  return next;
};

export const moveRow = (groups: TeeSheetGroup[], fromIndex: number, toIndex: number): TeeSheetGroup[] => {
  if (
    fromIndex === toIndex ||
    fromIndex < 0 ||
    toIndex < 0 ||
    fromIndex >= groups.length ||
    toIndex >= groups.length
  ) {
    return groups;
  }
  const next = [...groups];
  const [row] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, row);
  return next;
};

export const publicStartColumn = (
  groups: { holeNumber: number | null; teeTime: string | null }[],
): 'Hoyo - Hora' | 'Hoyo' | 'Hora' | null => {
  const hasHole = groups.some((group) => group.holeNumber != null);
  const hasTime = groups.some((group) => Boolean(group.teeTime));
  if (hasHole && hasTime) return 'Hoyo - Hora';
  if (hasHole) return 'Hoyo';
  if (hasTime) return 'Hora';
  return null;
};

export const formatStartCell = (holeNumber: number | null, teeTime: string | null): string => {
  const parts: string[] = [];
  if (holeNumber != null) parts.push(`Hoyo ${holeNumber}`);
  if (teeTime) parts.push(teeTime);
  return parts.join(' · ');
};
