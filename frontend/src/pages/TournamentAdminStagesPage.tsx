import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createPortal } from 'react-dom';
import Modal from '../components/Modal';
import Table, { TableAction } from '../components/Table';
import { tournamentAdminService } from '../services/tournamentAdminService';
import { tournamentAdminStageService } from '../services/tournamentAdminStageService';
import { TournamentAdminDetail, TournamentAdminStage, TournamentRelationOption } from '../types';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';

const TournamentAdminStagesPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const tournamentAdminId = Number(id);

  const [adminDetail, setAdminDetail] = useState<TournamentAdminDetail | null>(null);
  const [stages, setStages] = useState<TournamentAdminStage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [showModal, setShowModal] = useState(false);
  const [editingStage, setEditingStage] = useState<TournamentAdminStage | null>(null);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    nombre: '',
    tournamentIds: [] as number[],
  });
  const [relationOptions, setRelationOptions] = useState<TournamentRelationOption[]>([]);
  const [showRelationsDropdown, setShowRelationsDropdown] = useState(false);
  const relationInputRef = useRef<HTMLDivElement | null>(null);
  const [relationsDropdownStyle, setRelationsDropdownStyle] = useState<React.CSSProperties>({});

  useEffect(() => {
    if (!Number.isFinite(tournamentAdminId)) {
      setError('ID de torneo inválido');
      setLoading(false);
      return;
    }
    loadData();
  }, [tournamentAdminId]);

  const loadData = async () => {
    try {
      setLoading(true);
      const [detail, stagesData] = await Promise.all([
        tournamentAdminService.getDetail(tournamentAdminId),
        tournamentAdminStageService.getAll(tournamentAdminId),
      ]);
      setAdminDetail(detail);
      setStages(stagesData);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando etapas');
    } finally {
      setLoading(false);
    }
  };

  const loadRelationOptions = async (stageId?: number) => {
    const options = await tournamentAdminStageService.getRelationOptions(tournamentAdminId, stageId);
    setRelationOptions(options);
  };

  const handleCreate = async () => {
    setEditingStage(null);
    setFormData({ nombre: '', tournamentIds: [] });
    setShowRelationsDropdown(false);
    await loadRelationOptions();
    setShowModal(true);
  };

  const handleEdit = async (stage: TournamentAdminStage) => {
    setEditingStage(stage);
    setFormData({
      nombre: stage.nombre,
      tournamentIds: [...stage.tournamentIds],
    });
    setShowRelationsDropdown(false);
    await loadRelationOptions(stage.id);
    setShowModal(true);
  };

  const handleSelectRelatedTournament = (tournamentId: number) => {
    setRelationOptions(prev => prev.map(opt => (
      opt.id === tournamentId ? { ...opt, related: true } : opt
    )));
    setFormData(prev => {
      if (prev.tournamentIds.includes(tournamentId)) return prev;
      return {
        ...prev,
        tournamentIds: [...prev.tournamentIds, tournamentId],
      };
    });
    setShowRelationsDropdown(false);
  };

  const handleRemoveRelatedTournament = (tournamentId: number) => {
    setRelationOptions(prev => prev.map(opt => (
      opt.id === tournamentId ? { ...opt, related: false } : opt
    )));
    setFormData(prev => ({
      ...prev,
      tournamentIds: prev.tournamentIds.filter(idValue => idValue !== tournamentId),
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setSaving(true);
      if (editingStage) {
        await tournamentAdminStageService.update(tournamentAdminId, editingStage.id, formData);
      } else {
        await tournamentAdminStageService.create(tournamentAdminId, formData);
      }
      setShowModal(false);
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error guardando etapa');
    } finally {
      setSaving(false);
    }
  };

  const handleViewDates = (stage: TournamentAdminStage) => {
    navigate(`/administration/${tournamentAdminId}/stages/${stage.id}`);
  };

  useEffect(() => {
    if (!showRelationsDropdown || !relationInputRef.current) return;

    const updatePosition = () => {
      if (!relationInputRef.current) return;
      const rect = relationInputRef.current.getBoundingClientRect();
      const availableAbove = Math.max(80, rect.top - 16);
      const maxHeight = Math.min(220, availableAbove);
      const bottom = Math.max(8, window.innerHeight - rect.top + 6);
      setRelationsDropdownStyle({
        position: 'fixed',
        left: rect.left,
        width: rect.width,
        bottom,
        maxHeight,
        zIndex: 20000,
      });
    };

    updatePosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [showRelationsDropdown]);

  const stageActions: TableAction<TournamentAdminStage>[] = [
    {
      label: 'Editar',
      onClick: handleEdit,
      variant: 'primary',
      icon: '✏️',
    },
    {
      label: 'Ver Fechas',
      onClick: handleViewDates,
      variant: 'secondary',
      icon: '📅',
    },
  ];

  const columns = [
    { header: 'Nombre de Etapa', accessor: (row: TournamentAdminStage) => row.nombre },
    { header: 'Cant. de Fechas', accessor: (row: TournamentAdminStage) => row.fechasCount },
    { header: 'Fecha Creación', accessor: (row: TournamentAdminStage) => formatDateSafe(row.createdAt) },
  ];

  if (loading) return <div className="loading">Cargando etapas...</div>;
  if (!adminDetail) return <div className="error-message">No se encontró el torneo administrativo</div>;

  const formatOptionLabel = (option: TournamentRelationOption) =>
    option.fechaInicio
      ? `${option.nombre} / ${formatDateSafe(option.fechaInicio)}`
      : option.nombre;

  return (
    <div className="players-page">
      <div className="page-header">
        <h1>Etapas - {adminDetail.nombre}</h1>
        <div className="header-actions">
          <button onClick={() => navigate(`/administration/${tournamentAdminId}`)} className="btn btn-secondary">
            ← Volver
          </button>
          <button onClick={loadData} className="btn btn-secondary">
            Actualizar
          </button>
          <button
            onClick={() => navigate(`/administration/${tournamentAdminId}/stages/playoff-results`)}
            className="btn btn-secondary"
            disabled={stages.length === 0}
            title={stages.length === 0 ? 'No hay etapas creadas' : undefined}
          >
            Resultados Play Off
          </button>
          <button
            onClick={handleCreate}
            className="btn btn-primary"
            disabled={!adminDetail.canManageStages}
          >
            + Crear Etapa
          </button>
        </div>
      </div>

      {!adminDetail.canManageStages && (
        <div className="error-message">
          Este torneo no tiene fechas FRUTALES relacionadas para administrar etapas.
        </div>
      )}

      {error && <div className="error-message">{error}</div>}

      <Table
        data={stages}
        columns={columns}
        actions={stageActions}
        emptyMessage="No hay etapas creadas"
      />

      <Modal
        isOpen={showModal}
        onClose={() => {
          setShowModal(false);
          setShowRelationsDropdown(false);
        }}
        title={editingStage ? 'Editar Etapa' : 'Crear Etapa'}
        size="medium"
        footer={
          <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
            <button
              type="button"
              onClick={() => {
                setShowModal(false);
                setShowRelationsDropdown(false);
              }}
              className="btn btn-secondary"
            >
              Cancelar
            </button>
            <button
              type="submit"
              form="stage-form"
              className="btn btn-primary"
              disabled={saving}
            >
              {saving ? 'Guardando...' : editingStage ? 'Actualizar' : 'Crear'}
            </button>
          </div>
        }
      >
        <form id="stage-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Nombre de Etapa *</label>
            <input
              type="text"
              value={formData.nombre}
              onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
              required
            />
          </div>

          <div className="form-group">
            <label>Torneos (Fechas) *</label>
            <div ref={relationInputRef} style={{ position: 'relative', zIndex: showRelationsDropdown ? 2000 : 'auto' }}>
              <button
                type="button"
                onClick={() => setShowRelationsDropdown(prev => !prev)}
                style={{
                  width: '100%',
                  padding: '0.75rem',
                  border: '2px solid #e0e0e0',
                  borderRadius: '6px',
                  fontSize: '1rem',
                  textAlign: 'left',
                  background: 'white',
                  cursor: 'pointer',
                }}
              >
                {relationOptions.filter(o => !o.related).length > 0
                  ? 'Seleccionar fechas...'
                  : 'No hay fechas disponibles'}
              </button>

              {showRelationsDropdown && createPortal(
                <div
                  style={{
                    ...relationsDropdownStyle,
                    background: 'white',
                    border: '1px solid #e0e0e0',
                    borderRadius: '6px',
                    overflowY: 'auto',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
                  }}
                >
                  {relationOptions.filter(o => !o.related).length === 0 ? (
                    <div style={{ padding: '0.75rem', color: '#666' }}>
                      No hay fechas disponibles
                    </div>
                  ) : (
                    relationOptions
                      .filter(o => !o.related)
                      .map(option => (
                        <button
                          key={option.id}
                          type="button"
                          onClick={() => handleSelectRelatedTournament(option.id)}
                          style={{
                            width: '100%',
                            textAlign: 'left',
                            padding: '0.65rem 0.75rem',
                            border: 'none',
                            background: 'white',
                            cursor: 'pointer',
                          }}
                        >
                          {formatOptionLabel(option)}
                        </button>
                      ))
                  )}
                </div>,
                document.body
              )}
            </div>

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginTop: '0.6rem' }}>
              {relationOptions
                .filter(o => o.related)
                .map(option => (
                  <span
                    key={option.id}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '0.4rem',
                      background: '#eef4ff',
                      color: '#1f4b99',
                      border: '1px solid #d8e6ff',
                      borderRadius: '999px',
                      padding: '0.2rem 0.6rem',
                      fontSize: '0.85rem',
                    }}
                  >
                    {formatOptionLabel(option)}
                    <button
                      type="button"
                      onClick={() => handleRemoveRelatedTournament(option.id)}
                      style={{
                        border: 'none',
                        background: 'transparent',
                        cursor: 'pointer',
                        color: '#1f4b99',
                        fontWeight: 700,
                        lineHeight: 1,
                      }}
                    >
                      ×
                    </button>
                  </span>
                ))}
            </div>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default TournamentAdminStagesPage;
