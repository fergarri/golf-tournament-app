import { useEffect, useMemo, useState } from 'react';
import { scorecardService } from '../services/scorecardService';
import { HoleScore, Scorecard } from '../types';
import { getScorecardStatusLabel } from '../utils/scorecardStatusLabel';
import {
  canViewPublicScorecard,
  formatNeto,
  formatScoreToPar,
} from '../utils/publicScorecard';
import '../pages/TournamentLeaderboardPage.css';
import '../pages/TournamentScorecardPage.css';
import './PublicScorecardModal.css';

export interface PublicScorecardOpenRequest {
  scorecardId: number;
  playerName: string;
  handicapIndex?: number;
  handicapCourse?: number;
  tournamentId?: number;
}

interface PublicScorecardModalProps {
  request: PublicScorecardOpenRequest | null;
  onClose: () => void;
}

const sumGolpes = (holes: HoleScore[]) => holes.reduce((s, h) => s + (h.golpesPropio ?? 0), 0);
const sumPar = (holes: HoleScore[]) => holes.reduce((s, h) => s + h.par, 0);

const PublicScorecardModal = ({ request, onClose }: PublicScorecardModalProps) => {
  const [scorecard, setScorecard] = useState<Scorecard | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!request) {
      setScorecard(null);
      setError('');
      setLoading(false);
      return;
    }

    let cancelled = false;
    const load = async () => {
      try {
        setLoading(true);
        setError('');
        const data = await scorecardService.getById(request.scorecardId);
        if (cancelled) return;
        if (request.tournamentId != null && data.tournamentId !== request.tournamentId) {
          setScorecard(null);
          setError('No se pudo mostrar la tarjeta de este jugador.');
          return;
        }
        if (!canViewPublicScorecard(data.status, data.id)) {
          setScorecard(null);
          setError('Esta tarjeta está en curso y no se puede mostrar.');
          return;
        }
        setScorecard(data);
      } catch {
        if (!cancelled) {
          setScorecard(null);
          setError('No se pudo cargar la tarjeta.');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, [request]);

  const layout = useMemo(() => {
    if (!scorecard?.holeScores?.length) return null;
    const sorted = [...scorecard.holeScores].sort((a, b) => a.numeroHoyo - b.numeroHoyo);
    const nineHoles = scorecard.cantidadHoyosJuego === 9;
    const frontNine = sorted.filter((h) => h.numeroHoyo <= 9);
    const backNine = nineHoles ? [] : sorted.filter((h) => h.numeroHoyo > 9);
    const hasBackNine = backNine.length > 0;
    const holes = hasBackNine ? [...frontNine, ...backNine] : frontNine.length ? frontNine : sorted;
    const totalGross = sumGolpes(holes);
    const totalParSum = sumPar(holes);
    const hc = scorecard.handicapCourse ?? request?.handicapCourse;
    const neto =
      totalGross > 0 && hc != null && !Number.isNaN(Number(hc)) ? totalGross - Number(hc) : null;
    const toPar = neto != null ? neto - totalParSum : null;
    return {
      holes,
      frontNine,
      backNine,
      hasBackNine,
      frontGross: sumGolpes(frontNine),
      backGross: sumGolpes(backNine),
      frontPar: sumPar(frontNine),
      backPar: sumPar(backNine),
      totalGross,
      totalParSum,
      neto,
      toPar,
    };
  }, [scorecard, request?.handicapCourse]);

  useEffect(() => {
    if (!request) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = 'unset';
    };
  }, [request, onClose]);

  if (!request) return null;

  const playerName = request.playerName || scorecard?.playerName;
  const handicapCourse = scorecard?.handicapCourse ?? request.handicapCourse;
  const handicapIndex = request.handicapIndex;
  const statusLabel = scorecard ? getScorecardStatusLabel(scorecard.status, true) : null;
  const toPar = layout?.toPar;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className={`modal-content scorecard-modal${layout && !layout.hasBackNine ? ' public-scorecard-nine' : ''}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div className="public-scorecard-summary">
            <div className="public-scorecard-summary-item">
              <span className="public-scorecard-summary-label">Jugador</span>
              <span className="public-scorecard-summary-value">
                {playerName}
                {statusLabel && (
                  <span className="public-scorecard-status" style={{ color: statusLabel.color }}>
                    {statusLabel.code}
                  </span>
                )}
              </span>
            </div>
            <div className="public-scorecard-summary-item">
              <span className="public-scorecard-summary-label">HCP Course</span>
              <span className="public-scorecard-summary-value">
                {handicapCourse != null ? Number(handicapCourse).toFixed(1) : '-'}
              </span>
            </div>
            <div className="public-scorecard-summary-item">
              <span className="public-scorecard-summary-label">HCP Index</span>
              <span className="public-scorecard-summary-value">
                {handicapIndex != null ? Number(handicapIndex).toFixed(1) : '-'}
              </span>
            </div>
            <div className="public-scorecard-summary-item">
              <span className="public-scorecard-summary-label">To Par</span>
              <span className="public-scorecard-summary-value">
                {toPar == null ? (
                  '-'
                ) : (
                  <span className={`score-to-par ${toPar < 0 ? 'under-par' : toPar > 0 ? 'over-par' : 'even-par'}`}>
                    {formatScoreToPar(toPar)}
                  </span>
                )}
              </span>
            </div>
          </div>
          <button className="modal-close" onClick={onClose} type="button" aria-label="Cerrar">
            ×
          </button>
        </div>

        <div className="modal-body public-scorecard-modal-body">
          {loading && <div className="public-scorecard-placeholder">Cargando tarjeta...</div>}
          {!loading && error && <div className="public-scorecard-placeholder error">{error}</div>}
          {!loading && !error && layout && (
            <div className="scorecard-table-wrapper scorecard-modal-table-inner">
              <div className="scorecard-table-container">
                <table className="scorecard-table public-scorecard-table">
                  <tbody>
                    {layout.hasBackNine ? (
                      <>
                        <tr className="hoyo-row">
                          <td className="sticky-col label-cell">HOYO</td>
                          {layout.frontNine.map((hole) => (
                            <td key={hole.id} className="hoyo-cell">{hole.numeroHoyo}</td>
                          ))}
                          <td className="subtotal-cell hoyo-cell">IDA</td>
                          {layout.backNine.map((hole) => (
                            <td key={hole.id} className="hoyo-cell">{hole.numeroHoyo}</td>
                          ))}
                          <td className="subtotal-cell hoyo-cell">VTA</td>
                          <td className="total-cell final-total-cell">GROSS</td>
                          <td className="total-cell final-total-cell">NETO</td>
                        </tr>
                        <tr className="par-row">
                          <td className="sticky-col label-cell">PAR</td>
                          {layout.frontNine.map((hole) => (
                            <td key={hole.id} className="par-cell">{hole.par}</td>
                          ))}
                          <td className="subtotal-cell par-cell">{layout.frontPar}</td>
                          {layout.backNine.map((hole) => (
                            <td key={hole.id} className="par-cell">{hole.par}</td>
                          ))}
                          <td className="subtotal-cell par-cell">{layout.backPar}</td>
                          <td className="total-cell final-total-cell">{layout.totalParSum}</td>
                          <td className="total-cell final-total-cell"></td>
                        </tr>
                        <tr className="score-row player-row">
                          <td className="sticky-col label-cell">GOLPES</td>
                          {layout.frontNine.map((hole) => (
                            <td key={hole.id}>{hole.golpesPropio ?? '-'}</td>
                          ))}
                          <td className="subtotal-cell score-total">{layout.frontGross || '-'}</td>
                          {layout.backNine.map((hole) => (
                            <td key={hole.id}>{hole.golpesPropio ?? '-'}</td>
                          ))}
                          <td className="subtotal-cell score-total">{layout.backGross || '-'}</td>
                          <td className="total-cell score-total">{layout.totalGross || '-'}</td>
                          <td className="total-cell score-total">
                            {layout.neto != null ? formatNeto(layout.neto) : '-'}
                          </td>
                        </tr>
                      </>
                    ) : (
                      <>
                        <tr className="hoyo-row">
                          <td className="sticky-col label-cell">HOYO</td>
                          {layout.holes.map((hole) => (
                            <td key={hole.id} className="hoyo-cell">{hole.numeroHoyo}</td>
                          ))}
                          <td className="total-cell final-total-cell">GROSS</td>
                          <td className="total-cell final-total-cell">NETO</td>
                        </tr>
                        <tr className="par-row">
                          <td className="sticky-col label-cell">PAR</td>
                          {layout.holes.map((hole) => (
                            <td key={hole.id} className="par-cell">{hole.par}</td>
                          ))}
                          <td className="total-cell final-total-cell">{layout.totalParSum}</td>
                          <td className="total-cell final-total-cell"></td>
                        </tr>
                        <tr className="score-row player-row">
                          <td className="sticky-col label-cell">GOLPES</td>
                          {layout.holes.map((hole) => (
                            <td key={hole.id}>{hole.golpesPropio ?? '-'}</td>
                          ))}
                          <td className="total-cell score-total">{layout.totalGross || '-'}</td>
                          <td className="total-cell score-total">
                            {layout.neto != null ? formatNeto(layout.neto) : '-'}
                          </td>
                        </tr>
                      </>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

interface PublicPlayerNameProps {
  name: string;
  status?: string;
  scorecardId?: number | null;
  onOpen: () => void;
}

export const PublicPlayerName = ({ name, status, scorecardId, onOpen }: PublicPlayerNameProps) => {
  if (!canViewPublicScorecard(status, scorecardId)) {
    return <>{name}</>;
  }
  return (
    <button
      type="button"
      className="player-name-link"
      onClick={(e) => {
        e.stopPropagation();
        onOpen();
      }}
    >
      {name}
    </button>
  );
};

export default PublicScorecardModal;
