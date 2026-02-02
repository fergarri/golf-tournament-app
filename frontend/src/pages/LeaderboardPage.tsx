import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { leaderboardService } from '../services/leaderboardService';
import { tournamentService } from '../services/tournamentService';
import { LeaderboardEntry, Tournament } from '../types';
import './LeaderboardPage.css';

const LeaderboardPage: React.FC = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [tournament, setTournament] = useState<Tournament | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<number | undefined>(undefined);

  const fetchData = async () => {
    if (!codigo) return;

    try {
      // Fetch tournament info by code
      const tournamentData = await tournamentService.getByCodigo(codigo);
      setTournament(tournamentData);

      // Fetch leaderboard
      const leaderboardData = await leaderboardService.getLeaderboard(
        tournamentData.id,
        selectedCategory
      );
      setLeaderboard(leaderboardData);
      setError(null);
    } catch (err) {
      console.error('Error fetching leaderboard:', err);
      setError('Error al cargar el leaderboard');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();

    // Poll for updates every 10 seconds
    const interval = setInterval(() => {
      fetchData();
    }, 10000);

    return () => clearInterval(interval);
  }, [codigo, selectedCategory]);

  const handleCategoryChange = (categoryId: number | undefined) => {
    setSelectedCategory(categoryId);
    setLoading(true);
  };

  const formatScore = (score: number, par: number) => {
    const diff = score - par;
    if (diff === 0) return 'E';
    return diff > 0 ? `+${diff}` : `${diff}`;
  };

  const getPositionClass = (position: number) => {
    if (position === 1) return 'position-first';
    if (position === 2) return 'position-second';
    if (position === 3) return 'position-third';
    return '';
  };

  if (loading && leaderboard.length === 0) {
    return (
      <div className="leaderboard-page">
        <div className="loading">Cargando leaderboard...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="leaderboard-page">
        <div className="error">{error}</div>
      </div>
    );
  }

  return (
    <div className="leaderboard-page">
      <div className="leaderboard-header">
        <h1>Leaderboard</h1>
        {tournament && (
          <div className="tournament-info">
            <h2>{tournament.nombre}</h2>
            <p className="course-name">{tournament.courseName}</p>
          </div>
        )}
      </div>

      {tournament && tournament.categories.length > 0 && (
        <div className="category-filter">
          <button
            className={selectedCategory === undefined ? 'active' : ''}
            onClick={() => handleCategoryChange(undefined)}
          >
            Todas las categorías
          </button>
          {tournament.categories.map((category) => (
            <button
              key={category.id}
              className={selectedCategory === category.id ? 'active' : ''}
              onClick={() => handleCategoryChange(category.id)}
            >
              {category.nombre}
            </button>
          ))}
        </div>
      )}

      {leaderboard.length === 0 ? (
        <div className="no-data">
          <p>No hay tarjetas entregadas aún.</p>
          <p>El leaderboard se actualizará automáticamente cuando los jugadores entreguen sus tarjetas.</p>
        </div>
      ) : (
        <div className="leaderboard-container">
          <div className="table-responsive">
            <table className="leaderboard-table">
              <thead>
                <tr>
                  <th>Pos</th>
                  <th>Jugador</th>
                  <th>Matrícula</th>
                  <th>HCP</th>
                  <th>Score Gross</th>
                  <th>Score Neto</th>
                  <th>To Par</th>
                  <th>Club</th>
                  {selectedCategory === undefined && <th>Categoría</th>}
                </tr>
              </thead>
              <tbody>
                {leaderboard.map((entry) => (
                  <tr key={entry.playerId} className={getPositionClass(entry.position)}>
                    <td className="position">
                      <span className="position-badge">{entry.position}</span>
                    </td>
                    <td className="player-name">{entry.playerName}</td>
                    <td className="matricula">{entry.matricula}</td>
                    <td className="handicap">
                      {entry.handicapCourse !== undefined && entry.handicapCourse !== null
                        ? entry.handicapCourse
                        : '-'}
                    </td>
                    <td className="score-gross">{entry.scoreGross}</td>
                    <td className="score-neto">
                      <strong>{entry.scoreNeto}</strong>
                    </td>
                    <td className="score-to-par">
                      <span className={`score-badge ${entry.scoreToPar === 0 ? 'even' : entry.scoreToPar < 0 ? 'under' : 'over'}`}>
                        {formatScore(entry.scoreGross, entry.totalPar)}
                      </span>
                    </td>
                    <td className="club">{entry.clubOrigen || '-'}</td>
                    {selectedCategory === undefined && (
                      <td className="category">{entry.categoryName || '-'}</td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="update-info">
            <p>
              <span className="live-indicator"></span>
              Actualizando en tiempo real
            </p>
          </div>
        </div>
      )}
    </div>
  );
};

export default LeaderboardPage;
