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
  const { matricula, handicapCourse } = location.state || {};

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [holes, setHoles] = useState<Hole[]>([]);
  const [scores, setScores] = useState<{ [key: number]: { propio: number | null; marcador: number | null } }>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [scorecard, setScorecard] = useState<Scorecard | null>(null);
  const [player, setPlayer] = useState<Player | null>(null);
  const [saving, setSaving] = useState(false);
  const [lastSaved, setLastSaved] = useState<Date | null>(null);
  
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
  
  // Para evitar múltiples guardados simultáneos
  const saveTimeoutRef = useRef<number | null>(null);

  useEffect(() => {
    if (!matricula) {
      setError('Invalid access. Please enter your registration number.');
      setLoading(false);
      return;
    }
    if (!handicapCourse) {
      setError('Invalid access. Please enter your handicap course.');
      setLoading(false);
      return;
    }
    loadData();
  }, [codigo, matricula, handicapCourse]);

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

      // Cargar o crear scorecard del backend
      let scorecardData: Scorecard | null = null;
      try {
        scorecardData = await scorecardService.getOrCreate(tournamentData.id, playerData.id, handicapCourse);
        setScorecard(scorecardData);
      } catch (err) {
        console.error('Error loading scorecard:', err);
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
            console.error('Error parsing localStorage data:', err);
          }
        }
      }

      setScores(initialScores);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading tournament data');
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
      console.error('Error saving score:', err);
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

  const deliverScorecardAction = async () => {
    try {
      setLoading(true);
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
        err.response?.data?.message || 'Error delivering scorecard. Please try again.',
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
        'Scorecard not loaded. Please try refreshing the page.',
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
        <div className="loading">Loading scorecard...</div>
      </div>
    );
  }

  if (error || !tournament) {
    return (
      <div className="scorecard-container">
        <div className="error-card">
          <h2>Error</h2>
          <p>{error || 'Tournament not found'}</p>
        </div>
      </div>
    );
  }

  const totalPropio = getTotalScore('propio');
  const totalMarcador = getTotalScore('marcador');
  const totalPar = getTotalPar();
  const scoreNeto = getScoreNeto();

  return (
    <div className="scorecard-container">
      <div className="scorecard-header">
        <h1>{tournament.nombre}</h1>
        <p className="course-name">{tournament.courseName}</p>
        <p className="tournament-date">{new Date(tournament.fechaInicio + "T00:00:00").toLocaleDateString("es-AR", {day: "2-digit", month: "2-digit", year: "numeric",})}</p>
        <p className="player-matricula">
          Registration: <strong>{matricula}</strong>
          {player && <span> - {player.nombre} {player.apellido}</span>}
        </p>
        <p className="player-matricula">
          Handicap Course: <strong>{handicapCourse}</strong>
        </p>
        {scorecard?.delivered && (
          <div className="delivered-badge">
            Scorecard Delivered ✓
          </div>
        )}
        {!scorecard?.delivered && (
          <div className="auto-save-indicator">
            {saving ? (
              <span className="saving">Saving...</span>
            ) : lastSaved ? (
              <span className="saved">Saved at {lastSaved.toLocaleTimeString()}</span>
            ) : (
              <span className="auto-save">Auto-save enabled</span>
            )}
          </div>
        )}
      </div>

      <div className="scorecard-wrapper">
        <div className="scorecard-table-container">
          <table className="scorecard-table">
            <thead>
              <tr className="header-row">
                <th className="sticky-col">HOLE</th>
                {holes.map((hole) => (
                  <th key={hole.numeroHoyo}>{hole.numeroHoyo}</th>
                ))}
                <th className="total-col">GROSS</th>
                <th className="total-col">NETO</th>
              </tr>
            </thead>
            <tbody>
              <tr className="par-row">
                <td className="sticky-col label-cell">PAR</td>
                {holes.map((hole) => (
                  <td key={hole.numeroHoyo} className="par-cell">{hole.par}</td>
                ))}
                <td className="total-cell">{totalPar}</td>
                <td className="total-cell"></td>
              </tr>
              <tr className="handicap-row">
                <td className="sticky-col label-cell">HCP</td>
                {holes.map((hole) => (
                  <td key={hole.numeroHoyo} className="hcp-cell">{hole.handicap}</td>
                ))}
                <td></td>
                <td></td>
              </tr>
              <tr className="score-row player-row">
                <td className="sticky-col label-cell player-label">YOU</td>
                {holes.map((hole) => (
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
                <td className="total-cell score-total">{totalPropio || '-'}</td>
                <td className="total-cell score-total neto-cell">
                  {getScoreNeto() !== null ? getScoreNeto() : '-'}
                </td>
              </tr>
              <tr className="score-row marker-row">
                <td className="sticky-col label-cell marker-label">MARKER</td>
                {holes.map((hole) => (
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
            <span>To Par:</span>
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
          {scorecard?.delivered ? 'Already Delivered' : 'Deliver Scorecard'}
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
    </div>
  );
};

export default TournamentScorecardPage;
