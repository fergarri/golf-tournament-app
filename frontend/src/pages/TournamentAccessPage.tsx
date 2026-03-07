import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { playerService } from '../services/playerService';
import { scorecardService } from '../services/scorecardService';
import { courseService } from '../services/courseService';
import { Tournament, Player, CourseTee } from '../types';
import Modal from '../components/Modal';
import { formatDateSafe } from '../utils/dateUtils';
import './TournamentAccessPage.css';

const TournamentAccessPage = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const navigate = useNavigate();
  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [matricula, setMatricula] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalMessage, setModalMessage] = useState('');
  const [inProgressModalOpen, setInProgressModalOpen] = useState(false);
  const [player, setPlayer] = useState<Player | null>(null);
  const [requiresConfig, setRequiresConfig] = useState(false);
  const [requiresTee, setRequiresTee] = useState(false);
  const [requiresHoles, setRequiresHoles] = useState(false);
  const [selectedTeeId, setSelectedTeeId] = useState('');
  const [selectedHoles, setSelectedHoles] = useState('');
  const [availableTees, setAvailableTees] = useState<CourseTee[]>([]);

  useEffect(() => {
    loadTournament();
  }, [codigo]);

  const loadTournament = async () => {
    if (!codigo) return;
    try {
      setLoading(true);
      const data = await tournamentService.getByCodigo(codigo);
      setTournament(data);
      const needsHoles = !data.cantidadHoyosJuego;
      const needsTee = !data.teeMasculinoId || !data.teeFemeninoId;
      setRequiresHoles(needsHoles);
      setRequiresTee(needsTee);
      setRequiresConfig(needsHoles || needsTee);
      setSelectedHoles(needsHoles ? '' : String(data.cantidadHoyosJuego || ''));
      setSelectedTeeId('');
      if (needsTee) {
        const tees = await courseService.getTees(data.courseId);
        setAvailableTees(tees.filter((tee: CourseTee) => tee.active));
      } else {
        setAvailableTees([]);
      }
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Torneo no encontrado');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!matricula.trim()) {
      setError('Por favor ingrese su número de matrícula');
      return;
    }

    if (tournament?.estado === "FINALIZED") {
      setError('Imposible acceder a la tarjeta. El torneo ha finalizado.');
      return;
    }

    try {
      setSubmitting(true);
      setError('');

      if (!tournament) {
        return;
      }

      let currentPlayer = player;
      if (!currentPlayer) {
        currentPlayer = await playerService.getByMatricula(matricula);
        setPlayer(currentPlayer);
      }

      if (requiresHoles && !selectedHoles) {
        setError('Debe seleccionar la cantidad de hoyos a jugar.');
        return;
      }

      const scorecard = await scorecardService.getOrCreate(tournament.id, currentPlayer.id, {
        cantidadHoyosJuego: selectedHoles ? parseInt(selectedHoles, 10) : undefined,
        teeId: selectedTeeId ? parseInt(selectedTeeId, 10) : undefined,
      });

      if (scorecard.status === 'IN_PROGRESS_EXISTS' && requiresConfig) {
        setInProgressModalOpen(true);
        return;
      }

      if (scorecard.status === 'PENDING_CONFIG') {
        const needHoles = !scorecard.cantidadHoyosJuego;
        const needTee = !scorecard.teeId;
        setRequiresConfig(needHoles || needTee);
        setRequiresHoles(needHoles);
        setRequiresTee(needTee);
        setSelectedHoles(needHoles ? selectedHoles : String(scorecard.cantidadHoyosJuego || ''));

        if (needTee) {
          if (availableTees.length === 0) {
            const tees = await courseService.getTees(tournament.courseId);
            setAvailableTees(tees.filter((tee: CourseTee) => tee.active));
          }
          if (!selectedTeeId) {
            setError('Debe seleccionar el tee de salida.');
            return;
          }
        }

        if (needHoles && !selectedHoles) {
          setError('Debe seleccionar la cantidad de hoyos a jugar.');
          return;
        }

        setError('Faltan datos para configurar la tarjeta.');
        return;
      }

      navigate(`/play/${codigo}/scorecard`, { state: { matricula } });
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || 'Jugador no encontrado o no inscrito en este torneo';
      setModalMessage(errorMessage);
      setModalOpen(true);
    } finally {
      setSubmitting(false);
    }
  };

  const resolveInProgressScorecard = async (
    action: 'CONTINUE_EXISTING' | 'START_NEW'
  ) => {
    if (!tournament || !player) {
      setInProgressModalOpen(false);
      return;
    }

    try {
      setSubmitting(true);
      setError('');

      const scorecard = await scorecardService.getOrCreate(tournament.id, player.id, {
        cantidadHoyosJuego: selectedHoles ? parseInt(selectedHoles, 10) : undefined,
        teeId: selectedTeeId ? parseInt(selectedTeeId, 10) : undefined,
        inProgressAction: action,
      });

      if (scorecard.status === 'PENDING_CONFIG') {
        const needHoles = !scorecard.cantidadHoyosJuego;
        const needTee = !scorecard.teeId;
        setRequiresConfig(needHoles || needTee);
        setRequiresHoles(needHoles);
        setRequiresTee(needTee);
        setSelectedHoles(needHoles ? selectedHoles : String(scorecard.cantidadHoyosJuego || ''));

        if (needTee && !selectedTeeId) {
          setError('Debe seleccionar el tee de salida.');
        }
        if (needHoles && !selectedHoles) {
          setError('Debe seleccionar la cantidad de hoyos a jugar.');
        }
        setInProgressModalOpen(false);
        return;
      }

      setInProgressModalOpen(false);
      navigate(`/play/${codigo}/scorecard`, { state: { matricula } });
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || 'No fue posible resolver la tarjeta en progreso';
      setModalMessage(errorMessage);
      setModalOpen(true);
      setInProgressModalOpen(false);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="tournament-access-container">
        <div className="access-card">
          <div className="loading">Cargando torneo...</div>
        </div>
      </div>
    );
  }

  if (!tournament) {
    return (
      <div className="tournament-access-container">
        <div className="access-card">
          <h2>Torneo no encontrado</h2>
          <p>El código del torneo es inválido o el torneo ha sido eliminado.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="tournament-access-container">
      <div className="access-card">
        <div className="tournament-header">
          <h1>{tournament.nombre}</h1>
          <p className="tournament-course">{tournament.courseName}</p>
          <p className="tournament-date">
            {formatDateSafe(tournament.fechaInicio)}
          </p>
        </div>

        <div className="access-form">
          <h2>Acceso de Jugador</h2>
          <p className="access-instruction">
            {!requiresConfig
              ? 'Ingrese su número de matrícula para acceder a su tarjeta'
              : 'Complete los datos faltantes para habilitar su tarjeta'}
          </p>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="matricula">Número de Matrícula</label>
              <input
                id="matricula"
                type="text"
                value={matricula}
                onChange={(e) => {
                  setMatricula(e.target.value);
                  setPlayer(null);
                }}
                placeholder="Ingrese su número de matrícula"
                required
                autoFocus
              />
            </div>

            {requiresConfig && (
              <>
                {requiresHoles && (
                  <div className="form-group">
                    <label htmlFor="cantidadHoyosJuego">Cantidad de Hoyos</label>
                    <select
                      id="cantidadHoyosJuego"
                      value={selectedHoles}
                      onChange={(e) => setSelectedHoles(e.target.value)}
                      required
                    >
                      <option value="">Seleccionar</option>
                      <option value="9">9 hoyos</option>
                      <option value="18">18 hoyos</option>
                    </select>
                  </div>
                )}

                {requiresTee && (
                  <div className="form-group">
                    <label htmlFor="teeId">Tee de Salida</label>
                    <select
                      id="teeId"
                      value={selectedTeeId}
                      onChange={(e) => setSelectedTeeId(e.target.value)}
                    >
                      <option value="">Seleccionar tee</option>
                      {availableTees.map((tee) => (
                        <option key={tee.id} value={tee.id}>
                          {tee.nombre} {tee.grupo ? `(${tee.grupo})` : ''}
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </>
            )}

            {error && <div className="error-message">{error}</div>}

            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting
                ? 'Validando...'
                : requiresConfig
                  ? 'Continuar a la tarjeta'
                  : 'Acceder a la tarjeta'}
            </button>
          </form>
        </div>

        <div className="tournament-info-footer">
          <p>Código del Torneo: <strong>{tournament.codigo}</strong></p>
          <p>Jugadores Inscritos: <strong>{tournament.currentInscriptos}</strong></p>
        </div>
      </div>

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Error"
        message={modalMessage}
        type="error"
        confirmText="Cerrar"
      />

      <Modal
        isOpen={inProgressModalOpen}
        onClose={() => setInProgressModalOpen(false)}
        title="Tarjeta en progreso encontrada"
        size="small"
        children={
          <p>
            Ya existe una tarjeta en progreso para este torneo.
            ¿Desea continuar con la tarjeta actual o iniciar una nueva?
          </p>
        }
        footer={
          <div className="modal-actions">
            <button
              type="button"
              className="modal-btn modal-btn-cancel"
              onClick={() => resolveInProgressScorecard('CONTINUE_EXISTING')}
              disabled={submitting}
            >
              Continuar actual
            </button>
            <button
              type="button"
              className="modal-btn modal-btn-primary confirm"
              onClick={() => resolveInProgressScorecard('START_NEW')}
              disabled={submitting}
            >
              Iniciar nueva
            </button>
          </div>
        }
      />
    </div>
  );
};

export default TournamentAccessPage;
