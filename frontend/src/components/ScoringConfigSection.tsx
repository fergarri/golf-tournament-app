import { useState, useEffect } from 'react';
import { ScoringConfig, ScoringPositionPoints } from '../types';
import { tournamentAdminService } from '../services/tournamentAdminService';
import Modal from './Modal';

interface ScoringConfigSectionProps {
  tournamentAdminId: number;
  /** CLASICO o FRUTALES */
  tipo?: string;
  onSaved?: () => void;
}

const TIE_BREAK_OPTIONS = [
  {
    value: 'NETO_HCP_HOLE',
    label: 'Desempate Frutales',
    tooltip:
      'En caso de empate en el score neto, gana el jugador con menor handicap índice. Si persiste el empate, se compara hoyo por hoyo desde el último jugado hacia atrás (en gross).',
  },
  {
    value: 'GROSS_BACK9',
    label: 'Desempate vuelta gross',
    tooltip:
      'En caso de empate en puntos, se compara el gross acumulado de los hoyos 10 al 18. Si persiste el empate, gana quien haya hecho menos golpes en el hoyo 18; si sigue empatado, en el 17, y así sucesivamente hasta el hoyo 10.',
  },
];

const ScoringConfigSection = ({ tournamentAdminId, tipo, onSaved }: ScoringConfigSectionProps) => {
  const [config, setConfig] = useState<ScoringConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [modalState, setModalState] = useState<{ open: boolean; type: 'success' | 'error'; message: string }>({
    open: false,
    type: 'success',
    message: '',
  });
  const [tooltipVisible, setTooltipVisible] = useState(false);

  useEffect(() => {
    loadConfig();
  }, [tournamentAdminId]);

  const loadConfig = async () => {
    try {
      setLoading(true);
      const data = await tournamentAdminService.getScoringConfig(tournamentAdminId);
      setConfig(data);
      setError('');
    } catch {
      setError('Error cargando configuración de puntuación');
    } finally {
      setLoading(false);
    }
  };

  const handleFieldChange = (field: keyof ScoringConfig, value: number | string) => {
    if (!config) return;
    setConfig({ ...config, [field]: value });
  };

  const handlePositionChange = (index: number, field: keyof ScoringPositionPoints, value: number) => {
    if (!config) return;
    const updated = config.positionPoints.map((pp, i) =>
      i === index ? { ...pp, [field]: value } : pp
    );
    setConfig({ ...config, positionPoints: updated });
  };

  const handleAddPosition = () => {
    if (!config) return;
    const nextPosition =
      config.positionPoints.length > 0
        ? Math.max(...config.positionPoints.map((pp) => pp.position)) + 1
        : 1;
    setConfig({
      ...config,
      positionPoints: [...config.positionPoints, { position: nextPosition, points: 0 }],
    });
  };

  const handleRemovePosition = (index: number) => {
    if (!config) return;
    setConfig({
      ...config,
      positionPoints: config.positionPoints.filter((_, i) => i !== index),
    });
  };

  const handleSave = async () => {
    if (!config) return;
    try {
      setSaving(true);
      const saved = await tournamentAdminService.saveScoringConfig(tournamentAdminId, {
        birdiePoints: config.birdiePoints,
        eaglePoints: config.eaglePoints,
        acePoints: config.acePoints,
        participationPoints: config.participationPoints,
        remainingPositionsPoints: config.remainingPositionsPoints,
        qualifiedPlayoffPositions: config.qualifiedPlayoffPositions,
        qualifiedPlayoffPositionsScratch: config.qualifiedPlayoffPositionsScratch ?? 0,
        hcpQualifiedMode: config.hcpQualifiedMode ?? 'GLOBAL',
        tieBreakMode: config.tieBreakMode,
        positionPoints: config.positionPoints,
      });
      setConfig(saved);
      if (onSaved) {
        onSaved();
      } else {
        setModalState({ open: true, type: 'success', message: 'Configuración de puntuación guardada correctamente.' });
      }
    } catch {
      setModalState({ open: true, type: 'error', message: 'Error guardando la configuración de puntuación.' });
    } finally {
      setSaving(false);
    }
  };

  const selectedTieBreak = TIE_BREAK_OPTIONS.find((o) => o.value === config?.tieBreakMode);

  if (loading) return <div style={{ padding: '1rem', color: '#7f8c8d' }}>Cargando configuración...</div>;
  if (!config) return <div style={{ padding: '1rem', color: '#e74c3c' }}>{error}</div>;

  return (
    <>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem' }}>
          {/* Columna izquierda: jugadas especiales y otros */}
          <div>
            <h3 style={{ margin: '0 0 1rem', fontSize: '0.95rem', fontWeight: 600, color: '#34495e', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Puntos por jugada especial
            </h3>
            <div style={{ display: 'grid', gap: '0.75rem' }}>
              {[
                { field: 'birdiePoints' as const, label: 'Birdie' },
                { field: 'eaglePoints' as const, label: 'Eagle' },
                { field: 'acePoints' as const, label: 'Hoyo en uno (Ace)' },
                { field: 'participationPoints' as const, label: 'Participación' },
              ].map(({ field, label }) => (
                <div key={field} style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <label style={{ flex: 1, fontSize: '0.9rem', color: '#555' }}>{label}</label>
                  <input
                    type="number"
                    min={0}
                    value={config[field] as number}
                    onChange={(e) => handleFieldChange(field, parseInt(e.target.value) || 0)}
                    style={inputStyle}
                  />
                  <span style={{ fontSize: '0.85rem', color: '#7f8c8d', minWidth: '30px' }}>pts</span>
                </div>
              ))}
            </div>

            {/* Clasificación Sin HCP — solo CLASICO */}
            {tipo === 'CLASICO' && (
              <div style={{ marginTop: '1.5rem' }}>
                <h3 style={{ margin: '0 0 1rem', fontSize: '0.95rem', fontWeight: 600, color: '#34495e', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Clasificación Sin HCP
                </h3>
                <div style={{ display: 'grid', gap: '0.75rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <label style={{ flex: 1, fontSize: '0.9rem', color: '#555' }}>Clasificados al playoff</label>
                    <input
                      type="number"
                      min={0}
                      value={config.qualifiedPlayoffPositionsScratch ?? 0}
                      onChange={(e) => handleFieldChange('qualifiedPlayoffPositionsScratch', parseInt(e.target.value) || 0)}
                      style={inputStyle}
                    />
                    <span style={{ fontSize: '0.85rem', color: '#7f8c8d', minWidth: '30px' }}>pos.</span>
                  </div>
                  {(config.qualifiedPlayoffPositionsScratch ?? 0) === 0 && (
                    <div style={{ fontSize: '0.78rem', color: '#7f8c8d', fontStyle: 'italic', paddingLeft: '0.1rem' }}>
                      Con valor 0, la clasificación y la tabla Sin HCP no se calculan ni se muestran.
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Clasificación Con HCP */}
            <div style={{ marginTop: '1.5rem' }}>
              <h3 style={{ margin: '0 0 1rem', fontSize: '0.95rem', fontWeight: 600, color: '#34495e', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Clasificación Con HCP
              </h3>
              <div style={{ display: 'grid', gap: '0.75rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <label style={{ flex: 1, fontSize: '0.9rem', color: '#555' }}>Clasificados al playoff</label>
                  <input
                    type="number"
                    min={1}
                    value={config.qualifiedPlayoffPositions}
                    onChange={(e) => handleFieldChange('qualifiedPlayoffPositions', parseInt(e.target.value) || 1)}
                    style={inputStyle}
                  />
                  <span style={{ fontSize: '0.85rem', color: '#7f8c8d', minWidth: '30px' }}>pos.</span>
                </div>

                {/* Modo de clasificación: solo para torneos CLASICO */}
                {tipo === 'CLASICO' && (
                  <div style={{ paddingTop: '0.1rem' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                      {[
                        { value: 'GLOBAL', label: 'Global', description: `Clasifican los primeros ${config.qualifiedPlayoffPositions} del total Con HCP.` },
                        { value: 'PER_CATEGORY', label: 'Por Categoría', description: `Clasifican los primeros ${config.qualifiedPlayoffPositions} de cada categoría Con HCP.` },
                      ].map((opt) => (
                        <label
                          key={opt.value}
                          style={{
                            display: 'flex',
                            alignItems: 'flex-start',
                            gap: '0.5rem',
                            cursor: 'pointer',
                            padding: '0.4rem 0.6rem',
                            borderRadius: '4px',
                            background: config.hcpQualifiedMode === opt.value ? '#f0f7ff' : 'transparent',
                            border: `1px solid ${config.hcpQualifiedMode === opt.value ? '#3498db' : 'transparent'}`,
                            transition: 'background 0.15s',
                          }}
                        >
                          <input
                            type="radio"
                            name="hcpQualifiedMode"
                            value={opt.value}
                            checked={config.hcpQualifiedMode === opt.value}
                            onChange={() => handleFieldChange('hcpQualifiedMode', opt.value)}
                            style={{ marginTop: '2px', flexShrink: 0 }}
                          />
                          <div>
                            <span style={{ fontSize: '0.88rem', fontWeight: 600, color: '#34495e' }}>{opt.label}</span>
                            <span style={{ display: 'block', fontSize: '0.78rem', color: '#7f8c8d', marginTop: '1px' }}>
                              {opt.description}
                            </span>
                          </div>
                        </label>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Otros */}
            <div style={{ marginTop: '1.5rem' }}>
              <h3 style={{ margin: '0 0 1rem', fontSize: '0.95rem', fontWeight: 600, color: '#34495e', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Otros
              </h3>
              <div style={{ display: 'grid', gap: '0.75rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <label style={{ flex: 1, fontSize: '0.9rem', color: '#555' }}>Puestos restantes</label>
                  <input
                    type="number"
                    min={0}
                    value={config.remainingPositionsPoints}
                    onChange={(e) => handleFieldChange('remainingPositionsPoints', parseInt(e.target.value) || 0)}
                    style={inputStyle}
                  />
                  <span style={{ fontSize: '0.85rem', color: '#7f8c8d', minWidth: '30px' }}>pts</span>
                </div>
              </div>
            </div>

            {/* Criterio de desempate */}
            <div style={{ marginTop: '1.5rem' }}>
              <h3 style={{ margin: '0 0 1rem', fontSize: '0.95rem', fontWeight: 600, color: '#34495e', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Criterio de desempate
              </h3>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <select
                  value={config.tieBreakMode}
                  onChange={(e) => handleFieldChange('tieBreakMode', e.target.value)}
                  style={{ ...inputStyle, flex: 1, width: 'auto' }}
                >
                  {TIE_BREAK_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
                {/* Tooltip con información del criterio seleccionado */}
                <div style={{ position: 'relative' }}>
                  <button
                    type="button"
                    onMouseEnter={() => setTooltipVisible(true)}
                    onMouseLeave={() => setTooltipVisible(false)}
                    style={{
                      background: '#3498db',
                      color: '#fff',
                      border: 'none',
                      borderRadius: '50%',
                      width: '22px',
                      height: '22px',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                      cursor: 'default',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    ?
                  </button>
                  {tooltipVisible && selectedTieBreak && (
                    <div
                      style={{
                        position: 'absolute',
                        bottom: '130%',
                        right: 0,
                        background: '#2c3e50',
                        color: '#fff',
                        borderRadius: '6px',
                        padding: '0.6rem 0.8rem',
                        fontSize: '0.8rem',
                        lineHeight: '1.4',
                        width: '260px',
                        zIndex: 100,
                        boxShadow: '0 2px 8px rgba(0,0,0,0.25)',
                      }}
                    >
                      <strong style={{ display: 'block', marginBottom: '0.25rem' }}>{selectedTieBreak.label}</strong>
                      {selectedTieBreak.tooltip}
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Columna derecha: puntos por posición */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
              <h3 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 600, color: '#34495e', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Puntos por posición
              </h3>
              <button
                type="button"
                onClick={handleAddPosition}
                style={{
                  background: '#3498db',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '4px',
                  padding: '0.35rem 0.85rem',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                + Agregar puesto
              </button>
            </div>

            {config.positionPoints.length === 0 ? (
              <div
                style={{
                  border: '2px dashed #dee2e6',
                  borderRadius: '6px',
                  padding: '1.5rem',
                  textAlign: 'center',
                  color: '#7f8c8d',
                  fontSize: '0.9rem',
                }}
              >
                No hay puestos configurados. Los jugadores recibirán los puntos de "puestos restantes".
              </div>
            ) : (
              <div style={{ border: '1px solid #e0e0e0', borderRadius: '6px', overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ background: '#f8f9fa' }}>
                      <th style={thStyle}>Puesto</th>
                      <th style={thStyle}>Puntos</th>
                      <th style={{ ...thStyle, width: '50px' }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {config.positionPoints
                      .slice()
                      .sort((a, b) => a.position - b.position)
                      .map((pp, index) => (
                        <tr key={index} style={{ borderTop: '1px solid #f0f0f0' }}>
                          <td style={tdStyle}>
                            <input
                              type="number"
                              min={1}
                              value={pp.position}
                              onChange={(e) => handlePositionChange(index, 'position', parseInt(e.target.value) || 1)}
                              style={{ ...inputStyle, width: '70px', textAlign: 'center' }}
                            />
                          </td>
                          <td style={tdStyle}>
                            <input
                              type="number"
                              min={0}
                              value={pp.points}
                              onChange={(e) => handlePositionChange(index, 'points', parseInt(e.target.value) || 0)}
                              style={{ ...inputStyle, width: '80px', textAlign: 'center' }}
                            />
                          </td>
                          <td style={{ ...tdStyle, textAlign: 'center' }}>
                            <button
                              type="button"
                              onClick={() => handleRemovePosition(index)}
                              style={{
                                background: 'none',
                                border: 'none',
                                color: '#e74c3c',
                                cursor: 'pointer',
                                fontSize: '1rem',
                                fontWeight: 700,
                                padding: '0.2rem 0.4rem',
                              }}
                              title="Eliminar puesto"
                            >
                              ✕
                            </button>
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            )}

            <div
              style={{
                marginTop: '0.75rem',
                padding: '0.6rem 0.8rem',
                background: '#f0f7ff',
                borderRadius: '4px',
                fontSize: '0.82rem',
                color: '#2980b9',
                lineHeight: '1.4',
              }}
            >
              Los jugadores en puestos no listados recibirán <strong>{config.remainingPositionsPoints} pts</strong> (configurable en "Puestos restantes").
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
          <button
            onClick={handleSave}
            disabled={saving}
            style={{
              background: saving ? '#bdc3c7' : '#27ae60',
              color: '#fff',
              border: 'none',
              borderRadius: '4px',
              padding: '0.6rem 1.5rem',
              fontWeight: 600,
              cursor: saving ? 'not-allowed' : 'pointer',
              fontSize: '0.95rem',
            }}
          >
            {saving ? 'Guardando...' : 'Guardar configuración'}
          </button>
        </div>

      <Modal
        isOpen={modalState.open}
        onClose={() => setModalState((s) => ({ ...s, open: false }))}
        title={modalState.type === 'success' ? 'Configuración guardada' : 'Error'}
        message={modalState.message}
        type={modalState.type}
      />
    </>
  );
};

const inputStyle: React.CSSProperties = {
  border: '1px solid #dee2e6',
  borderRadius: '4px',
  padding: '0.35rem 0.5rem',
  fontSize: '0.9rem',
  width: '70px',
  outline: 'none',
};

const thStyle: React.CSSProperties = {
  padding: '0.6rem 0.75rem',
  textAlign: 'left',
  fontSize: '0.82rem',
  fontWeight: 600,
  color: '#555',
  textTransform: 'uppercase',
  letterSpacing: '0.04em',
};

const tdStyle: React.CSSProperties = {
  padding: '0.5rem 0.75rem',
  fontSize: '0.9rem',
};

export default ScoringConfigSection;
