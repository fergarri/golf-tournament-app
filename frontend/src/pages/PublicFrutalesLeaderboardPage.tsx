import { useState, useEffect, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { leaderboardService } from '../services/leaderboardService';
import { Tournament, FrutalesScore } from '../types';
import Table from '../components/Table';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';
import './TournamentLeaderboardPage.css';

const PublicFrutalesLeaderboardPage = () => {
  const { codigo } = useParams<{ codigo: string }>();

  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [frutalesScores, setFrutalesScores] = useState<FrutalesScore[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    loadData();
  }, [codigo]);

  const loadData = async () => {
    if (!codigo) return;
    try {
      setLoading(true);
      const tournamentData = await tournamentService.getByCodigo(codigo);
      setTournament(tournamentData);

      const scores = await leaderboardService.getPublicFrutalesScores(codigo);
      setFrutalesScores(scores);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading leaderboard');
    } finally {
      setLoading(false);
    }
  };

  const getPositionClass = (position: number) => {
    if (position === 1) return 'position-first';
    if (position === 2) return 'position-second';
    if (position === 3) return 'position-third';
    return '';
  };

  const getStatusLabel = (status: string) => {
    if (status === 'DISQUALIFIED') return 'DS';
    if (status !== 'DELIVERED') return 'NM';
    return null;
  };

  const filteredScores = searchQuery
    ? frutalesScores.filter((entry: FrutalesScore) =>
        `${entry.playerName} ${entry.matricula}`.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : frutalesScores;

  const daPlayerIds = useMemo(() => {
    const byNeto = new Map<string, number[]>();
    for (const entry of frutalesScores) {
      if (entry.status !== 'DELIVERED' || entry.scoreNeto == null) continue;
      const key = entry.scoreNeto.toString();
      const ids = byNeto.get(key) || [];
      ids.push(entry.playerId);
      byNeto.set(key, ids);
    }

    const result = new Set<number>();
    for (const ids of byNeto.values()) {
      if (ids.length > 1) {
        ids.forEach((id) => result.add(id));
      }
    }
    return result;
  }, [frutalesScores]);

  const columns = [
    {
      header: 'Pos',
      accessor: (row: FrutalesScore) => {
        if (row.status === 'DISQUALIFIED') return <span style={{ color: '#e74c3c', fontWeight: 'bold' }}>DS</span>;
        if (row.position) return <span className={`position ${getPositionClass(row.position)}`}>{row.position}</span>;
        return <span>-</span>;
      },
      width: '60px',
    },
    { header: 'Jugador', accessor: 'playerName' as keyof FrutalesScore, width: '18%' },
    { header: 'Matrícula', accessor: 'matricula' as keyof FrutalesScore, width: '10%' },
    {
      header: 'HCP Index',
      accessor: (row: FrutalesScore) => row.handicapIndex?.toFixed(1) || '-',
      width: '8%',
    },
    {
      header: 'HCP Course',
      accessor: (row: FrutalesScore) => row.handicapCourse?.toFixed(1) || '-',
      width: '8%',
    },
    {
      header: 'Gross',
      accessor: (row: FrutalesScore) => {
        const label = getStatusLabel(row.status);
        if (label) return <span style={{ color: label === 'DS' ? '#e74c3c' : '#f39c12', fontWeight: 'bold' }}>{label}</span>;
        return row.scoreGross || '-';
      },
      width: '7%',
    },
    {
      header: 'Neto',
      accessor: (row: FrutalesScore) => {
        const label = getStatusLabel(row.status);
        if (label) return <span style={{ color: label === 'DS' ? '#e74c3c' : '#f39c12', fontWeight: 'bold' }}>{label}</span>;
        return row.scoreNeto != null ? <strong>{row.scoreNeto}</strong> : '-';
      },
      width: '7%',
    },
    {
      header: 'Birdie',
      accessor: (row: FrutalesScore) => row.birdieCount || '-',
      width: '6%',
    },
    {
      header: 'Aguila',
      accessor: (row: FrutalesScore) => row.eagleCount || '-',
      width: '6%',
    },
    {
      header: 'Ace',
      accessor: (row: FrutalesScore) => row.aceCount || '-',
      width: '6%',
    },
    {
      header: 'Puntos',
      accessor: (row: FrutalesScore) => (
        <strong style={{ color: '#2980b9' }}>
          {row.totalPoints}
          {daPlayerIds.has(row.playerId) ? ' (DA)' : ''}
        </strong>
      ),
      width: '8%',
    },
  ];

  if (loading) return <div className="loading">Cargando leaderboard...</div>;

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <div className="tournament-info">
          <h1>{tournament?.nombre}</h1>
          {tournament?.doublePoints && <span className="final-badge" style={{ backgroundColor: '#8e44ad' }}>FECHA DOBLE</span>}
          {tournament?.estado === 'FINALIZED' && <span className="final-badge">RESULTADOS FINALES FRUTALES FECHA {formatDateSafe(tournament.fechaInicio)}</span>}
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

      <div className="search-container" style={{ marginBottom: '1.5rem' }}>
        <div className="search-input-wrapper" style={{ width: '50%' }}>
          <input
            type="text"
            placeholder="Buscar jugadores por nombre o matrícula"
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
          <p style={{ marginTop: '0.5rem', fontSize: '0.875rem', color: '#7f8c8d' }}>
            Mostrando {filteredScores.length} de {frutalesScores.length} jugadores
          </p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {frutalesScores.length === 0 ? (
        <div className="empty-state">
          <h2>No hay Resultados Disponibles</h2>
          <p>No hay resultados calculados para este torneo aún</p>
        </div>
      ) : (
        <>
          <div className="leaderboard-container">
            <Table
              data={filteredScores}
              columns={columns}
              emptyMessage="No hay jugadores que coincidan con la búsqueda"
              getRowKey={(row) => row.playerId}
            />
          </div>
          <div className="update-info">
            <span className="live-indicator"></span>
            <span style={{ marginLeft: '20px' }}>
              Cantidad de jugadores de la fecha: {frutalesScores.length}
            </span>
          </div>

          <div
            style={{
              marginTop: '1.25rem',
              backgroundColor: '#fff',
              border: '1px solid #e5e7eb',
              borderRadius: '6px',
              padding: '1rem 1.25rem',
              lineHeight: 1.45,
              color: '#2c3e50',
            }}
          >
            <p style={{ margin: '0 0 0.4rem 0', fontWeight: 700 }}>
              LOS JUGADORES QUE OBTENGAN EL 1ER PUESTO DE UNA FECHA, CON RESULTADO NETO BAJO PAR DE CANCHA
              PARA 9 HOYOS, SE LES DESCONTARA DE SU HCP COURSE LA DIFERENCIA DE GOLPES BAJO EL PAR EN SU
              SIGUIENTE PARTICIPACION.
            </p>

            <p style={{ margin: '0 0 0 0', fontWeight: 700 }}>
              Metodo de puntuación:
            </p>
            <p style={{ margin: '0 0 0 0', fontWeight: 700 }}>1er puesto 12 pts, 2do puesto 10 pts, 3er puesto 8 pts, 4to puesto 6 pts, 5to puesto 4 pts y 6to puesto 2 pts.</p>
            <p style={{ margin: '0 0 0.7rem 0', fontWeight: 700 }}>También 1 punto por participación, 1 punto por cada birdie, 5 puntos por cada águila y 10 puntos por hoyo en 1.</p>

            <div
              style={{
                border: '2px solid #7f8c8d',
                borderRadius: '4px',
                padding: '0.5rem 0.75rem',
                maxWidth: '360px',
                fontWeight: 700,
              }}
            >
              <div><span style={{ color: '#2980b9' }}>DA</span> =DESEMPATE AUTOMATICO</div>
              <div><span style={{ color: '#f39c12' }}>NM</span> = NO MARCO</div>
              <div><span style={{ color: '#e74c3c' }}>DS</span> =DESCALIFICADO</div>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default PublicFrutalesLeaderboardPage;
