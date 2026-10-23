import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { teeSheetService } from '../services/teeSheetService';
import { PublicTeeSheet } from '../types';
import { formatDateSafe } from '../utils/dateUtils';
import { formatStartCell, publicStartColumn } from '../utils/teeSheetLayout';
import './TournamentLeaderboardPage.css';
import './TournamentTeeSheetPage.css';

const PublicTeeSheetGrid = ({ sheet }: { sheet: PublicTeeSheet }) => {
  const startTitle = publicStartColumn(sheet.groups);
  return (
    <div className="tee-sheet-table-wrap">
      <table className={`tee-sheet-table ${startTitle ? 'has-start' : ''}`}>
        <thead>
          <tr>
            {startTitle && <th>{startTitle}</th>}
            <th>Jugador 1</th>
            <th>Jugador 2</th>
            <th>Jugador 3</th>
            <th>Jugador 4</th>
          </tr>
        </thead>
        <tbody>
          {sheet.groups.map((group, index) => (
            <tr key={index}>
              {startTitle && <td className="tee-sheet-table-start">{formatStartCell(group.holeNumber, group.teeTime)}</td>}
              {playerNames(group).map((name, slot) => (
                <td key={slot}>
                  {name ? <span className="truncated-label" title={name}>{name}</span> : ''}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

const playerNames = (group: PublicTeeSheet['groups'][number]) => [
  group.player1Name,
  group.player2Name,
  group.player3Name,
  group.player4Name,
];

const PublicTeeSheetPage = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const [sheet, setSheet] = useState<PublicTeeSheet | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      if (!codigo) {
        setError('Torneo inválido');
        setLoading(false);
        return;
      }
      try {
        setLoading(true);
        const data = await teeSheetService.getPublic(codigo);
        setSheet(data);
        setError('');
      } catch (err: unknown) {
        const message =
          typeof err === 'object' && err && 'response' in err
            ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
            : undefined;
        setError(message || 'No se pudieron cargar las salidas');
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [codigo]);

  if (loading) return <div className="loading">Cargando salidas...</div>;
  if (!sheet) return <div className="tee-sheet-public"><div className="error-message">{error || 'No se encontraron las salidas'}</div></div>;

  return (
    <div className="tee-sheet-page tee-sheet-public">
      <div className="leaderboard-header">
        <div className="tournament-info">
          <h1>{sheet.tournamentName}</h1>
          <div className="tournament-details">
            <span className="detail-item">
              <strong>Campo:</strong> {sheet.courseName}
            </span>
            <span className="detail-item">
              <strong>Fecha:</strong> {formatDateSafe(sheet.fechaInicio)}
            </span>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      {!sheet.published ? (
        <div className="tee-sheet-unpublished">Las salidas todavía no fueron publicadas</div>
      ) : (
        <PublicTeeSheetGrid sheet={sheet} />
      )}
    </div>
  );
};

export default PublicTeeSheetPage;
