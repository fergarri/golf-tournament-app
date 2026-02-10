import { useState, useEffect, useRef } from 'react';
import { useParams, useLocation } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { courseService } from '../services/courseService';
import { scorecardService } from '../services/scorecardService';
import { playerService } from '../services/playerService';
import { Tournament, Hole, Scorecard, Player } from '../types';
import Modal from '../components/Modal';
import './TournamentScorecardPage.css';

const TournamentScorecardPage = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const location = useLocation();
  const { matricula, handicapCourse, teeId } = location.state || {};

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [holes, setHoles] = useState<Hole[]>([]);
  const [scores, setScores] = useState<{ [key: number]: { propio: number | null; marcador: number | null } }>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [scorecard, setScorecard] = useState<Scorecard | null>(null);
  const [player, setPlayer] = useState<Player | null>(null);
  const [saving, setSaving] = useState(false);
  const [lastSaved, setLastSaved] = useState<Date | null>(null);
  const [selectedTee, setSelectedTee] = useState<any>(null);
  
  // Modal states
  const [modalOpen, setModalOpen] = useState(false);
  const [modalConfig, setModalConfig] = useState<{
    title: string;
    message: string;
    type: 'info' | 'success' | 'warning' | 'error' | 'confirm';
    onConfirm?: () => void;
  }>({
    title: '',
    message: '',
    type: 'info',
  });

  // Marker assignment states
  const [markerModalOpen, setMarkerModalOpen] = useState(false);
  const [markerMatricula, setMarkerMatricula] = useState('');
  const [markerFound, setMarkerFound] = useState<Player | null>(null);
  const [markerStep, setMarkerStep] = useState<'search' | 'confirm'>('search');
  const [searchingMarker, setSearchingMarker] = useState(false);
  
  // Para evitar múltiples guardados simultáneos
  const saveTimeoutRef = useRef<number | null>(null);

  useEffect(() => {
    if (!matricula) {
      setError('Acceso inválido. Por favor ingrese su número de matrícula.');
      setLoading(false);
      return;
    }
    loadData();
  }, [codigo, matricula]);

  // Auto-guardar en localStorage cuando cambian los scores
  useEffect(() => {
    if (scorecard && tournament && Object.keys(scores).length > 0) {
      const storageKey = `scorecard_${tournament.id}_${matricula}`;
      localStorage.setItem(storageKey, JSON.stringify({
        scores,
        lastUpdated: new Date().toISOString(),
      }));
    }
  }, [scores, scorecard, tournament, matricula]);

  // Limpiar timeout al desmontar
  useEffect(() => {
    return () => {
      if (saveTimeoutRef.current !== null) {
        clearTimeout(saveTimeoutRef.current);
      }
    };
  }, []);

  const loadData = async () => {
    if (!codigo) return;
    try {
      setLoading(true);
      
      // Cargar datos del torneo y jugador
      const [tournamentData, playerData] = await Promise.all([
        tournamentService.getByCodigo(codigo),
        playerService.getByMatricula(matricula)
      ]);
      
      setTournament(tournamentData);
      setPlayer(playerData);

      // Cargar hoyos del campo
      const holesData = await courseService.getHoles(tournamentData.courseId);
      const sortedHoles = holesData.sort((a: Hole, b: Hole) => a.numeroHoyo - b.numeroHoyo);
      setHoles(sortedHoles);

      // Cargar información del tee seleccionado
      if (teeId) {
        const tees = await courseService.getTees(tournamentData.courseId);
        const tee = tees.find((t: any) => t.id === teeId);
        setSelectedTee(tee);
      }

      // Cargar o crear scorecard del backend
      let scorecardData: Scorecard | null = null;
      try {
        // El scorecard ya debería estar creado desde TournamentAccessPage
        // pero llamamos a getOrCreate por si acaso, pasando el teeId
        if (teeId) {
          scorecardData = await scorecardService.getOrCreate(tournamentData.id, playerData.id, teeId);
          setScorecard(scorecardData);
        } else {
          setError('Acceso inválido. Por favor ingrese nuevamente desde la página de acceso.');
        }
      } catch (err) {
        console.error('Error cargando scorecard:', err);
      }

      // Inicializar scores
      const initialScores: any = {};
      sortedHoles.forEach((hole: Hole) => {
        // Primero intentar del backend
        const holeScore = scorecardData?.holeScores.find(hs => hs.numeroHoyo === hole.numeroHoyo);
        initialScores[hole.numeroHoyo] = {
          propio: holeScore?.golpesPropio || null,
          marcador: holeScore?.golpesMarcador || null,
        };
      });

      // Si no hay datos del backend, intentar cargar de localStorage
      if (!scorecardData || scorecardData.holeScores.every(hs => !hs.golpesPropio && !hs.golpesMarcador)) {
        const storageKey = `scorecard_${tournamentData.id}_${matricula}`;
        const savedData = localStorage.getItem(storageKey);
        if (savedData) {
          try {
            const { scores: savedScores } = JSON.parse(savedData);
            Object.keys(savedScores).forEach(holeNumber => {
              const num = parseInt(holeNumber);
              if (initialScores[num]) {
                initialScores[num] = savedScores[num];
              }
            });
          } catch (err) {
            console.error('Error analizando datos en localStorage:', err);
          }
        }
      }

      setScores(initialScores);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando datos del torneo');
    } finally {
      setLoading(false);
    }
  };

  const updateScore = (holeNumber: number, type: 'propio' | 'marcador', value: string) => {
    const newScore = value ? parseInt(value) : null;
    
    setScores({
      ...scores,
      [holeNumber]: {
        ...scores[holeNumber],
        [type]: newScore,
      },
    });

    // Auto-guardar al backend con debounce
    if (newScore !== null && scorecard) {
      // Cancelar guardado previo si existe
      if (saveTimeoutRef.current !== null) {
        clearTimeout(saveTimeoutRef.current);
      }

      // Programar nuevo guardado después de 1 segundo sin cambios
      saveTimeoutRef.current = window.setTimeout(() => {
        saveScoreToBackend(holeNumber, type, newScore);
      }, 1000);
    }
  };

  const saveScoreToBackend = async (holeNumber: number, type: 'propio' | 'marcador', golpes: number) => {
    if (!scorecard) return;

    const hole = holes.find(h => h.numeroHoyo === holeNumber);
    if (!hole) return;

    try {
      setSaving(true);
      await scorecardService.updateScore(scorecard.id, {
        holeId: hole.id,
        golpes,
        tipo: type === 'propio' ? 'PROPIO' : 'MARCADOR',
      });
      setLastSaved(new Date());
    } catch (err: any) {
      console.error('Error guardando puntuación:', err);
      // No mostramos error al usuario ya que localStorage ya guardó los datos
    } finally {
      setSaving(false);
    }
  };

  const getTotalScore = (type: 'propio' | 'marcador') => {
    return holes.reduce((sum, hole) => {
      const score = scores[hole.numeroHoyo]?.[type];
      return sum + (score || 0);
    }, 0);
  };

  const getTotalPar = () => {
    return holes.reduce((sum, hole) => sum + hole.par, 0);
  };

  const getFrontNinePar = () => {
    return holes.filter(h => h.numeroHoyo <= 9).reduce((sum, hole) => sum + hole.par, 0);
  };

  const getBackNinePar = () => {
    return holes.filter(h => h.numeroHoyo > 9).reduce((sum, hole) => sum + hole.par, 0);
  };

  const getFrontNineScore = (type: 'propio' | 'marcador') => {
    return holes.filter(h => h.numeroHoyo <= 9).reduce((sum, hole) => {
      const score = scores[hole.numeroHoyo]?.[type];
      return sum + (score || 0);
    }, 0);
  };

  const getBackNineScore = (type: 'propio' | 'marcador') => {
    return holes.filter(h => h.numeroHoyo > 9).reduce((sum, hole) => {
      const score = scores[hole.numeroHoyo]?.[type];
      return sum + (score || 0);
    }, 0);
  };

  const getScoreNeto = () => {
    const totalPropio = getTotalScore('propio');
    if (!totalPropio || !scorecard?.handicapCourse) return null;
    return totalPropio - scorecard.handicapCourse;
  };

  const showModal = (
    title: string,
    message: string,
    type: 'info' | 'success' | 'warning' | 'error' | 'confirm',
    onConfirm?: () => void
  ) => {
    setModalConfig({ title, message, type, onConfirm });
    setModalOpen(true);
  };

  // Marker assignment functions
  const openMarkerModal = () => {
    if (scorecard?.delivered) {
      setTimeout(() => {
        showModal('Error', 'No se puede asignar un marcador a una tarjeta entregada', 'error');
      }, 100);
      return;
    }
    setMarkerMatricula('');
    setMarkerFound(null);
    setMarkerStep('search');
    setMarkerModalOpen(true);
  };

  const searchMarkerByMatricula = async () => {
    if (!markerMatricula.trim()) {
      setMarkerModalOpen(false);
      setTimeout(() => {
        showModal('Error', 'Por favor ingrese un número de matrícula', 'error');
      }, 200);
      return;
    }

    try {
      setSearchingMarker(true);
      const foundPlayer = await playerService.getByMatricula(markerMatricula);
      
      // Verify marker is not the same player
      if (foundPlayer.id === player?.id) {
        setMarkerModalOpen(false);
        setSearchingMarker(false);
        setTimeout(() => {
          showModal('Error', 'No se puede marcar a uno mismo', 'error');
        }, 200);
        return;
      }

      // Verify marker is inscribed in the tournament
      if (!tournament) {
        setMarkerModalOpen(false);
        setSearchingMarker(false);
        setTimeout(() => {
          showModal('Error', 'Datos del torneo no disponibles', 'error');
        }, 200);
        return;
      }

      setMarkerFound(foundPlayer);
      setMarkerStep('confirm');
    } catch (err: any) {
      setMarkerModalOpen(false);
      setSearchingMarker(false);
      setTimeout(() => {
        showModal('Error', 'Jugador no encontrado o no inscrito en este torneo', 'error');
      }, 200);
    } finally {
      setSearchingMarker(false);
    }
  };

  const confirmMarkerAssignment = async () => {
    if (!markerFound || !scorecard) return;

    try {
      const updatedScorecard = await scorecardService.assignMarker(scorecard.id, markerFound.id);
      setScorecard(updatedScorecard);
      setMarkerModalOpen(false);
      setTimeout(() => {
        showModal('Success', `${markerFound.apellido} ha sido asignado como tu marcador`, 'success');
      }, 200);
    } catch (err: any) {
      setMarkerModalOpen(false);
      setTimeout(() => {
        showModal('Error', err.response?.data?.message || 'Error asignando marcador', 'error');
      }, 200);
    }
  };

  const cancelMarkerAssignment = () => {
    setMarkerModalOpen(false);
    setMarkerMatricula('');
    setMarkerFound(null);
    setMarkerStep('search');
  };

  const deliverScorecardAction = async () => {
    try {
      setLoading(true);
      
      // PASO 1: Enviar TODOS los valores de TODOS los hoyos (doble guardado)
      const holeScoresUpdate = holes.map(hole => ({
        holeId: hole.id,
        golpesPropio: scores[hole.numeroHoyo]?.propio || undefined,
        golpesMarcador: scores[hole.numeroHoyo]?.marcador || undefined,
      }));

      await scorecardService.updateScorecard(scorecard!.id, {
        holeScores: holeScoresUpdate
      });
      
      // PASO 2: Marcar la tarjeta como entregada
      await scorecardService.deliverScorecard(scorecard!.id);
      
      // Limpiar localStorage
      if (tournament) {
        const storageKey = `scorecard_${tournament.id}_${matricula}`;
        localStorage.removeItem(storageKey);
      }
      
      showModal(
        'Exitos!',
        'Tarjeta enviada correctamente!',
        'success'
      );
      
      // Recargar para ver el estado actualizado
      await loadData();
    } catch (err: any) {
      showModal(
        'Error',
        err.response?.data?.message || 'Error entregando tarjeta. Por favor intente nuevamente.',
        'error'
      );
    } finally {
      setLoading(false);
    }
  };

  const handleDeliverScorecard = () => {
    if (!scorecard) {
      showModal(
        'Error',
        'Tarjeta no cargada. Por favor intente recargando la página.',
        'error'
      );
      return;
    }

    const hasAllScores = holes.every(
      (hole) => scores[hole.numeroHoyo]?.propio && scores[hole.numeroHoyo]?.marcador
    );

    if (!hasAllScores) {
      showModal(
        'Tarjeta incompleta',
        'Por favor complete todos los hoyos antes de enviar su tarjeta',
        'warning'
      );
      return;
    }

    showModal(
      'Confirmar Envío',
      '¿Seguro que desea enviar su tarjeta? No podrá editarla después de la entrega.',
      'confirm',
      deliverScorecardAction
    );
  };

  if (loading) {
    return (
      <div className="scorecard-container">
        <div className="loading">Cargando tarjeta...</div>
      </div>
    );
  }

  if (error || !tournament) {
    return (
      <div className="scorecard-container">
        <div className="error-card">
          <h2>Error</h2>
          <p>{error || 'Torneo no encontrado'}</p>
        </div>
      </div>
    );
  }

  const totalPropio = getTotalScore('propio');
  const totalMarcador = getTotalScore('marcador');
  const totalPar = getTotalPar();
  const scoreNeto = getScoreNeto();
  const hasBackNine = holes.some(h => h.numeroHoyo > 9);

  return (
    <div className="scorecard-container">
      <div className="scorecard-header">
        <h1>{tournament.nombre}</h1>
        <p className="course-name">{tournament.courseName}</p>
        <p className="tournament-date">{new Date(tournament.fechaInicio + "T00:00:00").toLocaleDateString("es-AR", {day: "2-digit", month: "2-digit", year: "numeric",})}</p>
        <p className="player-matricula">
          Matrícula: <strong>{matricula}</strong>
          {player && <span> - {player.nombre} {player.apellido}</span>}
        </p>
        <p className="player-matricula">
          Handicap Course: <strong>{handicapCourse}</strong>
        </p>
        {scorecard?.delivered && (
          <div className="delivered-badge">
            Tarjeta entregada ✓
          </div>
        )}
        {!scorecard?.delivered && (
          <div className="auto-save-indicator">
            {saving ? (
              <span className="saving">Guardando...</span>
            ) : lastSaved ? (
              <span className="saved">Saved at {lastSaved.toLocaleTimeString()}</span>
            ) : (
              <span className="auto-save">Auto-guardado activado</span>
            )}
          </div>
        )}
      </div>

      <div className="scorecard-wrapper">
        <div className="scorecard-table-container">
          <table className="scorecard-table">
            <thead>
              <tr className="header-row">
                <th className="sticky-col">{selectedTee.grupo} - {selectedTee.nombre}</th>
                {holes.filter(h => h.numeroHoyo <= 9).map((hole) => (
                    <td key={hole.numeroHoyo} className="distance-cell">
                      {hole.distancesByTee?.[teeId] || '-'}
                    </td>
                  ))}
                  {hasBackNine && <td className="subtotal-cell distance-cell"></td>}
                  {hasBackNine && holes.filter(h => h.numeroHoyo > 9).map((hole) => (
                    <td key={hole.numeroHoyo} className="distance-cell">
                      {hole.distancesByTee?.[teeId] || '-'}
                    </td>
                  ))}
                {hasBackNine && <th className="subtotal-col distance-cell"></th>}
                <th className="total-col"></th>
                <th className="total-col"></th>
              </tr>
            </thead>
            <tbody>
              {/* Fila 1: HCP */}
              <tr className="handicap-row">
                <td className="sticky-col label-cell">HCP</td>
                {holes.filter(h => h.numeroHoyo <= 9).map((hole) => (
                  <td key={hole.numeroHoyo} className="hcp-cell">{hole.handicap}</td>
                ))}
                {hasBackNine && <td className="subtotal-cell hcp-cell"></td>}
                {hasBackNine && holes.filter(h => h.numeroHoyo > 9).map((hole) => (
                  <td key={hole.numeroHoyo} className="hcp-cell">{hole.handicap}</td>
                ))}
                {hasBackNine && <td className="subtotal-cell hcp-cell"></td>}
                <td></td>
                <td></td>
              </tr>
              {/* Fila 2: PAR */}
              <tr className="par-row">
                <td className="sticky-col label-cell">PAR</td>
                {holes.filter(h => h.numeroHoyo <= 9).map((hole) => (
                  <td key={hole.numeroHoyo} className="par-cell">{hole.par}</td>
                ))}
                {hasBackNine && <td className="subtotal-cell par-cell">{getFrontNinePar()}</td>}
                {hasBackNine && holes.filter(h => h.numeroHoyo > 9).map((hole) => (
                  <td key={hole.numeroHoyo} className="par-cell">{hole.par}</td>
                ))}
                {hasBackNine && <td className="subtotal-cell par-cell">{getBackNinePar()}</td>}
                <td className="total-cell final-total-cell">{totalPar}</td>
                <td className="total-cell final-total-cell"></td>
              </tr>
              {/* Fila 3: HOYO */}
              <tr className="hoyo-row">
                <td className="sticky-col label-cell">HOYO</td>
                {holes.filter(h => h.numeroHoyo <= 9).map((hole) => (
                  <td key={hole.numeroHoyo} className="hoyo-cell">{hole.numeroHoyo}</td>
                ))}
                {hasBackNine && <td className="subtotal-cell hoyo-cell">IDA</td>}
                {hasBackNine && holes.filter(h => h.numeroHoyo > 9).map((hole) => (
                  <td key={hole.numeroHoyo} className="hoyo-cell">{hole.numeroHoyo}</td>
                ))}
                {hasBackNine && <td className="subtotal-cell hoyo-cell">VTA</td>}
                <td className="total-cell final-total-cell">GROSS</td>
                <td className="total-cell final-total-cell">NETO</td>
              </tr>
              {/* Fila 4: TU (jugador) */}
              <tr className="score-row player-row">
                <td className="sticky-col label-cell player-label">TU</td>
                {holes.filter(h => h.numeroHoyo <= 9).map((hole) => (
                  <td key={hole.numeroHoyo}>
                    <input
                      type="number"
                      min="1"
                      max="15"
                      value={scores[hole.numeroHoyo]?.propio || ''}
                      onChange={(e) => updateScore(hole.numeroHoyo, 'propio', e.target.value)}
                      className="score-input"
                      placeholder="-"
                      disabled={scorecard?.delivered || false}
                    />
                  </td>
                ))}
                {hasBackNine && <td className="subtotal-cell score-total">{getFrontNineScore('propio') || '-'}</td>}
                {hasBackNine && holes.filter(h => h.numeroHoyo > 9).map((hole) => (
                  <td key={hole.numeroHoyo}>
                    <input
                      type="number"
                      min="1"
                      max="15"
                      value={scores[hole.numeroHoyo]?.propio || ''}
                      onChange={(e) => updateScore(hole.numeroHoyo, 'propio', e.target.value)}
                      className="score-input"
                      placeholder="-"
                      disabled={scorecard?.delivered || false}
                    />
                  </td>
                ))}
                {hasBackNine && <td className="subtotal-cell score-total">{getBackNineScore('propio') || '-'}</td>}
                <td className="total-cell score-total">{totalPropio || '-'}</td>
                <td className="total-cell score-total neto-cell">
                  {getScoreNeto() !== null ? getScoreNeto() : '-'}
                </td>
              </tr>
              {/* Fila 5: MARCADOR */}
              <tr className="score-row marker-row">
                <td 
                  className="sticky-col label-cell marker-label clickable"
                  onClick={openMarkerModal}
                  style={{ cursor: 'pointer' }}
                  title="Click to assign marker"
                >
                  {scorecard?.markerName ? scorecard.markerName : 'MARCADOR'}
                </td>
                {holes.filter(h => h.numeroHoyo <= 9).map((hole) => (
                  <td key={hole.numeroHoyo}>
                    <input
                      type="number"
                      min="1"
                      max="15"
                      value={scores[hole.numeroHoyo]?.marcador || ''}
                      onChange={(e) => updateScore(hole.numeroHoyo, 'marcador', e.target.value)}
                      className="score-input"
                      placeholder="-"
                      disabled={scorecard?.delivered || false}
                    />
                  </td>
                ))}
                {hasBackNine && <td className="subtotal-cell score-total">{getFrontNineScore('marcador') || '-'}</td>}
                {hasBackNine && holes.filter(h => h.numeroHoyo > 9).map((hole) => (
                  <td key={hole.numeroHoyo}>
                    <input
                      type="number"
                      min="1"
                      max="15"
                      value={scores[hole.numeroHoyo]?.marcador || ''}
                      onChange={(e) => updateScore(hole.numeroHoyo, 'marcador', e.target.value)}
                      className="score-input"
                      placeholder="-"
                      disabled={scorecard?.delivered || false}
                    />
                  </td>
                ))}
                {hasBackNine && <td className="subtotal-cell score-total">{getBackNineScore('marcador') || '-'}</td>}
                <td className="total-cell score-total">{totalMarcador || '-'}</td>
                <td></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div className="scorecard-actions">
        <div className="score-summary">
          <div className="summary-item">
            <span>Score Gross:</span>
            <strong>{totalPropio || '-'}</strong>
          </div>
          <div className="summary-item">
            <span>Score Neto:</span>
            <strong className="neto-score">{getScoreNeto() !== null ? getScoreNeto() : '-'}</strong>
          </div>
          <div className="summary-item">
            <span>Para Par:</span>
            <strong className={scoreNeto !== null && scoreNeto > totalPar ? 'over-par' : scoreNeto !== null && scoreNeto < totalPar ? 'under-par' : ''}>
              {scoreNeto !== null ? (scoreNeto === totalPar ? 'E' : scoreNeto > totalPar ? `+${scoreNeto - totalPar}` : `${scoreNeto - totalPar}`) : '-'}
            </strong>
          </div>
        </div>
        <button 
          onClick={handleDeliverScorecard} 
          className="btn btn-deliver"
          disabled={scorecard?.delivered || false}
        >
          {scorecard?.delivered ? 'Ya entregada' : 'Entregar tarjeta'}
        </button>
      </div>

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onConfirm={modalConfig.onConfirm}
        title={modalConfig.title}
        message={modalConfig.message}
        type={modalConfig.type}
        confirmText={modalConfig.type === 'confirm' ? 'Si, Enviar' : 'OK'}
        cancelText="Cancelar"
      />

      {/* Marker Assignment Modal */}
      <Modal
        isOpen={markerModalOpen}
        onClose={cancelMarkerAssignment}
        title={markerStep === 'search' ? 'Asignar Marcador' : 'Confirmar Marcador'}
        size="medium"
      >
        {markerStep === 'search' ? (
          <div style={{ padding: '1rem 0' }}>
            <div style={{ marginBottom: '1.5rem' }}>
              <label 
                htmlFor="markerMatricula" 
                style={{ 
                  display: 'block', 
                  marginBottom: '0.5rem',
                  fontWeight: 600,
                  color: '#2c3e50'
                }}
              >
                Número de Matrícula del Marcador
              </label>
              <input
                id="markerMatricula"
                type="text"
                value={markerMatricula}
                onChange={(e) => setMarkerMatricula(e.target.value)}
                placeholder="Matrícula Marcador"
                onKeyPress={(e) => e.key === 'Enter' && searchMarkerByMatricula()}
                autoFocus
                style={{
                  width: '100%',
                  padding: '0.75rem',
                  fontSize: '1rem',
                  border: '2px solid #dee2e6',
                  borderRadius: '8px',
                  outline: 'none',
                  transition: 'border-color 0.2s'
                }}
                onFocus={(e) => e.target.style.borderColor = '#3498db'}
                onBlur={(e) => e.target.style.borderColor = '#dee2e6'}
              />
            </div>
            <button
              onClick={searchMarkerByMatricula}
              disabled={searchingMarker}
              style={{
                width: '100%',
                padding: '0.75rem',
                background: 'linear-gradient(135deg, #3498db 0%, #2980b9 100%)',
                color: 'white',
                border: 'none',
                borderRadius: '8px',
                fontSize: '1rem',
                fontWeight: 600,
                cursor: searchingMarker ? 'not-allowed' : 'pointer',
                opacity: searchingMarker ? 0.7 : 1,
                transition: 'all 0.2s'
              }}
            >
              {searchingMarker ? 'Buscando...' : 'Buscar'}
            </button>
          </div>
        ) : (
          <div style={{ padding: '1rem 0', textAlign: 'center' }}>
            <div style={{ 
              fontSize: '3rem', 
              color: '#3498db', 
              marginBottom: '1rem' 
            }}>
              👤
            </div>
            <p style={{ 
              fontSize: '1.1rem', 
              color: '#2c3e50',
              marginBottom: '2rem',
              lineHeight: 1.6
            }}>
              Confirmar jugador <strong>{markerFound?.nombre} {markerFound?.apellido}</strong> como tu marcador?
            </p>
            <div style={{ 
              display: 'flex', 
              gap: '0.75rem',
              justifyContent: 'center'
            }}>
              <button
                onClick={cancelMarkerAssignment}
                style={{
                  padding: '0.75rem 2rem',
                  backgroundColor: '#ecf0f1',
                  color: '#5a6c7d',
                  border: 'none',
                  borderRadius: '8px',
                  fontSize: '1rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  minWidth: '120px',
                  transition: 'all 0.2s'
                }}
              >
                Cancelar
              </button>
              <button
                onClick={confirmMarkerAssignment}
                style={{
                  padding: '0.75rem 2rem',
                  background: 'linear-gradient(135deg, #3498db 0%, #2980b9 100%)',
                  color: 'white',
                  border: 'none',
                  borderRadius: '8px',
                  fontSize: '1rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  minWidth: '120px',
                  transition: 'all 0.2s'
                }}
              >
                Confirmar
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default TournamentScorecardPage;
