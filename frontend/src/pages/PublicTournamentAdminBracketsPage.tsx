import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { TransformWrapper, TransformComponent } from 'react-zoom-pan-pinch';
import Tabs, { Tab } from '../components/Tabs';
import { tournamentAdminPlayoffBracketService } from '../services/tournamentAdminPlayoffBracketService';
import { PlayoffScoreType, TournamentAdminPlayoffBracket, TournamentAdminPlayoffBracketSlot } from '../types';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';
import './TournamentAdminBracketsPage.css';

const formatPlayerLabel = (name: string, handicapIndex: number | null | undefined) =>
  handicapIndex !== null && handicapIndex !== undefined ? name : name;

// El "step" de zoom-pan-pinch se suma directo a la escala (1 = 100%), así que
// 0.05 equivale a variar el zoom de a 5 unidades por click en +/-.
const ZOOM_STEP = 0.05;

const PublicTournamentAdminBracketsPage = () => {
  const { tournamentAdminId: tournamentAdminIdParam } = useParams<{ tournamentAdminId: string }>();
  const tournamentAdminId = Number(tournamentAdminIdParam);

  const [data, setData] = useState<Awaited<ReturnType<typeof tournamentAdminPlayoffBracketService.getPublic>> | null>(
    null
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<PlayoffScoreType>('HCP');
  const [zoomScale, setZoomScale] = useState(1);

  useEffect(() => {
    if (!Number.isFinite(tournamentAdminId)) {
      setError('Parámetros inválidos');
      setLoading(false);
      return;
    }
    loadData();
    const interval = setInterval(() => loadData({ silent: true }), 100000);
    return () => clearInterval(interval);
  }, [tournamentAdminId]);

  useEffect(() => {
    setZoomScale(1);
  }, [activeTab]);

  const loadData = async (options?: { silent?: boolean }) => {
    const silent = options?.silent ?? false;
    try {
      if (!silent) setLoading(true);
      const result = await tournamentAdminPlayoffBracketService.getPublic(tournamentAdminId);
      setData(result);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando las llaves de Playoff');
    } finally {
      if (!silent) setLoading(false);
    }
  };

  if (loading && !data) return <div className="loading">Cargando llaves de Playoff...</div>;
  if (!data) return <div className="error-message">No se encontraron datos</div>;

  const tabs: Tab[] = [{ id: 'HCP', label: 'Con HCP' }];
  if (data.scratchApplicable) {
    tabs.push({ id: 'SCRATCH', label: 'SCRATCH' });
  }

  const bracket = data.brackets.find((b) => b.scoreType === activeTab) ?? null;

  const renderSlot = (slot: TournamentAdminPlayoffBracketSlot, pair: TournamentAdminPlayoffBracketSlot[]) => {
    const opponent = pair.find((s) => s.slotId !== slot.slotId);
    const isLoser = Boolean(opponent?.isWinner);
    const statusClass = slot.isWinner ? 'winner' : isLoser ? 'loser' : slot.playerId === null ? 'empty' : '';
    return (
      <div key={slot.slotId} className={`bracket-slot-box ${statusClass}`}>
        <span className="bracket-slot-player-name">
          {slot.playerName ? formatPlayerLabel(slot.playerName, slot.playerHandicapIndex) : 'Sin asignar'}
        </span>
      </div>
    );
  };

  const renderBracket = (activeBracket: TournamentAdminPlayoffBracket) => {
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

        <div className="bracket-zoom-area">
          <TransformWrapper
            key={activeBracket.bracketId}
            initialScale={1}
            minScale={0.35}
            maxScale={2.5}
            limitToBounds={false}
            centerOnInit={false}
            wheel={{ disabled: true }}
            doubleClick={{ disabled: true }}
            onTransform={(_, state) => setZoomScale(state.scale)}
          >
            {({ zoomIn, zoomOut, resetTransform }) => (
              <>
                <div className="bracket-zoom-toolbar">
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
                <TransformComponent wrapperClass="bracket-transform-wrapper" contentClass="bracket-transform-content">
                  <div className="bracket-columns">
                    {activeBracket.rounds.map((round) => {
                      const isLast = round.roundNumber === activeBracket.rounds.length;
                      const pairs: TournamentAdminPlayoffBracketSlot[][] = [];
                      for (let i = 0; i < round.slots.length; i += 2) {
                        pairs.push(round.slots.slice(i, i + 2));
                      }
                      return (
                        <div key={round.roundNumber} className="bracket-round">
                          <div className="bracket-round-title">{round.roundName}</div>
                          <div className="bracket-round-body">
                            {pairs.map((pair, pairIdx) => (
                              <div key={pairIdx} className={`bracket-match-pair ${!isLast ? 'has-connector' : ''}`}>
                                {pair.map((slot) => renderSlot(slot, pair))}
                              </div>
                            ))}
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
      </>
    );
  };

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="header-actions">
          <button type="button" onClick={() => void loadData()} className="btn-refresh" disabled={loading}>
            ⟳ Actualizar
          </button>
        </div>
        <div className="tournament-info">
          <h1>Llaves de Playoff</h1>
          <span className="final-badge">RESULTADOS PÚBLICOS</span>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <Tabs tabs={tabs} activeTab={activeTab} onTabChange={(tabId) => setActiveTab(tabId as PlayoffScoreType)} />

      {!bracket ? (
        <div className="brackets-generate-panel">
          <p>Todavía no se generó la llave {activeTab === 'HCP' ? 'Con HCP' : 'SCRATCH'} de este torneo.</p>
        </div>
      ) : (
        renderBracket(bracket)
      )}

      <div className="update-info">
        <span className="live-indicator"></span>
        <span>Actualización automática cada 100 segundos</span>
      </div>
    </div>
  );
};

export default PublicTournamentAdminBracketsPage;
