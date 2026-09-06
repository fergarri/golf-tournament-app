import { useEffect, useRef, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import {
  tournamentAdminPlayoffMatchService,
  UpdatePlayoffMatchHoleScore,
} from '../services/tournamentAdminPlayoffMatchService';
import { PlayoffMatchHoleInfo, PlayoffMatchState } from '../types';
import Modal from '../components/Modal';
import './PlayoffMatchScorecardPage.css';

type LocalHoleScore = { propio: number | null; rival: number | null };

const PlayoffMatchScorecardPage = () => {
  const { code, matchId: matchIdParam } = useParams<{ code: string; matchId: string }>();
  const location = useLocation();
  const { matricula } = (location.state as { matricula?: string }) || {};
  const matchId = Number(matchIdParam);

  const [state, setState] = useState<PlayoffMatchState | null>(null);
  const [localScores, setLocalScores] = useState<{ [holeSequence: number]: LocalHoleScore }>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [lastSaved, setLastSaved] = useState<Date | null>(null);

  const [modalConfig, setModalConfig] = useState<{
    title: string;
    message: string;
    type: 'info' | 'success' | 'warning' | 'error' | 'confirm';
    onConfirm?: () => void;
  } | null>(null);

  const saveTimeoutRef = useRef<number | null>(null);
  const localScoresRef = useRef(localScores);

  useEffect(() => {
    localScoresRef.current = localScores;
  }, [localScores]);

  useEffect(() => {
    if (!code || !matricula || !Number.isFinite(matchId)) {
      setError('Acceso inválido. Volvé a ingresar tu matrícula.');
      setLoading(false);
      return;
    }
    void loadState();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code, matricula, matchId]);

  // Polling para reflejar cambios cargados por el rival.
  useEffect(() => {
    if (!code || !matricula || !Number.isFinite(matchId)) return;
    if (state?.status === 'FINISHED') return;
    const interval = window.setInterval(() => {
      void refreshState({ silent: true });
    }, 15000);
    return () => window.clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code, matricula, matchId, state?.status]);

  useEffect(() => {
    return () => {
      if (saveTimeoutRef.current !== null) clearTimeout(saveTimeoutRef.current);
    };
  }, []);

  const applyStateToLocalScores = (data: PlayoffMatchState) => {
    const mySide = data.requestingPlayerId === data.playerA.playerId ? 'A' : 'B';
    const next: { [holeSequence: number]: LocalHoleScore } = {};
    data.holes.forEach((hole) => {
      next[hole.holeSequence] = {
        propio: mySide === 'A' ? hole.golpesPropioA : hole.golpesPropioB,
        rival: mySide === 'A' ? hole.golpesRivalA : hole.golpesRivalB,
      };
    });
    setLocalScores(next);
  };

  const loadState = async () => {
    if (!code || !matricula) return;
    try {
      setLoading(true);
      const data = await tournamentAdminPlayoffMatchService.getMatchState(code, matchId, matricula);
      setState(data);
      applyStateToLocalScores(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'No se pudo cargar el partido');
    } finally {
      setLoading(false);
    }
  };

  const refreshState = async (options?: { silent?: boolean }) => {
    if (!code || !matricula) return;
    try {
      const data = await tournamentAdminPlayoffMatchService.getMatchState(code, matchId, matricula);
      setState(data);
      // No pisamos lo que el usuario está tipeando; solo sincronizamos si no hay
      // guardado pendiente.
      if (saveTimeoutRef.current === null) {
        applyStateToLocalScores(data);
      }
    } catch (err) {
      if (!options?.silent) {
        console.error('Error refrescando el partido', err);
      }
    }
  };

  const mySide: 'A' | 'B' | null = state
    ? state.requestingPlayerId === state.playerA.playerId
      ? 'A'
      : 'B'
    : null;
  const me = state ? (mySide === 'A' ? state.playerA : state.playerB) : null;
  const opponent = state ? (mySide === 'A' ? state.playerB : state.playerA) : null;

  const updateScore = (holeSequence: number, field: 'propio' | 'rival', value: string) => {
    const parsed = value ? parseInt(value, 10) : null;
    setLocalScores((prev) => ({
      ...prev,
      [holeSequence]: {
        propio: prev[holeSequence]?.propio ?? null,
        rival: prev[holeSequence]?.rival ?? null,
        [field]: parsed,
      },
    }));

    if (saveTimeoutRef.current !== null) clearTimeout(saveTimeoutRef.current);
    saveTimeoutRef.current = window.setTimeout(() => {
      void saveScores();
    }, 1000);
  };

  const saveScores = async () => {
    if (!code || !matricula || !state) return;
    const holeScores: UpdatePlayoffMatchHoleScore[] = state.holes
      .filter((h) => !h.pending || localScoresRef.current[h.holeSequence]?.propio != null || localScoresRef.current[h.holeSequence]?.rival != null)
      .map((h) => ({
        holeSequence: h.holeSequence,
        golpesPropio: localScoresRef.current[h.holeSequence]?.propio ?? undefined,
        golpesRival: localScoresRef.current[h.holeSequence]?.rival ?? undefined,
      }));

    try {
      setSaving(true);
      const updated = await tournamentAdminPlayoffMatchService.updateHoles(code, matchId, matricula, holeScores);
      setState(updated);
      setLastSaved(new Date());
      saveTimeoutRef.current = null;
      // Reconciliamos los hoyos "extra" (sudden death) que puedan haberse habilitado.
      const currentLocal = localScoresRef.current;
      const next: { [holeSequence: number]: LocalHoleScore } = { ...currentLocal };
      updated.holes.forEach((hole) => {
        if (next[hole.holeSequence] === undefined) {
          next[hole.holeSequence] = {
            propio: mySide === 'A' ? hole.golpesPropioA : hole.golpesPropioB,
            rival: mySide === 'A' ? hole.golpesRivalA : hole.golpesRivalB,
          };
        }
      });
      setLocalScores(next);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error guardando la puntuación');
    } finally {
      setSaving(false);
    }
  };

  const showModal = (
    title: string,
    message: string,
    type: 'info' | 'success' | 'warning' | 'error' | 'confirm',
    onConfirm?: () => void
  ) => {
    setModalConfig({ title, message, type, onConfirm });
  };

  const handleDeliver = () => {
    showModal(
      'Confirmar entrega',
      '¿Seguro que querés entregar la tarjeta de este partido? No vas a poder modificarla después.',
      'confirm',
      async () => {
        if (!code || !matricula) return;
        try {
          setSaving(true);
          const updated = await tournamentAdminPlayoffMatchService.deliver(code, matchId, matricula);
          setState(updated);
          applyStateToLocalScores(updated);
          showModal('Tarjeta entregada', updated.resultSummary ? `Resultado: ${updated.resultSummary}` : 'La tarjeta fue entregada correctamente.', 'success');
        } catch (err: any) {
          showModal('Error', err.response?.data?.message || 'No se pudo entregar la tarjeta', 'error');
        } finally {
          setSaving(false);
        }
      }
    );
  };

  const handleConcede = () => {
    showModal(
      'Levantar Bola',
      `¿Seguro que querés levantar la bola? Se le va a otorgar la victoria a ${opponent?.playerName ?? 'tu rival'} de inmediato.`,
      'confirm',
      async () => {
        if (!code || !matricula) return;
        try {
          setSaving(true);
          const updated = await tournamentAdminPlayoffMatchService.concede(code, matchId, matricula);
          setState(updated);
          applyStateToLocalScores(updated);
          showModal('Bola levantada', `${opponent?.playerName ?? 'Tu rival'} ganó el partido.`, 'info');
        } catch (err: any) {
          showModal('Error', err.response?.data?.message || 'No se pudo levantar la bola', 'error');
        } finally {
          setSaving(false);
        }
      }
    );
  };

  const liveStatusLabel = (): string => {
    if (!state || !me || !opponent) return '';
    const { holesWonA, holesWonB, holesRemaining } = state.tally;
    const myHolesWon = mySide === 'A' ? holesWonA : holesWonB;
    const oppHolesWon = mySide === 'A' ? holesWonB : holesWonA;
    if (myHolesWon === oppHolesWon) {
      return holesRemaining > 0 ? 'IGUALADOS (ALL SQUARE)' : 'IGUALADOS';
    }
    const leaderIsMe = myHolesWon > oppHolesWon;
    const margin = Math.abs(myHolesWon - oppHolesWon);
    const leaderName = leaderIsMe ? 'Vos vas' : `${opponent.playerName} va`;
    return `${leaderName} ${margin} UP`;
  };

  if (loading) {
    return (
      <div className="playoff-match-container">
        <div className="loading">Cargando partido...</div>
      </div>
    );
  }

  if (error || !state || !me || !opponent || !mySide) {
    return (
      <div className="playoff-match-container">
        <div className="error-card">
          <h2>Error</h2>
          <p>{error || 'No se encontró el partido'}</p>
        </div>
      </div>
    );
  }

  const isFinished = state.status === 'FINISHED';
  const myCardDelivered = me.cardStatus === 'DELIVERED';
  const inputsDisabled = isFinished || myCardDelivered;

  const cantidadHoyosJuego = state.cantidadHoyosJuego;
  const regularHoles = state.holes.filter((h) => h.holeSequence <= cantidadHoyosJuego);
  const extraHoles = state.holes.filter((h) => h.holeSequence > cantidadHoyosJuego);
  const frontNine = regularHoles.filter((h) => h.numeroHoyo <= 9);
  const backNine = regularHoles.filter((h) => h.numeroHoyo > 9);
  const hasBackNine = backNine.length > 0;
  const hasExtraHoles = extraHoles.length > 0;

  const myStrokesOf = (hole: PlayoffMatchHoleInfo) => (mySide === 'A' ? hole.strokesA : hole.strokesB);
  const oppStrokesOf = (hole: PlayoffMatchHoleInfo) => (mySide === 'A' ? hole.strokesB : hole.strokesA);
  const myDistanceOf = (hole: PlayoffMatchHoleInfo) => (mySide === 'A' ? hole.distanceA : hole.distanceB);
  const myValidadoOf = (hole: PlayoffMatchHoleInfo) => (mySide === 'A' ? hole.validadoA : hole.validadoB);
  const myPropioLocal = (hole: PlayoffMatchHoleInfo) => localScores[hole.holeSequence]?.propio ?? null;
  const myRivalLocal = (hole: PlayoffMatchHoleInfo) => localScores[hole.holeSequence]?.rival ?? null;

  const holeResultForRow = (hole: PlayoffMatchHoleInfo, rowSide: 'me' | 'rival'): 'won' | 'lost' | 'halved' | null => {
    if (!hole.holeWinner) return null;
    if (hole.holeWinner === 'HALVED') return 'halved';
    const winnerIsMe = hole.holeWinner === mySide;
    if (rowSide === 'me') return winnerIsMe ? 'won' : 'lost';
    return winnerIsMe ? 'lost' : 'won';
  };

  const sumPar = (holes: PlayoffMatchHoleInfo[]) => holes.reduce((sum, h) => sum + h.par, 0);
  const sumDistance = (holes: PlayoffMatchHoleInfo[]) =>
    holes.reduce((sum, h) => sum + (myDistanceOf(h) || 0), 0) || null;
  const sumMyScore = (holes: PlayoffMatchHoleInfo[]) => {
    const values = holes.map((h) => myPropioLocal(h)).filter((v): v is number => v != null);
    return values.length ? values.reduce((a, b) => a + b, 0) : null;
  };
  const sumRivalScore = (holes: PlayoffMatchHoleInfo[]) => {
    const values = holes.map((h) => myRivalLocal(h)).filter((v): v is number => v != null);
    return values.length ? values.reduce((a, b) => a + b, 0) : null;
  };

  const allHoles = [...regularHoles, ...extraHoles];

  const renderStrokeDots = (strokes: number) => (
    <div
      className="stroke-dots"
      title={strokes > 0 ? `Recibe ${strokes} golpe${strokes > 1 ? 's' : ''} de hándicap en este hoyo` : undefined}
    >
      {strokes > 0 &&
        Array.from({ length: strokes }).map((_, i) => (
          <span key={i} className="stroke-dot">
            ●
          </span>
        ))}
    </div>
  );

  const renderHoyoCells = (holes: PlayoffMatchHoleInfo[]) =>
    holes.map((hole) => (
      <td key={hole.holeSequence} className="playoff-hoyo-cell">
        {hole.numeroHoyo}
      </td>
    ));

  const renderDistanciaCells = (holes: PlayoffMatchHoleInfo[]) =>
    holes.map((hole) => (
      <td key={hole.holeSequence} className="playoff-distance-cell">
        {myDistanceOf(hole) ?? '-'}
      </td>
    ));

  const renderParCells = (holes: PlayoffMatchHoleInfo[]) =>
    holes.map((hole) => (
      <td key={hole.holeSequence} className="playoff-par-cell">
        {hole.par}
      </td>
    ));

  const renderHcpCells = (holes: PlayoffMatchHoleInfo[]) =>
    holes.map((hole) => (
      <td key={hole.holeSequence} className="playoff-hcp-cell">
        {hole.handicapIndex}
      </td>
    ));

  const renderMyScoreCells = (holes: PlayoffMatchHoleInfo[]) =>
    holes.map((hole) => {
      const result = holeResultForRow(hole, 'me');
      return (
        <td
          key={hole.holeSequence}
          className={`playoff-score-cell ${result ? `result-${result}` : ''}`}
        >
          {renderStrokeDots(myStrokesOf(hole))}
          <input
            type="number"
            min={1}
            max={15}
            value={myPropioLocal(hole) ?? ''}
            onChange={(e) => updateScore(hole.holeSequence, 'propio', e.target.value)}
            disabled={inputsDisabled}
            placeholder="-"
            className="playoff-score-input"
          />
        </td>
      );
    });

  const renderRivalScoreCells = (holes: PlayoffMatchHoleInfo[]) =>
    holes.map((hole) => {
      const result = holeResultForRow(hole, 'rival');
      const validado = myValidadoOf(hole);
      const hasBoth = myRivalLocal(hole) != null;
      const concordanceClass = hasBoth ? (validado ? 'concordance-match' : 'concordance-pending') : '';
      return (
        <td
          key={hole.holeSequence}
          className={`playoff-score-cell ${result ? `result-${result}` : ''} ${concordanceClass}`}
        >
          {renderStrokeDots(oppStrokesOf(hole))}
          <input
            type="number"
            min={1}
            max={15}
            value={myRivalLocal(hole) ?? ''}
            onChange={(e) => updateScore(hole.holeSequence, 'rival', e.target.value)}
            disabled={inputsDisabled}
            placeholder="-"
            className="playoff-score-input playoff-score-input-rival"
          />
        </td>
      );
    });

  return (
    <div className="playoff-match-container">
      <div className="playoff-match-header">
        <h1>Match Play — {state.scoreType === 'HCP' ? 'Con HCP' : 'SCRATCH'}</h1>
        <p className="playoff-match-players">
          <strong>{me.playerName}</strong> (Vos) vs <strong>{opponent.playerName}</strong>
        </p>
        {me.handicapCourse !== null && (
          <p className="playoff-match-hcp">Tu Handicap de Cancha: {me.handicapCourse}</p>
        )}

        {isFinished ? (
          <div className="playoff-status-chip finished">
            {state.winnerPlayerId === me.playerId
              ? `🏆 Ganaste — ${state.resultSummary}`
              : `Perdiste contra ${opponent.playerName} — ${state.resultSummary}`}
          </div>
        ) : (
          <div className="playoff-status-chip in-progress">{liveStatusLabel()}</div>
        )}

        {!isFinished && state.tally.decided && (
          <div className="playoff-decided-banner">
            ✅ Partido definido: {state.tally.leaderPlayerId === me.playerId ? 'ganaste' : `ganó ${opponent.playerName}`} por{' '}
            {state.tally.margin} {state.tally.margin === 1 ? 'hoyo' : 'hoyos'}
            {state.tally.holesRemaining > 0 ? ` (quedaban ${state.tally.holesRemaining} por jugar)` : ''}. Los hoyos
            cargados después de este punto no modifican el resultado. Ya podés{' '}
            <strong>entregar la tarjeta</strong> para confirmarlo.
          </div>
        )}

        {!isFinished && !state.canDeliver && state.blockedReason && (
          <div className="playoff-blocked-reason">{state.blockedReason}</div>
        )}

        <div className="playoff-legend-dot">
          <span className="stroke-dot small">●</span> = golpe de hándicap a favor en ese hoyo
        </div>

        <div className="auto-save-indicator">
          {saving ? (
            <span className="saving">Guardando...</span>
          ) : lastSaved ? (
            <span className="saved">Guardado {lastSaved.toLocaleTimeString()}</span>
          ) : (
            <span className="auto-save">Auto-guardado activado</span>
          )}
        </div>
      </div>

      <div className="playoff-match-table-wrapper">
        <table className="playoff-match-table">
          <tbody>
            <tr className="playoff-row-hoyo">
              <td className="playoff-sticky-col playoff-label-cell">HOYO</td>
              {renderHoyoCells(frontNine)}
              {hasBackNine && <td className="playoff-subtotal-cell">IDA</td>}
              {renderHoyoCells(backNine)}
              {hasBackNine && <td className="playoff-subtotal-cell">VTA</td>}
              {hasExtraHoles && <td className="playoff-subtotal-cell">M.S.</td>}
              {renderHoyoCells(extraHoles)}
              <td className="playoff-total-cell">TOTAL</td>
            </tr>

            <tr className="playoff-row-distancia">
              <td className="playoff-sticky-col playoff-label-cell">
                {me.teeName ? ` ${me.teeName}` : 'DISTANCIA'}
              </td>
              {renderDistanciaCells(frontNine)}
              {hasBackNine && <td className="playoff-subtotal-cell">{sumDistance(frontNine) ?? '-'}</td>}
              {renderDistanciaCells(backNine)}
              {hasBackNine && <td className="playoff-subtotal-cell">{sumDistance(backNine) ?? '-'}</td>}
              {hasExtraHoles && <td className="playoff-subtotal-cell" />}
              {renderDistanciaCells(extraHoles)}
              <td className="playoff-total-cell">{sumDistance(regularHoles) ?? '-'}</td>
            </tr>

            <tr className="playoff-row-hcp">
              <td className="playoff-sticky-col playoff-label-cell">HCP HOYO</td>
              {renderHcpCells(frontNine)}
              {hasBackNine && <td className="playoff-subtotal-cell" />}
              {renderHcpCells(backNine)}
              {hasBackNine && <td className="playoff-subtotal-cell" />}
              {hasExtraHoles && <td className="playoff-subtotal-cell" />}
              {renderHcpCells(extraHoles)}
              <td className="playoff-total-cell" />
            </tr>

            <tr className="playoff-row-par">
              <td className="playoff-sticky-col playoff-label-cell">PAR</td>
              {renderParCells(frontNine)}
              {hasBackNine && <td className="playoff-subtotal-cell">{sumPar(frontNine)}</td>}
              {renderParCells(backNine)}
              {hasBackNine && <td className="playoff-subtotal-cell">{sumPar(backNine)}</td>}
              {hasExtraHoles && <td className="playoff-subtotal-cell" />}
              {renderParCells(extraHoles)}
              <td className="playoff-total-cell">{sumPar(regularHoles)}</td>
            </tr>

            <tr className="playoff-row-player playoff-row-me">
              <td className="playoff-sticky-col playoff-label-cell playoff-player-label">{me.shortName}</td>
              {renderMyScoreCells(frontNine)}
              {hasBackNine && (
                <td className="playoff-subtotal-cell">{sumMyScore(frontNine) ?? '-'}</td>
              )}
              {renderMyScoreCells(backNine)}
              {hasBackNine && (
                <td className="playoff-subtotal-cell">{sumMyScore(backNine) ?? '-'}</td>
              )}
              {hasExtraHoles && <td className="playoff-subtotal-cell" />}
              {renderMyScoreCells(extraHoles)}
              <td className="playoff-total-cell">{sumMyScore(allHoles) ?? '-'}</td>
            </tr>

            <tr className="playoff-row-player playoff-row-rival">
              <td className="playoff-sticky-col playoff-label-cell playoff-rival-label">{opponent.shortName}</td>
              {renderRivalScoreCells(frontNine)}
              {hasBackNine && (
                <td className="playoff-subtotal-cell">{sumRivalScore(frontNine) ?? '-'}</td>
              )}
              {renderRivalScoreCells(backNine)}
              {hasBackNine && (
                <td className="playoff-subtotal-cell">{sumRivalScore(backNine) ?? '-'}</td>
              )}
              {hasExtraHoles && <td className="playoff-subtotal-cell" />}
              {renderRivalScoreCells(extraHoles)}
              <td className="playoff-total-cell">{sumRivalScore(allHoles) ?? '-'}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div className="playoff-match-legend">
        <p>
          Fila <strong>{me.shortName}</strong>: tus golpes. Fila <strong>{opponent.shortName}</strong>: los golpes de{' '}
          {opponent.playerName} (vos los anotás, como su marcador).
        </p>
      </div>

      <div className="playoff-match-floating-actions">
        <button
          type="button"
          className="btn btn-cancel btn-floating"
          onClick={handleConcede}
          disabled={isFinished || myCardDelivered}
        >
          {isFinished ? 'Partido finalizado' : 'Levantar Bola'}
        </button>
        <button
          type="button"
          className="btn btn-deliver btn-floating"
          onClick={handleDeliver}
          disabled={isFinished || myCardDelivered || !state.canDeliver}
        >
          {isFinished ? 'Ya entregada' : myCardDelivered ? 'Ya entregada' : 'Entregar tarjeta'}
        </button>
      </div>

      <Modal
        isOpen={modalConfig !== null}
        onClose={() => setModalConfig(null)}
        onConfirm={modalConfig?.onConfirm}
        title={modalConfig?.title || ''}
        message={modalConfig?.message}
        type={modalConfig?.type}
        confirmText={modalConfig?.type === 'confirm' ? 'Confirmar' : 'OK'}
        cancelText="Cancelar"
      />
    </div>
  );
};

export default PlayoffMatchScorecardPage;
