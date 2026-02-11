import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { inscriptionService } from '../services/inscriptionService';
import { Tournament } from '../types';
import './PublicInscriptionPage.css';

const PublicInscriptionPage = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [matricula, setMatricula] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [inscriptionData, setInscriptionData] = useState<any>(null);

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
    if (!codigo || !matricula.trim()) {
      setError('Please enter your registration number');
      return;
    }

    try {
      setSubmitting(true);
      setError('');
      setSuccess(false);

      const response = await inscriptionService.inscribePlayer(codigo, matricula);
      setInscriptionData(response);
      setSuccess(true);
      setMatricula('');
    } catch (err: any) {
      if (err.response?.status === 404) {
        setError('No se encuentra registrado en la aplicacion');
      } else {
        setError(err.response?.data?.message || 'Error during inscription');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="inscription-container">
        <div className="inscription-card">
          <div className="loading">Loading tournament...</div>
        </div>
      </div>
    );
  }

  if (!tournament) {
    return (
      <div className="inscription-container">
        <div className="inscription-card">
          <h2>Tournament Not Found</h2>
          <p>The tournament code is invalid or the tournament has been removed.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="inscription-container">
      <div className="inscription-card">
        <div className="tournament-header">
          <h1>{tournament.nombre}</h1>
          <p className="tournament-course">{tournament.courseName}</p>
          <p className="tournament-date">
            {new Date(tournament.fechaInicio).toLocaleDateString()}
            {tournament.fechaFin && ` - ${new Date(tournament.fechaFin).toLocaleDateString()}`}
          </p>
        </div>

        {success && inscriptionData ? (
          <div className="success-message">
            <div className="success-icon">✓</div>
            <h2>Inscription Successful!</h2>
            <div className="inscription-details">
              <p><strong>Player:</strong> {inscriptionData.player.nombre} {inscriptionData.player.apellido}</p>
              <p><strong>Category:</strong> {inscriptionData.categoryName}</p>
              <p><strong>Handicap Index:</strong> {inscriptionData.player.handicapIndex}</p>
              <p><strong>Club:</strong> {inscriptionData.player.clubOrigen}</p>
            </div>
            <button 
              onClick={() => {
                setSuccess(false);
                setInscriptionData(null);
              }} 
              className="btn btn-primary"
            >
              Register Another Player
            </button>
          </div>
        ) : (
          <div className="inscription-form">
            <h2>Inscripción de Torneo</h2>
            <p className="inscription-instruction">
              Ingrese su número de matrícula para inscribirse
            </p>

            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label htmlFor="matricula">Número de Matrícula (AAG)</label>
                <input
                  id="matricula"
                  type="text"
                  value={matricula}
                  onChange={(e) => setMatricula(e.target.value)}
                  placeholder="Ingrese su número de matrícula"
                  required
                  autoFocus
                />
              </div>

              {error && <div className="error-message">{error}</div>}

              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Procesando...' : 'Inscribir'}
              </button>
            </form>

            <div className="tournament-info-footer">
              <p><strong>Jugadores Inscritos:</strong> {tournament.currentInscriptos}{tournament.limiteInscriptos ? ` / ${tournament.limiteInscriptos}` : ''}</p>
              {tournament.limiteInscriptos && tournament.currentInscriptos >= tournament.limiteInscriptos && (
                <p className="full-warning">Este torneo ha alcanzado su capacidad máxima</p>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default PublicInscriptionPage;
