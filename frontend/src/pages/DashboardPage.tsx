import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { Tournament } from '../types';
import Table, { TableAction } from '../components/Table';
import Modal from '../components/Modal';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { Badge } from '../components/ui/badge';
import { formatDateSafe } from '../utils/dateUtils';
import { Trophy, Users, Link2, Copy, CheckCircle2 } from 'lucide-react';
import '../components/Form.css';

const statusBadge = (estado: string) => {
  if (estado === 'IN_PROGRESS') return <Badge variant="success">En curso</Badge>;
  if (estado === 'PENDING') return <Badge variant="warning">Pendiente</Badge>;
  if (estado === 'FINALIZED') return <Badge variant="secondary">Finalizado</Badge>;
  return <Badge variant="outline">{estado}</Badge>;
};

const DashboardPage = () => {
  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [selectedTournament, setSelectedTournament] = useState<Tournament | null>(null);
  const [copied, setCopied] = useState(false);
  const navigate = useNavigate();

  const [modalOpen, setModalOpen] = useState(false);
  const [modalConfig, setModalConfig] = useState<{
    title: string;
    message: string;
    type: 'info' | 'success' | 'warning' | 'error' | 'confirm';
    onConfirm?: () => void;
  }>({ title: '', message: '', type: 'info' });

  useEffect(() => { loadTournaments(); }, []);

  const loadTournaments = async () => {
    try {
      setLoading(true);
      const data = await tournamentService.getAll();
      setTournaments(data.filter(t => t.estado === 'IN_PROGRESS'));
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
    if (tournament.tipo === 'FRUTALES') {
      navigate(`/tournaments/${tournament.id}/frutales-leaderboard`);
    } else {
      navigate(`/tournaments/${tournament.id}/leaderboard`);
    }
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

  const getPlayLink = (codigo: string) => `${window.location.origin}/play/${codigo}`;

  const copyLink = (link: string) => {
    navigator.clipboard.writeText(link);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const columns = [
    {
      header: 'Torneo',
      accessor: (row: Tournament) => (
        <Link
          to={row.tipo === 'FRUTALES'
            ? `/tournaments/${row.id}/frutales-leaderboard`
            : `/tournaments/${row.id}/leaderboard`}
          className="tournament-name-link"
        >
          {row.nombre}
        </Link>
      ),
      sortValue: (row: Tournament) => row.nombre,
      width: '28%',
    },
    { header: 'Campo', accessor: 'courseName' as keyof Tournament, width: '20%' },
    {
      header: 'Fecha de Inicio',
      accessor: (row: Tournament) => formatDateSafe(row.fechaInicio),
      sortValue: (row: Tournament) => row.fechaInicio ?? '',
      width: '13%'
    },
    {
      header: 'Estado',
      accessor: (row: Tournament) => statusBadge(row.estado),
      sortValue: (row: Tournament) => row.estado,
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
            <span className="font-medium">{row.currentInscriptos}{limit}</span>
            {percentage !== null && percentage >= 80 && (
              <span className={`ml-2 text-xs font-medium ${percentage >= 100 ? 'text-red-500' : 'text-amber-500'}`}>
                ({percentage}% lleno)
              </span>
            )}
          </div>
        );
      },
      sortValue: (row: Tournament) => row.currentInscriptos,
      width: '12%'
    },
    {
      header: 'Código',
      accessor: (row: Tournament) => (
        <span className="font-mono text-xs bg-slate-100 text-slate-600 px-2 py-1 rounded">
          {row.codigo}
        </span>
      ),
      sortValue: (row: Tournament) => row.codigo,
      width: '10%'
    },
  ];

  const dashboardActions: TableAction<Tournament>[] = [
    { label: 'Iniciar', onClick: handleStartTournament, variant: 'primary', show: (t) => t.estado === 'PENDING' },
    { label: 'Copiar Link', onClick: (t) => copyLink(getPlayLink(t.codigo)), variant: 'secondary', show: (t) => t.estado === 'IN_PROGRESS' },
    { label: 'Tabla de Líderes', onClick: handleViewLeaderboard, variant: 'primary', show: (t) => t.estado === 'IN_PROGRESS' },
    { label: 'Finalizar', onClick: handleFinalizeTournament, variant: 'danger', show: (t) => t.estado === 'IN_PROGRESS' },
    { label: 'Ver Resultados', onClick: handleViewLeaderboard, variant: 'primary', show: (t) => t.estado === 'FINALIZED' },
  ];

  if (loading) return <div className="loading">Cargando torneos...</div>;

  const totalPlayers = tournaments.reduce((sum, t) => sum + t.currentInscriptos, 0);

  return (
    <div>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 tracking-tight">Dashboard</h1>
          <p className="text-slate-500 text-sm mt-0.5">Resumen de torneos activos</p>
        </div>
        <Button onClick={() => navigate('/tournaments')} size="sm">
          <Trophy className="h-4 w-4" />
          Ir a Torneos
        </Button>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <Card>
          <CardContent className="p-5 flex items-center gap-4">
            <div className="w-11 h-11 rounded-full bg-emerald-100 flex items-center justify-center shrink-0">
              <Trophy className="h-5 w-5 text-emerald-600" />
            </div>
            <div>
              <p className="text-2xl font-bold text-slate-800">{tournaments.length}</p>
              <p className="text-sm text-slate-500">Torneos Activos</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5 flex items-center gap-4">
            <div className="w-11 h-11 rounded-full bg-blue-100 flex items-center justify-center shrink-0">
              <Users className="h-5 w-5 text-blue-600" />
            </div>
            <div>
              <p className="text-2xl font-bold text-slate-800">{totalPlayers}</p>
              <p className="text-sm text-slate-500">Total de Jugadores</p>
            </div>
          </CardContent>
        </Card>
      </div>

      {error && <div className="error-message">{error}</div>}

      {tournaments.length === 0 ? (
        <Card>
          <CardContent className="py-16 flex flex-col items-center text-center">
            <div className="w-16 h-16 rounded-full bg-slate-100 flex items-center justify-center mb-4">
              <Trophy className="h-8 w-8 text-slate-300" />
            </div>
            <h2 className="text-lg font-semibold text-slate-700 mb-1">No hay torneos activos</h2>
            <p className="text-slate-400 text-sm mb-5">Creá un nuevo torneo para comenzar</p>
            <Button onClick={() => navigate('/tournaments')}>
              <Trophy className="h-4 w-4" />
              Ir a Torneos
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Table
          data={tournaments}
          columns={columns}
          actions={dashboardActions}
          emptyMessage="No hay torneos activos"
        />
      )}

      {/* Modal: torneo iniciado */}
      {selectedTournament && (
        <Modal
          isOpen={showLinkModal}
          onClose={() => { setShowLinkModal(false); setSelectedTournament(null); setCopied(false); }}
          title="Torneo Iniciado"
          size="medium"
        >
          <div className="text-center py-2">
            <div className="w-14 h-14 rounded-full bg-emerald-100 flex items-center justify-center mx-auto mb-4">
              <CheckCircle2 className="h-7 w-7 text-emerald-600" />
            </div>
            <h3 className="text-lg font-semibold text-slate-800 mb-2">
              {selectedTournament.nombre} está activo
            </h3>
            <p className="text-slate-500 text-sm mb-4">
              Compartí este link para que los jugadores accedan a sus tarjetas:
            </p>
            <div className="bg-slate-50 border border-slate-200 rounded-lg px-4 py-3 mb-4 text-left">
              <div className="flex items-center justify-between gap-3">
                <code className="text-sm text-slate-700 break-all flex-1">
                  {getPlayLink(selectedTournament.codigo)}
                </code>
                <Button
                  size="sm"
                  variant={copied ? 'secondary' : 'default'}
                  onClick={() => copyLink(getPlayLink(selectedTournament!.codigo))}
                  className="shrink-0"
                >
                  {copied ? (
                    <><CheckCircle2 className="h-4 w-4" /> Copiado</>
                  ) : (
                    <><Copy className="h-4 w-4" /> Copiar</>
                  )}
                </Button>
              </div>
            </div>
            <p className="text-slate-400 text-xs flex items-center justify-center gap-1">
              <Link2 className="h-3 w-3" />
              Los jugadores deberán ingresar su matrícula para acceder
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
