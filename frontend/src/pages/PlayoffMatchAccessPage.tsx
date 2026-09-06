import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { tournamentAdminPlayoffMatchService } from '../services/tournamentAdminPlayoffMatchService';
import Modal from '../components/Modal';
import '../components/Form.css';
import './TournamentAccessPage.css';

const PlayoffMatchAccessPage = () => {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();

  const [matricula, setMatricula] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [modalMessage, setModalMessage] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!code) return;
    if (!matricula.trim()) {
      setError('Por favor ingrese su número de matrícula');
      return;
    }

    try {
      setSubmitting(true);
      setError('');
      const response = await tournamentAdminPlayoffMatchService.access(code, matricula.trim());
      navigate(`/playoff-match/${code}/${response.matchId}/scorecard`, { state: { matricula: matricula.trim() } });
    } catch (err: any) {
      const message =
        err.response?.data?.message || 'No se encontró un partido para ese código y matrícula';
      setModalMessage(message);
      setModalOpen(true);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="tournament-access-container">
      <div className="access-card">
        <div className="tournament-header">
          <h1>Match Play — Playoff</h1>
          <p className="tournament-course">Código de ronda: {code}</p>
        </div>

        <div className="access-form">
          <h2>Acceso al Partido</h2>
          <p className="access-instruction">
            Ingresá tu número de matrícula para acceder a la tarjeta de tu partido
          </p>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="matricula">Número de Matrícula</label>
              <input
                id="matricula"
                type="text"
                value={matricula}
                onChange={(e) => setMatricula(e.target.value)}
                placeholder="Ingresá tu número de matrícula"
                required
                autoFocus
              />
            </div>

            {error && <div className="error-message">{error}</div>}

            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Validando...' : 'Acceder al partido'}
            </button>
          </form>
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

export default PlayoffMatchAccessPage;
