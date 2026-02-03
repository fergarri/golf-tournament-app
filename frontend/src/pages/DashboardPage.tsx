import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { Tournament } from '../types';
import Table from '../components/Table';
import Modal from '../components/Modal';
import '../components/Form.css';
import './DashboardPage.css';

const DashboardPage = () => {
  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [selectedTournament, setSelectedTournament] = useState<Tournament | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    loadTournaments();
  }, []);

  const loadTournaments = async () => {
    try {
      setLoading(true);
      const data = await tournamentService.getAll();
      const activeTournaments = data.filter(t => {
        const endDate = t.fechaFin ? new Date(t.fechaFin) : new Date(t.fechaInicio);
        return endDate > new Date();
      });
      setTournaments(activeTournaments);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading tournaments');
    } finally {
      setLoading(false);
    }
  };

  const handleStartTournament = async (tournament: Tournament) => {
    try {
      await tournamentService.start(tournament.id);
      setSelectedTournament(tournament);
      setShowLinkModal(true);
      loadTournaments();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error starting tournament');
    }
  };

  const handleViewLeaderboard = (tournament: Tournament) => {
    navigate(`/tournaments/${tournament.id}/leaderboard`);
  };

  const handleFinalizeTournament = async (tournament: Tournament) => {
    if (!confirm(`Are you sure you want to finalize ${tournament.nombre}? This will close the tournament.`)) {
      return;
    }
    try {
      await tournamentService.finalize(tournament.id);
      loadTournaments();
      navigate(`/tournaments/${tournament.id}/leaderboard?final=true`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error finalizing tournament');
    }
  };

  const copyLink = (link: string) => {
    navigator.clipboard.writeText(link);
    alert('Link copied to clipboard!');
  };

  const getPlayLink = (codigo: string) => {
    return `${window.location.origin}/play/${codigo}`;
  };

  const columns = [
    { header: 'Tournament Name', accessor: 'nombre' as keyof Tournament, width: '25%' },
    { header: 'Course', accessor: 'courseName' as keyof Tournament, width: '20%' },
    { 
      header: 'Start Date', 
      accessor: (row: Tournament) => new Date(row.fechaInicio).toLocaleDateString(),
      width: '12%'
    },
    {
      header: 'Inscribed',
      accessor: (row: Tournament) => {
        const limit = row.limiteInscriptos ? `/${row.limiteInscriptos}` : '';
        const percentage = row.limiteInscriptos 
          ? Math.round((row.currentInscriptos / row.limiteInscriptos) * 100)
          : null;
        return (
          <div>
            <div>{row.currentInscriptos}{limit}</div>
            {percentage !== null && percentage >= 80 && (
              <small style={{ color: percentage >= 100 ? '#e74c3c' : '#f39c12' }}>
                ({percentage}% full)
              </small>
            )}
          </div>
        );
      },
      width: '12%'
    },
    { 
      header: 'Code', 
      accessor: (row: Tournament) => (
        <span className="tournament-code">{row.codigo}</span>
      ),
      width: '10%'
    },
  ];

  const customActions = (tournament: Tournament) => (
    <>
      {tournament.estado === 'PENDING' && (
        <button 
          onClick={() => handleStartTournament(tournament)} 
          className="btn btn-success"
          style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem', backgroundColor: '#27ae60' }}
        >
          Start
        </button>
      )}
      {tournament.estado === 'IN_PROGRESS' && (
        <>
          <button 
            onClick={() => copyLink(getPlayLink(tournament.codigo))} 
            className="btn btn-secondary"
            style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem', backgroundColor: '#9b59b6' }}
          >
            Copy Link
          </button>
          <button 
            onClick={() => handleViewLeaderboard(tournament)} 
            className="btn btn-secondary"
            style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem' }}
          >
            Leaderboard
          </button>
          <button 
            onClick={() => handleFinalizeTournament(tournament)} 
            className="btn btn-danger"
            style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem' }}
          >
            Finalize
          </button>
        </>
      )}
      {tournament.estado === 'FINALIZED' && (
        <button 
          onClick={() => handleViewLeaderboard(tournament)} 
          className="btn btn-secondary"
          style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem' }}
        >
          View Results
        </button>
      )}
    </>
  );

  if (loading) return <div className="loading">Loading tournaments...</div>;

  return (
    <div>
      <div className="dashboard-header">
        <div>
          <h1>Dashboard</h1>
          <p className="subtitle">Active Tournaments</p>
        </div>
        <div className="dashboard-stats">
          <div className="stat-card">
            <div className="stat-value">{tournaments.length}</div>
            <div className="stat-label">Active Tournaments</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">
              {tournaments.reduce((sum, t) => sum + t.currentInscriptos, 0)}
            </div>
            <div className="stat-label">Total Players</div>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      {tournaments.length === 0 ? (
        <div className="empty-state">
          <h2>No Active Tournaments</h2>
          <p>Create a new tournament to get started</p>
          <button onClick={() => navigate('/tournaments')} className="btn btn-primary">
            Go to Tournaments
          </button>
        </div>
      ) : (
        <Table 
          data={tournaments} 
          columns={columns} 
          customActions={customActions}
          emptyMessage="No active tournaments"
        />
      )}

      {selectedTournament && (
        <Modal
          isOpen={showLinkModal}
          onClose={() => {
            setShowLinkModal(false);
            setSelectedTournament(null);
          }}
          title="Tournament Started"
          size="medium"
        >
          <div style={{ textAlign: 'center' }}>
            <div className="success-icon" style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>
              ✓
            </div>
            <h3 style={{ marginBottom: '1rem', color: '#2c3e50' }}>
              {selectedTournament.nombre} is now active!
            </h3>
            <p style={{ color: '#7f8c8d', marginBottom: '1.5rem' }}>
              Share this link with players so they can access their scorecards:
            </p>
            <div style={{ 
              backgroundColor: '#f8f9fa', 
              padding: '1rem', 
              borderRadius: '6px', 
              marginBottom: '1rem',
              wordBreak: 'break-all'
            }}>
              <code style={{ fontSize: '1rem', color: '#2c3e50' }}>
                {getPlayLink(selectedTournament.codigo)}
              </code>
            </div>
            <button 
              onClick={() => copyLink(getPlayLink(selectedTournament.codigo))} 
              className="btn btn-primary"
              style={{ marginBottom: '1rem' }}
            >
              Copy Link to Clipboard
            </button>
            <p style={{ color: '#7f8c8d', fontSize: '0.9rem' }}>
              Players will need to enter their registration number to access their scorecard
            </p>
          </div>
        </Modal>
      )}
    </div>
  );
};

export default DashboardPage;
