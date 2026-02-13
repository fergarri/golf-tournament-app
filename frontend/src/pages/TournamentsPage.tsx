import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { courseService } from '../services/courseService';
import { Tournament, Course, TournamentCategory } from '../types';
import Table, { TableAction } from '../components/Table';
import Modal from '../components/Modal';
import ManualInscriptionModal from '../components/ManualInscriptionModal';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';

const TournamentsPage = () => {
  const navigate = useNavigate();
  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [showCopyLinkModal, setShowCopyLinkModal] = useState(false);
  const [showInscriptionModal, setShowInscriptionModal] = useState(false);
  const [showCreatedModal, setShowCreatedModal] = useState(false);
  const [selectedTournamentForLink, setSelectedTournamentForLink] = useState<Tournament | null>(null);
  const [selectedTournamentForInscription, setSelectedTournamentForInscription] = useState<Tournament | null>(null);
  const [createdTournament, setCreatedTournament] = useState<Tournament | null>(null);
  const [editingTournament, setEditingTournament] = useState<Tournament | null>(null);
  const [formData, setFormData] = useState<any>({
    nombre: '',
    tipo: 'CLASICO',
    modalidad: 'MEDAL_PLAY',
    courseId: '',
    fechaInicio: '',
    fechaFin: '',
    limiteInscriptos: '',
    valorInscripcion: '',
    teeConfig: {
      courseTeeIdPrimeros9: '',
      courseTeeIdSegundos9: '',
    },
    categories: [{ nombre: 'General', handicapMin: 0, handicapMax: 54 }],
  });

  // Función para formatear número a formato argentino/europeo (punto miles, coma decimales)
  const formatCurrency = (value: string | number): string => {
    if (!value && value !== 0) return '';
    
    // Si ya es un número, convertirlo directamente
    let num: number;
    if (typeof value === 'number') {
      num = value;
    } else {
      // Si es string, parsearlo (viene del backend en formato estándar: 10500.15)
      num = parseFloat(String(value));
    }
    
    if (isNaN(num)) return '';
    
    // Formatear con separador de miles (punto) y decimales (coma)
    return num.toLocaleString('es-AR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  };

  // Función para parsear formato argentino/europeo a número
  const parseCurrency = (formatted: string): number | null => {
    if (!formatted) return null;
    
    // Remover puntos (separador de miles) y reemplazar coma por punto (separador decimal)
    const cleaned = formatted.replace(/\./g, '').replace(/,/g, '.');
    const num = parseFloat(cleaned);
    
    return isNaN(num) ? null : num;
  };

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [tournamentsData, coursesData] = await Promise.all([
        tournamentService.getAll(),
        courseService.getAll(),
      ]);
      setTournaments(tournamentsData);
      setCourses(coursesData);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading data');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingTournament(null);
    setFormData({
      nombre: '',
      tipo: 'CLASICO',
      modalidad: 'MEDAL_PLAY',
      courseId: courses.length > 0 ? courses[0].id : '',
      fechaInicio: '',
      fechaFin: '',
      limiteInscriptos: '',
      teeConfig: {
        courseTeeIdPrimeros9: '',
        courseTeeIdSegundos9: '',
      },
      categories: [{ nombre: 'General', handicapMin: 0, handicapMax: 54 }],
    });
    setShowModal(true);
  };

  const handleEdit = (tournament: Tournament) => {
    setEditingTournament(tournament);
    setFormData({
      nombre: tournament.nombre,
      tipo: tournament.tipo,
      modalidad: tournament.modalidad,
      courseId: tournament.courseId,
      fechaInicio: tournament.fechaInicio,
      fechaFin: tournament.fechaFin || '',
      limiteInscriptos: tournament.limiteInscriptos || '',
      valorInscripcion: tournament.valorInscripcion ? formatCurrency(tournament.valorInscripcion) : '',
      teeConfig: tournament.teeConfig,
      categories: tournament.categories,
    });
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        ...formData,
        courseId: parseInt(formData.courseId),
        limiteInscriptos: formData.limiteInscriptos ? parseInt(formData.limiteInscriptos) : null,
        valorInscripcion: parseCurrency(formData.valorInscripcion),
        teeConfig: {
          courseTeeIdPrimeros9: parseInt(formData.teeConfig.courseTeeIdPrimeros9),
          courseTeeIdSegundos9: formData.teeConfig.courseTeeIdSegundos9
            ? parseInt(formData.teeConfig.courseTeeIdSegundos9)
            : null,
        },
      };

      if (editingTournament) {
        await tournamentService.update(editingTournament.id, payload);
        setShowModal(false);
      } else {
        const created = await tournamentService.create(payload);
        setCreatedTournament(created);
        setShowModal(false);
        setShowCreatedModal(true);
      }
      loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error saving tournament');
    }
  };

  const handleInscribePlayers = (tournament: Tournament) => {
    setSelectedTournamentForInscription(tournament);
    setShowInscriptionModal(true);
  };

  const getInscriptionLink = (codigo: string) => {
    return `${window.location.origin}/inscribe/${codigo}`;
  };

  const handleDelete = async (tournament: Tournament) => {
    if (!confirm(`Are you sure you want to delete ${tournament.nombre}?`)) return;
    try {
      await tournamentService.delete(tournament.id);
      loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error deleting tournament');
    }
  };

  const handleStartTournament = async (tournament: Tournament) => {
    try {
      await tournamentService.start(tournament.id);
      setSelectedTournamentForLink(tournament);
      setShowLinkModal(true);
      loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error starting tournament');
    }
  };

  const handleViewLeaderboard = (tournament: Tournament) => {
    navigate(`/tournaments/${tournament.id}/leaderboard`);
  };

  const copyLink = (link: string) => {
    navigator.clipboard.writeText(link);
    setShowCopyLinkModal(true);
  };

  const getPlayLink = (codigo: string) => {
    return `${window.location.origin}/play/${codigo}`;
  };

  const addCategory = () => {
    setFormData({
      ...formData,
      categories: [...formData.categories, { nombre: '', handicapMin: 0, handicapMax: 54 }],
    });
  };

  const updateCategory = (index: number, field: keyof TournamentCategory, value: any) => {
    const newCategories = [...formData.categories];
    newCategories[index] = { ...newCategories[index], [field]: value };
    setFormData({ ...formData, categories: newCategories });
  };

  // Manejador especial para el input de valor de inscripción con formato automático
  const handleValorInscripcionChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const input = e.target.value;
    
    // Permitir solo números, punto, coma y teclas de control
    const cleaned = input.replace(/[^\d,]/g, '');
    
    // Si está vacío, actualizar con string vacío
    if (!cleaned) {
      setFormData({ ...formData, valorInscripcion: '' });
      return;
    }
    
    // Separar parte entera y decimal
    const parts = cleaned.split(',');
    let integerPart = parts[0];
    let decimalPart = parts[1] || '';
    
    // Limitar decimales a 2 dígitos
    if (decimalPart.length > 2) {
      decimalPart = decimalPart.substring(0, 2);
    }
    
    // Formatear parte entera con separador de miles (punto)
    integerPart = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    
    // Construir valor formateado
    let formatted = integerPart;
    if (parts.length > 1) {
      formatted += ',' + decimalPart;
    }
    
    setFormData({ ...formData, valorInscripcion: formatted });
  };

  const removeCategory = (index: number) => {
    setFormData({
      ...formData,
      categories: formData.categories.filter((_: any, i: number) => i !== index),
    });
  };

  const selectedCourse = courses.find((c) => c.id === parseInt(formData.courseId));

  const columns = [
    { header: 'Nombre', accessor: 'nombre' as keyof Tournament },
    { header: 'Código', accessor: 'codigo' as keyof Tournament },
    { header: 'Campo', accessor: 'courseName' as keyof Tournament },
    { header: 'Tipo', accessor: 'tipo' as keyof Tournament },
    { header: 'Fecha Inicio', accessor: (row: Tournament) => formatDateSafe(row.fechaInicio) },
    { header: 'Inscriptos', accessor: (row: Tournament) => `${row.currentInscriptos}${row.limiteInscriptos ? `/${row.limiteInscriptos}` : ''}` },
    { 
      header: 'Estado', 
      accessor: (row: Tournament) => (
        <span className={`status-badge status-${row.estado?.toLowerCase()}`}>
          {row.estado || 'PENDIENTE'}
        </span>
      ),
    },
  ];

  const tournamentActions: TableAction<Tournament>[] = [
    {
      label: 'Inscribir',
      onClick: handleInscribePlayers,
      variant: 'secondary',
    },
    {
      label: 'Link Inscripción',
      onClick: (tournament) => copyLink(getInscriptionLink(tournament.codigo)),
      variant: 'secondary',
      show: (tournament) => tournament.estado === 'PENDING',
    },
    {
      label: 'Iniciar Torneo',
      onClick: handleStartTournament,
      variant: 'primary',
      show: (tournament) => tournament.estado === 'PENDING',
    },
    {
      label: 'Link Inscripción',
      onClick: (tournament) => copyLink(getInscriptionLink(tournament.codigo)),
      variant: 'secondary',
      show: (tournament) => tournament.estado === 'IN_PROGRESS',
    },
    {
      label: 'Link Tarjetas',
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
      label: 'Editar',
      onClick: handleEdit,
      variant: 'default',
    },
    {
      label: 'Eliminar',
      onClick: handleDelete,
      variant: 'danger',
    },
  ];

  if (loading) return <div className="loading">Cargando torneos...</div>;

  return (
    <div>
      <div className="page-header">
        <h1>Torneos</h1>
        <button onClick={handleCreate} className="btn btn-primary">
          Crear Torneo
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}

      <Table data={tournaments} columns={columns} actions={tournamentActions} />


      <Modal
        isOpen={showCopyLinkModal}
        onClose={() => setShowCopyLinkModal(false)}
        title="Link Copiado"
        size="medium"
      >
        <div>
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            <div style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>✓</div>
            <p>Link copiado al portapapeles</p>
          </div>
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            <button onClick={() => setShowCopyLinkModal(false)} className="btn btn-primary">
              Cerrar
            </button>
          </div>
        </div>
      </Modal>

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingTournament ? 'Editar Torneo' : 'Crear Torneo'}
        size="large"
        footer={
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            <button type="button" onClick={() => setShowModal(false)} className="btn btn-cancel">
              Cancelar
            </button>
            <button type="submit" form="tournament-form" className="btn btn-primary">
              {editingTournament ? 'Actualizar' : 'Crear'}
            </button>
          </div>
        }
      >
        <form id="tournament-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Nombre del Torneo *</label>
            <input
              type="text"
              value={formData.nombre}
              onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
              required
            />
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Tipo *</label>
              <select
                value={formData.tipo}
                onChange={(e) => setFormData({ ...formData, tipo: e.target.value })}
                required
              >
                <option value="CLASICO">Clásico</option>
                <option value="ANUAL">Anual</option>
              </select>
            </div>
            <div className="form-group">
              <label>Modalidad de Puntuación *</label>
              <select
                value={formData.modalidad}
                onChange={(e) => setFormData({ ...formData, modalidad: e.target.value })}
                required
              >
                <option value="MEDAL_PLAY">Medal Play</option>
              </select>
            </div>
          </div>

          <div className="form-group">
            <label>Campo de Golf *</label>
            <select
              value={formData.courseId}
              onChange={(e) => setFormData({ ...formData, courseId: e.target.value })}
              required
            >
              <option value="">Seleccionar un campo</option>
              {courses.map((course) => (
                <option key={course.id} value={course.id}>
                  {course.nombre} ({course.cantidadHoyos} hoyos)
                </option>
              ))}
            </select>
          </div>

          {selectedCourse && selectedCourse.tees && selectedCourse.tees.length > 0 && (
            <div className="form-row">
              <div className="form-group">
                <label>Tee para los primeros 9 hoyos *</label>
                <select
                  value={formData.teeConfig.courseTeeIdPrimeros9}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      teeConfig: { ...formData.teeConfig, courseTeeIdPrimeros9: e.target.value },
                    })
                  }
                  required
                >
                  <option value="">Seleccionar tee</option>
                  {selectedCourse.tees
                    .filter((tee) => tee.active)
                    .map((tee) => (
                      <option key={tee.id} value={tee.id}>
                        {tee.nombre} {tee.grupo ? `(${tee.grupo})` : ''}
                      </option>
                    ))}
                </select>
              </div>
              <div className="form-group">
                <label>Tee para los segundos 9 hoyos</label>
                <select
                  value={formData.teeConfig.courseTeeIdSegundos9}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      teeConfig: { ...formData.teeConfig, courseTeeIdSegundos9: e.target.value },
                    })
                  }
                >
                  <option value="">Igual que los primeros 9</option>
                  {selectedCourse.tees
                    .filter((tee) => tee.active)
                    .map((tee) => (
                      <option key={tee.id} value={tee.id}>
                        {tee.nombre} {tee.grupo ? `(${tee.grupo})` : ''}
                      </option>
                    ))}
                </select>
              </div>
            </div>
          )}

          <div className="form-row">
            <div className="form-group">
              <label>Fecha de Inicio *</label>
              <input
                type="date"
                value={formData.fechaInicio}
                onChange={(e) => setFormData({ ...formData, fechaInicio: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label>Fecha de Fin</label>
              <input
                type="date"
                value={formData.fechaFin}
                onChange={(e) => setFormData({ ...formData, fechaFin: e.target.value })}
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Máximo de Inscriptos</label>
              <input
                type="number"
                value={formData.limiteInscriptos}
                onChange={(e) => setFormData({ ...formData, limiteInscriptos: e.target.value })}
                placeholder="Dejar vacío para ilimitado"
              />
            </div>

            <div className="form-group">
              <label>Valor de Inscripción</label>
              <input
                type="text"
                value={formData.valorInscripcion}
                onChange={handleValorInscripcionChange}
                placeholder="Ej: 1.500,00"
                inputMode="decimal"
              />
            </div>
          </div>

          <div className="form-group">
            <label>Categorías</label>
            {formData.categories.map((category: TournamentCategory, index: number) => (
              <div key={index} className="category-row" style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
                <input
                  type="text"
                  placeholder="Nombre de la categoría"
                  value={category.nombre}
                  onChange={(e) => updateCategory(index, 'nombre', e.target.value)}
                  required
                  style={{ flex: 2 }}
                />
                <input
                  type="number"
                  step="0.1"
                  placeholder="Mínimo"
                  value={category.handicapMin}
                  onChange={(e) => updateCategory(index, 'handicapMin', parseFloat(e.target.value))}
                  required
                  style={{ flex: 1 }}
                />
                <input
                  type="number"
                  step="0.1"
                  placeholder="Máximo"
                  value={category.handicapMax}
                  onChange={(e) => updateCategory(index, 'handicapMax', parseFloat(e.target.value))}
                  required
                  style={{ flex: 1 }}
                />
                {formData.categories.length > 1 && (
                  <button
                    type="button"
                    onClick={() => removeCategory(index)}
                    className="btn btn-danger"
                    style={{ padding: '0.75rem' }}
                  >
                    Eliminar
                  </button>
                )}
              </div>
            ))}
            <button type="button" onClick={addCategory} className="btn btn-secondary" style={{ marginTop: '0.5rem' }}>
              Agregar Categoría
            </button>
          </div>
        </form>
      </Modal>

      {createdTournament && (
        <Modal
          isOpen={showCreatedModal}
          onClose={() => {
            setShowCreatedModal(false);
            setCreatedTournament(null);
          }}
          title="Torneo Creado Exitosamente"
          size="medium"
        >
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>✓</div>
            <h3 style={{ marginBottom: '1rem', color: '#2c3e50' }}>
              {createdTournament.nombre}
            </h3>
            <p style={{ color: '#7f8c8d', marginBottom: '1.5rem' }}>
              Código del Torneo: <strong style={{ color: '#2c3e50', fontFamily: 'monospace', fontSize: '1.2rem' }}>{createdTournament.codigo}</strong>
            </p>
            
            <div style={{ marginBottom: '2rem' }}>
              <h4 style={{ marginBottom: '0.5rem', color: '#2c3e50' }}>Link de Inscripción</h4>
              <p style={{ color: '#7f8c8d', fontSize: '0.9rem', marginBottom: '0.5rem' }}>
                Comparte este link con los jugadores para inscribirse:
              </p>
              <div style={{ backgroundColor: '#f8f9fa', padding: '1rem', borderRadius: '6px', marginBottom: '0.5rem', wordBreak: 'break-all' }}>
                <code style={{ fontSize: '0.9rem', color: '#2c3e50' }}>
                  {getInscriptionLink(createdTournament.codigo)}
                </code>
              </div>
              <button 
                onClick={() => copyLink(getInscriptionLink(createdTournament.codigo))} 
                className="btn btn-secondary"
              >
                Copiar Link de Inscripción
              </button>
            </div>
          </div>
        </Modal>
      )}

      {selectedTournamentForLink && (
        <Modal
          isOpen={showLinkModal}
          onClose={() => {
            setShowLinkModal(false);
            setSelectedTournamentForLink(null);
          }}
          title="Torneo Iniciado"
          size="medium"
        >
          <div style={{ textAlign: 'center' }}>
            <div className="success-icon" style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>
              ✓
            </div>
            <h3 style={{ marginBottom: '1rem', color: '#2c3e50' }}>
              {selectedTournamentForLink.nombre} está ahora activo!
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
                {getPlayLink(selectedTournamentForLink.codigo)}
              </code>
            </div>
            <button 
              onClick={() => copyLink(getPlayLink(selectedTournamentForLink.codigo))} 
              className="btn btn-primary"
              style={{ marginBottom: '1rem' }}
            >
              Copiar Link al Portapapeles
            </button>
            <p style={{ color: '#7f8c8d', fontSize: '0.9rem' }}>
              Los jugadores necesitarán ingresar su número de matrícula para acceder a su tarjeta de puntuación
            </p>
          </div>
        </Modal>
      )}

      {selectedTournamentForInscription && (
        <ManualInscriptionModal
          isOpen={showInscriptionModal}
          onClose={() => {
            setShowInscriptionModal(false);
            setSelectedTournamentForInscription(null);
          }}
          tournament={selectedTournamentForInscription}
          onSuccess={() => {
            loadData();
            setShowInscriptionModal(false);
            setSelectedTournamentForInscription(null);
          }}
        />
      )}
    </div>
  );
};

export default TournamentsPage;
