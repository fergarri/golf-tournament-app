import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { Tournament } from '../types';
import Table, { TableAction } from '../components/Table';
import Modal from '../components/Modal';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';
import './DashboardPage.css';

const DashboardPage = () => {
  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [selectedTournament, setSelectedTournament] = useState<Tournament | null>(null);
  const navigate = useNavigate();

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

  useEffect(() => {
    loadTournaments();
  }, []);

  const loadTournaments = async () => {
    try {
      setLoading(true);
      const data = await tournamentService.getAll();
      const todayMidnight = new Date();
      const activeTournaments = data.filter(t => {
        const endDate = t.fechaFin ? new Date(t.fechaFin + 'T00:00:00') : new Date(t.fechaInicio + 'T00:00:00');
        todayMidnight.setHours(0, 0, 0, 0);
        return endDate >= todayMidnight;
      });
      setTournaments(activeTournaments);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando torneos');
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
      setError(err.response?.data?.message || 'Error iniciando torneo');
    }
  };

  const handleViewLeaderboard = (tournament: Tournament) => {
    navigate(`/tournaments/${tournament.id}/leaderboard`);
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

  const finalizeTournamentAction = async (tournament: Tournament) => {
    try {
      await tournamentService.finalize(tournament.id);
      loadTournaments();
      navigate(`/tournaments/${tournament.id}/leaderboard?final=true`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error finalizando torneo');
    }
  };

  const handleFinalizeTournament = (tournament: Tournament) => {
    showModal(
      'Finalizar Torneo',
      `¿Estás seguro de querer finalizar ${tournament.nombre}? Esto cerrará el torneo.`,
      'confirm',
      () => finalizeTournamentAction(tournament)
    );
  };

  const copyLink = (link: string) => {
    navigator.clipboard.writeText(link);
    showModal(
      'Éxito',
      'Link copiado al portapapeles!',
      'success'
    );
  };

  const getPlayLink = (codigo: string) => {
    return `${window.location.origin}/play/${codigo}`;
  };

  const columns = [
    { header: 'Nombre del Torneo', accessor: 'nombre' as keyof Tournament, width: '25%' },
    { header: 'Campo', accessor: 'courseName' as keyof Tournament, width: '20%' },
    { 
      header: 'Fecha de Inicio', 
      accessor: (row: Tournament) => formatDateSafe(row.fechaInicio),
      width: '12%'
    },
    {
      header: 'Inscriptos',
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
                ({percentage}% lleno)
              </small>
            )}
          </div>
        );
      },
      width: '12%'
    },
    { 
      header: 'Código', 
      accessor: (row: Tournament) => (
        <span className="tournament-code">{row.codigo}</span>
      ),
      width: '10%'
    },
  ];

  const dashboardActions: TableAction<Tournament>[] = [
    {
      label: 'Iniciar',
      onClick: handleStartTournament,
      variant: 'primary',
      show: (tournament) => tournament.estado === 'PENDING',
    },
    {
      label: 'Copiar Link',
      onClick: (tournament) => copyLink(getPlayLink(tournament.codigo)),
      variant: 'secondary',
      show: (tournament) => tournament.estado === 'IN_PROGRESS',
    },
    {
      label: 'Tabla de Líderes',
      onClick: handleViewLeaderboard,
      variant: 'primary',
      show: (tournament) => tournament.estado === 'IN_PROGRESS',
    },
    {
      label: 'Finalizar',
      onClick: handleFinalizeTournament,
      variant: 'danger',
      show: (tournament) => tournament.estado === 'IN_PROGRESS',
    },
    {
      label: 'Ver Resultados',
      onClick: handleViewLeaderboard,
      variant: 'primary',
      show: (tournament) => tournament.estado === 'FINALIZED',
    },
  ];

  if (loading) return <div className="loading">Cargando torneos...</div>;

  return (
    <div>
      <div className="dashboard-header">
        <div>
          <h1>Dashboard</h1>
          <p className="subtitle">Torneos Activos</p>
        </div>
        <div className="dashboard-stats">
          <div className="stat-card">
            <div className="stat-value">{tournaments.length}</div>
            <div className="stat-label">Torneos Activos</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">
              {tournaments.reduce((sum, t) => sum + t.currentInscriptos, 0)}
            </div>
            <div className="stat-label">Total de Jugadores</div>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      {tournaments.length === 0 ? (
        <div className="empty-state">
          <h2>No Torneos Activos</h2>
          <p>Crea un nuevo torneo para comenzar</p>
          <button onClick={() => navigate('/tournaments')} className="btn btn-primary">
            Ir a Torneos
          </button>
        </div>
      ) : (
        <Table 
          data={tournaments} 
          columns={columns} 
          actions={dashboardActions}
          emptyMessage="No hay torneos activos"
        />
      )}

      {selectedTournament && (
        <Modal
          isOpen={showLinkModal}
          onClose={() => {
            setShowLinkModal(false);
            setSelectedTournament(null);
          }}
          title="Torneo Iniciado"
          size="medium"
        >
          <div style={{ textAlign: 'center' }}>
            <div className="success-icon" style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>
              ✓
            </div>
            <h3 style={{ marginBottom: '1rem', color: '#2c3e50' }}>
              {selectedTournament.nombre} está ahora activo!
            </h3>
            <p style={{ color: '#7f8c8d', marginBottom: '1.5rem' }}>
              Comparte este link con los jugadores para que puedan acceder a sus tarjetas de puntuación:
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
              Copiar Link
            </button>
            <p style={{ color: '#7f8c8d', fontSize: '0.9rem' }}>
              Los jugadores necesitarán ingresar su matrícula para acceder a sus tarjetas
            </p>
          </div>
        </Modal>
      )}

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onConfirm={modalConfig.onConfirm}
        title={modalConfig.title}
        message={modalConfig.message}
        type={modalConfig.type}
        confirmText={modalConfig.type === 'confirm' ? 'Sí, Finalizar' : 'OK'}
        cancelText="Cancelar"
      />
    </div>
  );
};

export default DashboardPage;
