import { useState, useEffect, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { leaderboardService } from '../services/leaderboardService';
import { Tournament, LeaderboardEntry } from '../types';
import Table from '../components/Table';
import Tabs, { Tab } from '../components/Tabs';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';

const PublicLeaderboardPage = () => {
  const { codigo } = useParams<{ codigo: string }>();

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [activeTab, setActiveTab] = useState<string>('general');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    loadData();
    
    // Poll for updates every 100 seconds for real-time updates
    const interval = setInterval(() => {
      loadData();
    }, 100000); // 100 seconds

    return () => clearInterval(interval);
  }, [codigo]);

  const loadData = async () => {
    if (!codigo) return;
    try {
      setLoading(true);
      
      // Fetch tournament by code
      const tournamentData = await tournamentService.getByCodigo(codigo);
      setTournament(tournamentData);

      // Fetch public leaderboard using the public endpoint
      const leaderboardData = await leaderboardService.getPublicLeaderboard(codigo);
      setLeaderboard(leaderboardData);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading leaderboard');
    } finally {
      setLoading(false);
    }
  };

  // Build tabs based on tournament categories (excluding "Sin Categoría")
  const tabs = useMemo((): Tab[] => {
    if (!tournament || !tournament.categories) return [];

    const tabsList: Tab[] = [];
    
    // Always add "General" tab first
    tabsList.push({
      id: 'general',
      label: 'General',
      count: leaderboard.filter(entry => entry.delivered).length,
    });

    // Sort categories by handicap range (min to max)
    const sortedCategories = [...tournament.categories].sort((a, b) => 
      a.handicapMin - b.handicapMin
    );

    // Add tab for each category
    sortedCategories.forEach(category => {
      if (!category.id) return;
      
      const count = leaderboard.filter(entry => 
        entry.categoryId === category.id && entry.delivered
      ).length;
      
      tabsList.push({
        id: category.id.toString(),
        label: `${category.nombre} (${category.handicapMin}-${category.handicapMax})`,
        count,
      });
    });

    // Note: "Sin Categoría" tab is NOT added for public leaderboard

    return tabsList;
  }, [tournament, leaderboard]);

  // Filter leaderboard based on active tab and recalculate positions per category
  const filteredByCategory = useMemo(() => {
    let filtered: LeaderboardEntry[] = [];
    
    // Filter by active tab
    if (activeTab === 'general') {
      filtered = leaderboard.filter(entry => entry.delivered);
    } else {
      // Filter by specific category ID
      const categoryId = parseInt(activeTab);
      filtered = leaderboard.filter(entry => 
        entry.categoryId === categoryId && entry.delivered
      );
    }
    
    // Assign positions for each category (1, 2, 3...)
    const withPositions = filtered.map((entry, index) => ({
      ...entry,
      position: index + 1
    }));
    
    return withPositions;
  }, [leaderboard, activeTab]);

  const getScoreToPar = (scoreToPar: number) => {
    if (scoreToPar === 0) return 'E';
    if (scoreToPar > 0) return `+${scoreToPar}`;
    return `${scoreToPar}`;
  };

  const getPositionClass = (position: number) => {
    if (position === 1) return 'position-first';
    if (position === 2) return 'position-second';
    if (position === 3) return 'position-third';
    return '';
  };

  // Apply search filter to the category-filtered leaderboard
  const filteredLeaderboard = searchQuery
    ? filteredByCategory.filter((entry: LeaderboardEntry) =>
        `${entry.playerName} ${entry.matricula} ${entry.clubOrigen || ''}`.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : filteredByCategory;

  const columns = [
    {
      header: 'Pos',
      accessor: (row: LeaderboardEntry) => (
        row.position && row.position > 0 ? (
          <span className={`position ${getPositionClass(row.position)}`}>{row.position}</span>
        ) : (
          <span>-</span>
        )
      ),
      width: '60px',
    },
    { header: 'Jugador', accessor: 'playerName' as keyof LeaderboardEntry, width: '15%' },
    { header: 'Matrícula', accessor: 'matricula' as keyof LeaderboardEntry, width: '10%' },
    {
      header: 'HCP',
      accessor: (row: LeaderboardEntry) => row.handicapCourse?.toFixed(1) || '-',
      width: '8%',
    },
    { 
      header: 'Score Gross', 
      accessor: 'scoreGross' as keyof LeaderboardEntry,
      width: '10%' 
    },
    { 
      header: 'Score Neto', 
      accessor: (row: LeaderboardEntry) => <strong>{row.scoreNeto}</strong>, 
      width: '10%' 
    },
    {
      header: 'To Par',
      accessor: (row: LeaderboardEntry) => (
        <span className={`score-to-par ${row.scoreToPar < 0 ? 'under-par' : row.scoreToPar > 0 ? 'over-par' : 'even-par'}`}>
          {getScoreToPar(row.scoreToPar)}
        </span>
      ),
      width: '8%',
    },
    { header: 'Club', accessor: (row: LeaderboardEntry) => row.clubOrigen || '-', width: '12%' },
    { 
      header: 'Categoría', 
      accessor: (row: LeaderboardEntry) => row.categoryName || '-',
      width: '12%' 
    },
  ];

  if (loading) return <div className="loading">Cargando leaderboard...</div>;

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="tournament-info">
          <h1>{tournament?.nombre}</h1>
          {tournament?.estado === 'FINALIZED' && <span className="final-badge">RESULTADOS FINALES</span>}
          <div className="tournament-details">
            <span className="detail-item">
              <strong>Campo:</strong> {tournament?.courseName}
            </span>
            <span className="detail-item">
              <strong>Fecha:</strong> {tournament?.fechaInicio ? formatDateSafe(tournament.fechaInicio) : ''}
            </span>
            <span className="detail-item">
              <strong>Estado:</strong> {tournament?.estado === 'IN_PROGRESS' ? 'En Proceso' : tournament?.estado === 'FINALIZED' ? 'Finalizado' : 'Pendiente'}
            </span>
            <span className="detail-item">
              <strong>Jugadores:</strong> {tournament?.currentInscriptos}
            </span>
          </div>
        </div>
      </div>

      {tournament && tabs.length > 0 && (
        <Tabs 
          tabs={tabs} 
          activeTab={activeTab} 
          onTabChange={setActiveTab} 
        />
      )}

      <div className="search-container" style={{ marginBottom: '1.5rem' }}>
        <div className="search-input-wrapper" style={{ width: '50%' }}>
          <input
            type="text"
            placeholder="Buscar jugadores por nombre, matrícula o club"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="search-input"
            style={{
              width: '100%',
              padding: '0.75rem 2rem 0.75rem 1rem',
              fontSize: '1rem',
              border: '1px solid #e0e0e0',
              borderRadius: '4px',
              transition: 'border-color 0.3s',
            }}
            onFocus={(e) => e.target.style.borderColor = '#3498db'}
            onBlur={(e) => e.target.style.borderColor = '#e0e0e0'}
          />
          {searchQuery && (
            <button className="search-clear-btn" onClick={() => setSearchQuery('')} type="button">×</button>
          )}
        </div>
        {searchQuery && (
          <p style={{ 
            marginTop: '0.5rem', 
            fontSize: '0.875rem', 
            color: '#7f8c8d' 
          }}>
            Mostrando {filteredLeaderboard.length} de {filteredByCategory.length} jugadores
          </p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {filteredByCategory.length === 0 ? (
        <div className="empty-state">
          <h2>No hay Tarjetas Entregadas</h2>
          <p>No hay tarjetas entregadas en este torneo aún</p>
        </div>
      ) : (
        <>
          <div className="leaderboard-container">
            <Table 
              data={filteredLeaderboard} 
              columns={columns} 
              emptyMessage="No hay jugadores que coincidan con la búsqueda"
              getRowKey={(row) => row.playerId}
            />
          </div>
          <div className="update-info">
            <span className="live-indicator"></span>
            <span>Actualizando en tiempo real cada 100 segundos</span>
            <span style={{ marginLeft: '20px' }}>
              • Tarjetas entregadas: {filteredByCategory.length}
            </span>
          </div>
        </>
      )}
    </div>
  );
};

export default PublicLeaderboardPage;
