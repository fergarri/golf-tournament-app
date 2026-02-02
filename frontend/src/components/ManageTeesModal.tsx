import { useState, useEffect } from 'react';
import { Course, CourseTee } from '../types';
import { courseService } from '../services/courseService';
import Modal from './Modal';
import Table from './Table';
import '../components/Form.css';

interface ManageTeesModalProps {
  isOpen: boolean;
  onClose: () => void;
  course: Course;
  onSave: () => void;
}

const ManageTeesModal = ({ isOpen, onClose, course, onSave }: ManageTeesModalProps) => {
  const [tees, setTees] = useState<CourseTee[]>([]);
  const [loading, setLoading] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [editingTee, setEditingTee] = useState<CourseTee | null>(null);
  const [formData, setFormData] = useState({
    nombre: '',
    grupo: '',
  });

  useEffect(() => {
    if (course && isOpen) {
      loadTees();
    }
  }, [course, isOpen]);

  const loadTees = async () => {
    try {
      setLoading(true);
      const teesData = await courseService.getTees(course.id);
      setTees(teesData);
    } catch (err) {
      console.error('Error loading tees:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingTee(null);
    setFormData({ nombre: '', grupo: '' });
    setShowForm(true);
  };

  const handleEdit = (tee: CourseTee) => {
    setEditingTee(tee);
    setFormData({
      nombre: tee.nombre,
      grupo: tee.grupo || '',
    });
    setShowForm(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingTee) {
        await courseService.updateTee(editingTee.id, formData);
      } else {
        await courseService.addTee(course.id, formData);
      }
      setShowForm(false);
      loadTees();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Error saving tee');
    }
  };

  const handleClose = () => {
    onSave();
    onClose();
  };

  const handleDeactivate = async (tee: CourseTee) => {
    if (!confirm(`Are you sure you want to deactivate ${tee.nombre}?`)) return;
    try {
      await courseService.deactivateTee(tee.id);
      loadTees();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Error deactivating tee');
    }
  };

  const columns = [
    { header: 'Tee Name', accessor: 'nombre' as keyof CourseTee },
    { header: 'Group/Color', accessor: (row: CourseTee) => row.grupo || '-' },
    {
      header: 'Status',
      accessor: (row: CourseTee) => (
        <span className={row.active ? 'status-active' : 'status-inactive'}>
          {row.active ? 'Active' : 'Inactive'}
        </span>
      ),
    },
  ];

  const customActions = (tee: CourseTee) => (
    <>
      <button onClick={() => handleEdit(tee)} className="btn-edit">
        Edit
      </button>
      {tee.active && (
        <button onClick={() => handleDeactivate(tee)} className="btn btn-danger" style={{ padding: '0.375rem 0.75rem', fontSize: '0.875rem' }}>
          Deactivate
        </button>
      )}
    </>
  );

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={`Manage Tees - ${course?.nombre}`} size="large">
      <div style={{ minHeight: '400px' }}>
        <div style={{ marginBottom: '1.5rem' }}>
          <button onClick={handleCreate} className="btn btn-primary">
            Add New Tee
          </button>
        </div>

        {loading ? (
          <div className="loading">Loading tees...</div>
        ) : (
          <Table data={tees} columns={columns} customActions={customActions} emptyMessage="No tees configured. Add your first tee." />
        )}

        {showForm && (
          <div style={{ marginTop: '2rem', padding: '1.5rem', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
            <h3 style={{ marginTop: 0 }}>{editingTee ? 'Edit Tee' : 'Add New Tee'}</h3>
            <form onSubmit={handleSubmit}>
              <div className="form-row">
                <div className="form-group">
                  <label>Tee Name *</label>
                  <input
                    type="text"
                    value={formData.nombre}
                    onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
                    placeholder="e.g., White, Red, Blue, Championship"
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Group/Color (optional)</label>
                  <input
                    type="text"
                    value={formData.grupo}
                    onChange={(e) => setFormData({ ...formData, grupo: e.target.value })}
                    placeholder="e.g., White, Red"
                  />
                </div>
              </div>
              <div className="form-actions" style={{ marginTop: '1rem', borderTop: 'none', paddingTop: 0 }}>
                <button type="button" onClick={() => setShowForm(false)} className="btn btn-cancel">
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  {editingTee ? 'Update Tee' : 'Add Tee'}
                </button>
              </div>
            </form>
          </div>
        )}
      </div>
    </Modal>
  );
};

export default ManageTeesModal;