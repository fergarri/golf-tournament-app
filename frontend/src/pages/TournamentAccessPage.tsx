import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { playerService } from '../services/playerService';
import { scorecardService } from '../services/scorecardService';
import { Tournament } from '../types';
import Modal from '../components/Modal';
import './TournamentAccessPage.css';

const TournamentAccessPage = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const navigate = useNavigate();
  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [matricula, setMatricula] = useState('');
  const [handicapCourse, setHandicapCourse] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalMessage, setModalMessage] = useState('');

  useEffect(() => {
    loadTournament();
  }, [codigo]);

  const loadTournament = async () => {
    if (!codigo) return;
    try {
      setLoading(true);
      const data = await tournamentService.getByCodigo(codigo);
      setTournament(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Tournament not found');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!matricula.trim()) {
      setError('Please enter your registration number');
      return;
    }

    if (!handicapCourse.trim()) {
      setError('Please enter your handicap course');
      return;
    }

    const handicapValue = parseFloat(handicapCourse);
    if (isNaN(handicapValue) || handicapValue < 0 || handicapValue > 54) {
      setError('Handicap course must be a number between 0 and 54');
      return;
    }

    try {
      setSubmitting(true);
      setError('');

      // Validar que el jugador exista
      const player = await playerService.getByMatricula(matricula);
      
      // Validar que el jugador esté inscrito en el torneo
      if (tournament) {
        await scorecardService.getOrCreate(tournament.id, player.id, handicapValue);
      }
      
      // Si todo está bien, navegar a la scorecard
      navigate(`/play/${codigo}/scorecard`, {
        state: { matricula, handicapCourse: handicapValue },
      });
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || 'Player not found or not inscribed in this tournament';
      setModalMessage(errorMessage);
      setModalOpen(true);
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="tournament-access-container">
        <div className="access-card">
          <div className="loading">Loading tournament...</div>
        </div>
      </div>
    );
  }

  if (!tournament) {
    return (
      <div className="tournament-access-container">
        <div className="access-card">
          <h2>Tournament Not Found</h2>
          <p>The tournament code is invalid or the tournament has been removed.</p>
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
            {new Date(tournament.fechaInicio).toLocaleDateString()}
          </p>
        </div>

        <div className="access-form">
          <h2>Player Access</h2>
          <p className="access-instruction">
            Enter your registration number to access your scorecard
          </p>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="matricula">Registration Number</label>
              <input
                id="matricula"
                type="text"
                value={matricula}
                onChange={(e) => setMatricula(e.target.value)}
                placeholder="Enter your registration number"
                required
                autoFocus
              />
            </div>

            <div className="form-group">
              <label htmlFor="handicapCourse">Handicap Course</label>
              <input
                id="handicapCourse"
                type="number"
                step="0.01"
                min="0"
                max="54"
                value={handicapCourse}
                onChange={(e) => setHandicapCourse(e.target.value)}
                placeholder="Enter your handicap course (e.g., 18.50)"
                required
              />
            </div>

            {error && <div className="error-message">{error}</div>}

            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Validating...' : 'Access Scorecard'}
            </button>
          </form>
        </div>

        <div className="tournament-info-footer">
          <p>Tournament Code: <strong>{tournament.codigo}</strong></p>
          <p>Inscribed Players: <strong>{tournament.currentInscriptos}</strong></p>
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
    </div>
  );
};

export default TournamentAccessPage;
