import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { playerService } from '../services/playerService';
import { Tournament } from '../types';
import './TournamentAccessPage.css';

const TournamentAccessPage = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const navigate = useNavigate();
  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [matricula, setMatricula] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

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

    try {
      setSubmitting(true);
      setError('');

      await playerService.getByMatricula(matricula);
      
      navigate(`/play/${codigo}/scorecard`, {
        state: { matricula },
      });
    } catch (err: any) {
      setError(err.response?.data?.message || 'Player not found or not inscribed in this tournament');
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
    </div>
  );
};

export default TournamentAccessPage;
