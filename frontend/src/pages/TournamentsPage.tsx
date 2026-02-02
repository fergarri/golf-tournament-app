import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { tournamentService } from '../services/tournamentService';
import { courseService } from '../services/courseService';
import { Tournament, Course, TournamentCategory } from '../types';
import Table from '../components/Table';
import Modal from '../components/Modal';
import ManualInscriptionModal from '../components/ManualInscriptionModal';
import '../components/Form.css';

const TournamentsPage = () => {
  const navigate = useNavigate();
  const [tournaments, setTournaments] = useState<Tournament[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [showLinkModal, setShowLinkModal] = useState(false);
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
    teeConfig: {
      courseTeeIdPrimeros9: '',
      courseTeeIdSegundos9: '',
    },
    categories: [{ nombre: 'General', handicapMin: 0, handicapMax: 54 }],
  });

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
    alert('Link copied to clipboard!');
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

  const removeCategory = (index: number) => {
    setFormData({
      ...formData,
      categories: formData.categories.filter((_: any, i: number) => i !== index),
    });
  };

  const selectedCourse = courses.find((c) => c.id === parseInt(formData.courseId));

  const columns = [
    { header: 'Name', accessor: 'nombre' as keyof Tournament },
    { header: 'Code', accessor: 'codigo' as keyof Tournament },
    { header: 'Course', accessor: 'courseName' as keyof Tournament },
    { header: 'Type', accessor: 'tipo' as keyof Tournament },
    { header: 'Start Date', accessor: (row: Tournament) => new Date(row.fechaInicio).toLocaleDateString() },
    { header: 'Inscribed', accessor: (row: Tournament) => `${row.currentInscriptos}${row.limiteInscriptos ? `/${row.limiteInscriptos}` : ''}` },
    { 
      header: 'Status', 
      accessor: (row: Tournament) => (
        <span className={`status-badge status-${row.estado?.toLowerCase()}`}>
          {row.estado || 'PENDING'}
        </span>
      ),
    },
  ];

  const customActions = (tournament: Tournament) => (
    <>
      <button 
        onClick={() => handleInscribePlayers(tournament)} 
        className="btn btn-secondary"
        style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem', backgroundColor: '#f39c12' }}
      >
        Inscribe
      </button>
      {tournament.estado === 'PENDING' && (
        <button 
          onClick={() => handleStartTournament(tournament)} 
          className="btn btn-success"
          style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem', backgroundColor: '#27ae60' }}
        >
          Start
        </button>
      )}
      {tournament.estado === 'IN_PROGRESS' && (
        <>
          <button 
            onClick={() => copyLink(getPlayLink(tournament.codigo))} 
            className="btn btn-secondary"
            style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem', backgroundColor: '#9b59b6' }}
          >
            Copy Link
          </button>
          <button 
            onClick={() => handleViewLeaderboard(tournament)} 
            className="btn btn-secondary"
            style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem' }}
          >
            Leaderboard
          </button>
        </>
      )}
      <button onClick={() => handleEdit(tournament)} className="btn-edit">
        Edit
      </button>
      <button onClick={() => handleDelete(tournament)} className="btn-delete">
        Delete
      </button>
    </>
  );

  if (loading) return <div className="loading">Loading tournaments...</div>;

  return (
    <div>
      <div className="page-header">
        <h1>Tournaments</h1>
        <button onClick={handleCreate} className="btn btn-primary">
          Create Tournament
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}

      <Table data={tournaments} columns={columns} customActions={customActions} />

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingTournament ? 'Edit Tournament' : 'Create Tournament'}
        size="large"
      >
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Tournament Name *</label>
            <input
              type="text"
              value={formData.nombre}
              onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
              required
            />
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Type *</label>
              <select
                value={formData.tipo}
                onChange={(e) => setFormData({ ...formData, tipo: e.target.value })}
                required
              >
                <option value="CLASICO">Classic</option>
                <option value="ANUAL">Annual</option>
              </select>
            </div>
            <div className="form-group">
              <label>Scoring Modality *</label>
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
            <label>Golf Course *</label>
            <select
              value={formData.courseId}
              onChange={(e) => setFormData({ ...formData, courseId: e.target.value })}
              required
            >
              <option value="">Select a course</option>
              {courses.map((course) => (
                <option key={course.id} value={course.id}>
                  {course.nombre} ({course.cantidadHoyos} holes)
                </option>
              ))}
            </select>
          </div>

          {selectedCourse && selectedCourse.tees && selectedCourse.tees.length > 0 && (
            <div className="form-row">
              <div className="form-group">
                <label>Tee for First 9 Holes *</label>
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
                  <option value="">Select tee</option>
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
                <label>Tee for Second 9 Holes</label>
                <select
                  value={formData.teeConfig.courseTeeIdSegundos9}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      teeConfig: { ...formData.teeConfig, courseTeeIdSegundos9: e.target.value },
                    })
                  }
                >
                  <option value="">Same as first 9</option>
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
              <label>Start Date *</label>
              <input
                type="date"
                value={formData.fechaInicio}
                onChange={(e) => setFormData({ ...formData, fechaInicio: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label>End Date</label>
              <input
                type="date"
                value={formData.fechaFin}
                onChange={(e) => setFormData({ ...formData, fechaFin: e.target.value })}
              />
            </div>
          </div>

          <div className="form-group">
            <label>Maximum Inscriptions</label>
            <input
              type="number"
              value={formData.limiteInscriptos}
              onChange={(e) => setFormData({ ...formData, limiteInscriptos: e.target.value })}
              placeholder="Leave empty for unlimited"
            />
          </div>

          <div className="form-group">
            <label>Categories</label>
            {formData.categories.map((category: TournamentCategory, index: number) => (
              <div key={index} className="category-row" style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
                <input
                  type="text"
                  placeholder="Category name"
                  value={category.nombre}
                  onChange={(e) => updateCategory(index, 'nombre', e.target.value)}
                  required
                  style={{ flex: 2 }}
                />
                <input
                  type="number"
                  step="0.1"
                  placeholder="Min"
                  value={category.handicapMin}
                  onChange={(e) => updateCategory(index, 'handicapMin', parseFloat(e.target.value))}
                  required
                  style={{ flex: 1 }}
                />
                <input
                  type="number"
                  step="0.1"
                  placeholder="Max"
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
                    Remove
                  </button>
                )}
              </div>
            ))}
            <button type="button" onClick={addCategory} className="btn btn-secondary" style={{ marginTop: '0.5rem' }}>
              Add Category
            </button>
          </div>

          <div className="form-actions">
            <button type="button" onClick={() => setShowModal(false)} className="btn btn-cancel">
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              {editingTournament ? 'Update' : 'Create'}
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
          title="Tournament Created Successfully"
          size="medium"
        >
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>✓</div>
            <h3 style={{ marginBottom: '1rem', color: '#2c3e50' }}>
              {createdTournament.nombre}
            </h3>
            <p style={{ color: '#7f8c8d', marginBottom: '1.5rem' }}>
              Tournament Code: <strong style={{ color: '#2c3e50', fontFamily: 'monospace', fontSize: '1.2rem' }}>{createdTournament.codigo}</strong>
            </p>
            
            <div style={{ marginBottom: '2rem' }}>
              <h4 style={{ marginBottom: '0.5rem', color: '#2c3e50' }}>Inscription Link</h4>
              <p style={{ color: '#7f8c8d', fontSize: '0.9rem', marginBottom: '0.5rem' }}>
                Share this link with players to inscribe:
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
                Copy Inscription Link
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
          title="Tournament Started"
          size="medium"
        >
          <div style={{ textAlign: 'center' }}>
            <div className="success-icon" style={{ fontSize: '3rem', color: '#27ae60', marginBottom: '1rem' }}>
              ✓
            </div>
            <h3 style={{ marginBottom: '1rem', color: '#2c3e50' }}>
              {selectedTournamentForLink.nombre} is now active!
            </h3>
            <p style={{ color: '#7f8c8d', marginBottom: '1.5rem' }}>
              Share this link with players so they can access their scorecards:
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
              Copy Link to Clipboard
            </button>
            <p style={{ color: '#7f8c8d', fontSize: '0.9rem' }}>
              Players will need to enter their registration number to access their scorecard
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
