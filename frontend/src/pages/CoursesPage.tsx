import { useState, useEffect } from 'react';
import { courseService } from '../services/courseService';
import { locationService, Country, Province } from '../services/locationService';
import { Course } from '../types';
import Table from '../components/Table';
import Modal from '../components/Modal';
import ManageHolesModal from '../components/ManageHolesModal';
import ManageTeesModal from '../components/ManageTeesModal';
import '../components/Form.css';

const CoursesPage = () => {
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingCourse, setEditingCourse] = useState<Course | null>(null);
  const [formData, setFormData] = useState<Partial<Course>>({
    nombre: '',
    pais: '',
    provincia: '',
    ciudad: '',
    cantidadHoyos: 18,
    courseRating: undefined,
    slopeRating: undefined,
  });
  
  // Location data
  const [countries, setCountries] = useState<Country[]>([]);
  const [provinces, setProvinces] = useState<Province[]>([]);
  const [selectedCountryId, setSelectedCountryId] = useState<number | null>(null);
  const [loadingCountries, setLoadingCountries] = useState(false);
  const [loadingProvinces, setLoadingProvinces] = useState(false);

  useEffect(() => {
    loadCourses();
  }, []);

  const loadCourses = async () => {
    try {
      setLoading(true);
      const data = await courseService.getAll();
      setCourses(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error loading courses');
    } finally {
      setLoading(false);
    }
  };

  const loadCountries = async () => {
    try {
      setLoadingCountries(true);
      const data = await locationService.getCountries();
      setCountries(data);
    } catch (err: any) {
      console.error('Error loading countries:', err);
    } finally {
      setLoadingCountries(false);
    }
  };

  const loadProvinces = async (countryId: number) => {
    try {
      setLoadingProvinces(true);
      const data = await locationService.getProvincesByCountry(countryId);
      setProvinces(data);
    } catch (err: any) {
      console.error('Error loading provinces:', err);
      setProvinces([]);
    } finally {
      setLoadingProvinces(false);
    }
  };

  const handleCreate = async () => {
    setEditingCourse(null);
    setFormData({
      nombre: '',
      pais: '',
      provincia: '',
      ciudad: '',
      cantidadHoyos: 18,
      courseRating: undefined,
      slopeRating: undefined,
    });
    setSelectedCountryId(null);
    setProvinces([]);
    setShowModal(true);
    await loadCountries();
  };

  const handleEdit = async (course: Course) => {
    setEditingCourse(course);
    setFormData({
      nombre: course.nombre,
      pais: course.pais,
      provincia: course.provincia || '',
      ciudad: course.ciudad || '',
      cantidadHoyos: course.cantidadHoyos,
      courseRating: course.courseRating,
      slopeRating: course.slopeRating,
    });
    setShowModal(true);
    
    // Load countries first
    try {
      setLoadingCountries(true);
      const countriesData = await locationService.getCountries();
      setCountries(countriesData);
      
      // If editing, try to find and select the country, then load its provinces
      const countryMatch = countriesData.find(c => c.nombre === course.pais);
      if (countryMatch) {
        setSelectedCountryId(countryMatch.id);
        await loadProvinces(countryMatch.id);
      }
    } catch (err: any) {
      console.error('Error loading countries:', err);
    } finally {
      setLoadingCountries(false);
    }
  };

  const handleCountryChange = async (countryId: string) => {
    const id = parseInt(countryId);
    setSelectedCountryId(id);
    
    const country = countries.find(c => c.id === id);
    if (country) {
      setFormData({ ...formData, pais: country.nombre, provincia: '' });
      await loadProvinces(id);
    }
  };

  const handleProvinceChange = (provinceName: string) => {
    setFormData({ ...formData, provincia: provinceName });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingCourse) {
        await courseService.update(editingCourse.id, formData);
      } else {
        await courseService.create(formData);
      }
      setShowModal(false);
      loadCourses();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error saving course');
    }
  };

  const handleDelete = async (course: Course) => {
    if (!confirm(`Are you sure you want to delete ${course.nombre}?`)) return;
    try {
      await courseService.delete(course.id);
      loadCourses();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error deleting course');
    }
  };

  const [showHolesModal, setShowHolesModal] = useState(false);
  const [showTeesModal, setShowTeesModal] = useState(false);
  const [selectedCourseForHoles, setSelectedCourseForHoles] = useState<Course | null>(null);
  const [selectedCourseForTees, setSelectedCourseForTees] = useState<Course | null>(null);

  const handleManageHoles = (course: Course) => {
    setSelectedCourseForHoles(course);
    setShowHolesModal(true);
  };

  const handleManageTees = (course: Course) => {
    setSelectedCourseForTees(course);
    setShowTeesModal(true);
  };

  const columns = [
    { header: 'Name', accessor: 'nombre' as keyof Course },
    { header: 'Location', accessor: (row: Course) => `${row.ciudad || ''}, ${row.provincia || ''}, ${row.pais}` },
    { header: 'Holes', accessor: 'cantidadHoyos' as keyof Course },
    { header: 'Rating', accessor: (row: Course) => row.courseRating || '-' },
    { header: 'Slope', accessor: (row: Course) => row.slopeRating || '-' },
  ];

  const customActions = (course: Course) => (
    <>
      <button onClick={() => handleEdit(course)} className="btn-edit">
        Edit
      </button>
      <button onClick={() => handleManageTees(course)} className="btn btn-secondary" style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem', backgroundColor: '#9b59b6' }}>
        Manage Tees
      </button>
      <button onClick={() => handleManageHoles(course)} className="btn btn-secondary" style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem' }}>
        Manage Holes
      </button>
      <button onClick={() => handleDelete(course)} className="btn-delete">
        Delete
      </button>
    </>
  );

  if (loading) return <div className="loading">Loading courses...</div>;

  return (
    <div>
      <div className="page-header">
        <h1>Golf Courses</h1>
        <button onClick={handleCreate} className="btn btn-primary">
          Create Course
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}

      <Table data={courses} columns={columns} customActions={customActions} />

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingCourse ? 'Edit Course' : 'Create Course'}
      >
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Course Name *</label>
            <input
              type="text"
              value={formData.nombre}
              onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
              required
            />
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Country *</label>
              <select
                value={selectedCountryId || ''}
                onChange={(e) => handleCountryChange(e.target.value)}
                required
                disabled={loadingCountries}
              >
                <option value="">
                  {loadingCountries ? 'Loading...' : 'Select a country'}
                </option>
                {countries.map((country) => (
                  <option key={country.id} value={country.id}>
                    {country.nombre}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>Province/State</label>
              <select
                value={formData.provincia || ''}
                onChange={(e) => handleProvinceChange(e.target.value)}
                disabled={!selectedCountryId || loadingProvinces}
              >
                <option value="">
                  {loadingProvinces ? 'Loading...' : selectedCountryId ? 'Select a province' : 'Select a country first'}
                </option>
                {provinces.map((province) => (
                  <option key={province.id} value={province.nombre}>
                    {province.nombre}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>City</label>
              <input
                type="text"
                value={formData.ciudad}
                onChange={(e) => setFormData({ ...formData, ciudad: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label>Number of Holes *</label>
              <select
                value={formData.cantidadHoyos}
                onChange={(e) => setFormData({ ...formData, cantidadHoyos: parseInt(e.target.value) })}
                required
              >
                <option value={9}>9 Holes</option>
                <option value={18}>18 Holes</option>
              </select>
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Course Rating</label>
              <input
                type="number"
                step="0.1"
                value={formData.courseRating || ''}
                onChange={(e) => setFormData({ ...formData, courseRating: e.target.value ? parseFloat(e.target.value) : undefined })}
              />
            </div>
            <div className="form-group">
              <label>Slope Rating</label>
              <input
                type="number"
                value={formData.slopeRating || ''}
                onChange={(e) => setFormData({ ...formData, slopeRating: e.target.value ? parseInt(e.target.value) : undefined })}
              />
            </div>
          </div>

          <div className="form-actions">
            <button type="button" onClick={() => setShowModal(false)} className="btn btn-cancel">
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              {editingCourse ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </Modal>

      {selectedCourseForTees && (
        <ManageTeesModal
          isOpen={showTeesModal}
          onClose={() => {
            setShowTeesModal(false);
            setSelectedCourseForTees(null);
          }}
          course={selectedCourseForTees}
          onSave={() => {
            loadCourses();
            setShowTeesModal(false);
            setSelectedCourseForTees(null);
          }}
        />
      )}

      {selectedCourseForHoles && (
        <ManageHolesModal
          isOpen={showHolesModal}
          onClose={() => {
            setShowHolesModal(false);
            setSelectedCourseForHoles(null);
          }}
          course={selectedCourseForHoles}
          onSave={() => {
            loadCourses();
            setShowHolesModal(false);
            setSelectedCourseForHoles(null);
          }}
        />
      )}
    </div>
  );
};

export default CoursesPage;
