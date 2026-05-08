import { useState, useEffect, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { leaderboardService } from '../services/leaderboardService';
import { Tournament, LeaderboardEntry, TournamentScore } from '../types';
import Table from '../components/Table';
import Tabs, { Tab } from '../components/Tabs';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';

const PublicLeaderboardPage = () => {
  const { codigo } = useParams<{ codigo: string }>();

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [tournamentScores, setTournamentScores] = useState<TournamentScore[]>([]);
  const [activeTab, setActiveTab] = useState<string>('general');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    loadData();
  }, [codigo]);

  useEffect(() => {
    if (tournament?.tipo === 'CLASICO' && tournament.scoringConfig != null && tournament.categories && tournament.categories.length > 0) {
      const sorted = [...tournament.categories].sort((a, b) => a.handicapMin - b.handicapMin);
      if (sorted[0]?.id) setActiveTab(sorted[0].id.toString());
    }
  }, [tournament?.tipo, tournament?.scoringConfig, tournament?.categories]);

  const loadData = async () => {
    if (!codigo) return;
    try {
      setLoading(true);
      
      const tournamentData = await tournamentService.getByCodigo(codigo);
      setTournament(tournamentData);

      const isClasic = tournamentData.tipo === 'CLASICO' && tournamentData.scoringConfig != null;
      const isFrutales = tournamentData.tipo === 'FRUTALES' && tournamentData.scoringConfig != null;
      const promises: [Promise<LeaderboardEntry[]>, Promise<TournamentScore[]>?] = [
        leaderboardService.getPublicLeaderboard(codigo),
        isClasic ? leaderboardService.getPublicClasicScores(codigo)
          : isFrutales ? leaderboardService.getPublicFrutalesScores(codigo)
          : undefined,
      ];
      const [leaderboardData, scoresData] = await Promise.all(promises);
      setLeaderboard(leaderboardData);
      setTournamentScores(scoresData ?? []);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading leaderboard');
    } finally {
      setLoading(false);
    }
  };

  // Mapa global (FRUTALES o CLASICO Scratch según tab)
  const globalScoreMap = useMemo(() => {
    const map = new Map<number, TournamentScore>();
    tournamentScores.filter(ts => ts.scoreType === 'GLOBAL' || ts.scoreType == null)
      .forEach(ts => map.set(ts.playerId, ts));
    return map;
  }, [tournamentScores]);

  const categoryScoreMap = useMemo(() => {
    const map = new Map<number, Map<number, TournamentScore>>();
    tournamentScores.filter(ts => ts.scoreType === 'CATEGORY' && ts.categoryId != null)
      .forEach(ts => {
        const catId = ts.categoryId!;
        if (!map.has(catId)) map.set(catId, new Map());
        map.get(catId)!.set(ts.playerId, ts);
      });
    return map;
  }, [tournamentScores]);

  const scratchScoreMap = useMemo(() => {
    const map = new Map<number, TournamentScore>();
    tournamentScores.filter(ts => ts.scoreType === 'SCRATCH')
      .forEach(ts => map.set(ts.playerId, ts));
    return map;
  }, [tournamentScores]);

  const activeScoreMap = useMemo((): Map<number, TournamentScore> => {
    if (tournament?.tipo === 'CLASICO' && tournament?.scoringConfig != null) {
      if (activeTab === 'scratch') return scratchScoreMap;
      const catId = parseInt(activeTab);
      if (!isNaN(catId)) return categoryScoreMap.get(catId) ?? new Map();
      return new Map();
    }
    return globalScoreMap;
  }, [tournament?.tipo, tournament?.scoringConfig, activeTab, globalScoreMap, categoryScoreMap, scratchScoreMap]);

  const daPlayerIds = useMemo(() => {
    if (!tournament?.scoringConfig || tournamentScores.length === 0) return new Set<number>();
    const scoresForTab = Array.from(activeScoreMap.values());
    const byNeto = new Map<string, number[]>();
    for (const entry of scoresForTab) {
      if (entry.status !== 'DELIVERED' || entry.scoreNeto == null || entry.position == null || entry.position > 6) continue;
      const key = entry.scoreNeto.toString();
      const ids = byNeto.get(key) || [];
      ids.push(entry.playerId);
      byNeto.set(key, ids);
    }
    const result = new Set<number>();
    for (const ids of byNeto.values()) {
      if (ids.length > 1) ids.forEach(pid => result.add(pid));
    }
    return result;
  }, [tournamentScores, activeScoreMap, tournament?.scoringConfig]);

  // Build tabs based on tournament categories (excluding "Sin Categoría")
  const tabs = useMemo((): Tab[] => {
    if (!tournament || !tournament.categories || tournament.categories.length === 0) return [];

    // Solo se comporta como CLASICO con admin (sin tab General) si tiene scoringConfig
    const isClasic = tournament.tipo === 'CLASICO' && tournament.scoringConfig != null;
    const tabsList: Tab[] = [];

    if (!isClasic) {
      tabsList.push({ id: 'general', label: 'General', count: leaderboard.filter(e => e.status === 'DELIVERED').length });
    }

    const sortedCategories = [...tournament.categories].sort((a, b) => a.handicapMin - b.handicapMin);
    sortedCategories.forEach(category => {
      if (!category.id) return;
      const count = isClasic
        ? (categoryScoreMap.get(category.id)?.size ?? leaderboard.filter(e => e.categoryId === category.id && e.status === 'DELIVERED').length)
        : leaderboard.filter(e => e.categoryId === category.id && e.status === 'DELIVERED').length;
      tabsList.push({
        id: category.id.toString(),
        label: `${category.nombre} (${category.handicapMin}-${category.handicapMax})`,
        count,
      });
    });

    // Scratch tab: para CLASICO mostrar total de inscriptos (con o sin scores calculados)
    const scratchCount = isClasic
      ? leaderboard.length
      : leaderboard.filter(e => e.status === 'DELIVERED').length;
    tabsList.push({ id: 'scratch', label: 'Scratch', count: scratchCount });

    return tabsList;
  }, [tournament, leaderboard, categoryScoreMap, scratchScoreMap]);

  // Filter leaderboard based on active tab and recalculate positions per category
  const filteredByCategory = useMemo(() => {
    const isClasic = tournament?.tipo === 'CLASICO' && tournament?.scoringConfig != null;

    if (activeTab === 'scratch') {
      if (isClasic) {
        if (scratchScoreMap.size > 0) {
          // CLASICO con scores calculados: ordenar por posición scratch
          return leaderboard
            .filter(e => scratchScoreMap.has(e.playerId))
            .sort((a, b) => (scratchScoreMap.get(a.playerId)?.position ?? 9999) - (scratchScoreMap.get(b.playerId)?.position ?? 9999))
            .map((e, i) => ({ ...e, position: i + 1 }));
        }
        // CLASICO sin scores calculados: mostrar todos los inscriptos, entregados primero ordenados por gross
        const delivered = leaderboard
          .filter(e => e.status === 'DELIVERED')
          .sort((a, b) => (a.scoreGross ?? 0) - (b.scoreGross ?? 0));
        const others = leaderboard.filter(e => e.status !== 'DELIVERED');
        return [...delivered.map((e, i) => ({ ...e, position: i + 1 })), ...others];
      }
      // FRUTALES: solo entregados, ordenados por Gross
      const delivered = leaderboard
        .filter(entry => entry.status === 'DELIVERED')
        .sort((a, b) => (a.scoreGross ?? 0) - (b.scoreGross ?? 0));
      return delivered.map((entry, index) => ({ ...entry, position: index + 1 }));
    }

    let filtered: LeaderboardEntry[];
    if (activeTab === 'general') {
      filtered = leaderboard.filter(entry => entry.status === 'DELIVERED');
    } else {
      const categoryId = parseInt(activeTab);
      filtered = leaderboard.filter(entry => entry.categoryId === categoryId && entry.status === 'DELIVERED');
    }

    if (activeScoreMap.size > 0) {
      filtered = [...filtered].sort((a, b) => {
        const posA = activeScoreMap.get(a.playerId)?.position ?? 9999;
        const posB = activeScoreMap.get(b.playerId)?.position ?? 9999;
        return posA - posB;
      });
    }

    return filtered.map((entry, index) => ({ ...entry, position: index + 1 }));
  }, [leaderboard, activeTab, activeScoreMap, scratchScoreMap, tournament?.tipo]);

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

  const hasScoringData = tournament?.scoringConfig != null && tournamentScores.length > 0;

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
      header: 'HCP I',
      accessor: (row: LeaderboardEntry) => row.handicapIndex?.toFixed(1) || '-',
      width: '8%',
    },
    {
      header: 'HCP C',
      accessor: (row: LeaderboardEntry) => row.handicapCourse?.toFixed(1) || '-',
      width: '8%',
    },
    { 
      header: 'Gross', 
      accessor: 'scoreGross' as keyof LeaderboardEntry,
      width: '7%' 
    },
    { 
      header: 'Neto', 
      accessor: (row: LeaderboardEntry) => <strong>{row.scoreNeto}</strong>, 
      width: '7%' 
    },
    {
      header: 'To Par',
      accessor: (row: LeaderboardEntry) => {
        // Scratch de CLASICO: To Par sobre Gross. Resto de tabs: To Par sobre Neto.
        const toPar = (tournament?.tipo === 'CLASICO' && tournament.scoringConfig != null && activeTab === 'scratch')
          ? row.scoreGross - row.totalPar
          : row.scoreToPar;
        return (
          <span className={`score-to-par ${toPar < 0 ? 'under-par' : toPar > 0 ? 'over-par' : 'even-par'}`}>
            {getScoreToPar(toPar)}
          </span>
        );
      },
      width: '8%',
    },
    ...(hasScoringData && tournament.scoringConfig!.birdiePoints > 0 ? [{
      header: 'Birdie',
      accessor: (row: LeaderboardEntry) => activeScoreMap.get(row.playerId)?.birdieCount || '-',
      width: '5%',
    }] : []),
    ...(hasScoringData && tournament.scoringConfig!.eaglePoints > 0 ? [{
      header: 'Aguila',
      accessor: (row: LeaderboardEntry) => activeScoreMap.get(row.playerId)?.eagleCount || '-',
      width: '5%',
    }] : []),
    ...(hasScoringData && tournament.scoringConfig!.acePoints > 0 ? [{
      header: 'Ace',
      accessor: (row: LeaderboardEntry) => activeScoreMap.get(row.playerId)?.aceCount || '-',
      width: '5%',
    }] : []),
    ...(hasScoringData ? [{
      header: 'Puntos',
      accessor: (row: LeaderboardEntry) => {
        const ts = activeScoreMap.get(row.playerId);
        if (!ts) return <span>-</span>;
        return (
          <strong style={{ color: '#2980b9' }}>
            {ts.totalPoints}{daPlayerIds.has(row.playerId) ? ' (DA)' : ''}
          </strong>
        );
      },
      width: '7%',
    }] : []),
    { header: 'Club', accessor: (row: LeaderboardEntry) => row.clubOrigen || '-', width: '12%' },
    ...(!hasScoringData ? [{ 
      header: 'Categoría', 
      accessor: (row: LeaderboardEntry) => row.categoryName || '-',
      width: '12%',
    }] : []),
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
          {(tournament?.prizes || []).length > 0 && (
            <div style={{ marginTop: '0.75rem', color: '#7f8c8d', fontSize: '0.95rem' }}>
              {(tournament?.prizes || []).map((prize) => {
                const labels: Record<string, string> = {
                  LONG_DRIVER: 'Long Driver',
                  BEST_DRIVER: 'Best Driver',
                  BEST_APPROACH: 'Best Approach',
                };
                return (
                  <div key={prize.id} style={{ display: 'flex', gap: '0.4rem', marginBottom: '0.50rem' }}>
                    <strong style={{ color: '#2c3e50' }}>{labels[prize.prizeType] || prize.prizeType}:</strong>
                    {prize.winnerName
                      ? <span>{prize.winnerName}</span>
                      : <em>Pendiente</em>
                    }
                  </div>
                );
              })}
            </div>
          )}
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
            <span>Tarjetas entregadas: {filteredByCategory.length}</span>
          </div>
        </>
      )}
    </div>
  );
};

export default PublicLeaderboardPage;
