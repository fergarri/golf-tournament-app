import { useState, useEffect } from 'react';
import { Course, CourseTee } from '../types';
import { courseService } from '../services/courseService';
import { useAuth } from '../hooks/useAuth';
import Modal from './Modal';
import Table, { TableAction } from './Table';
import '../components/Form.css';

interface ManageTeesModalProps {
  isOpen: boolean;
  onClose: () => void;
  course: Course;
  onSave: () => void;
}

const ManageTeesModal = ({ isOpen, onClose, course, onSave }: ManageTeesModalProps) => {
  const { canDelete } = useAuth();
  const [tees, setTees] = useState<CourseTee[]>([]);
  const [loading, setLoading] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [editingTee, setEditingTee] = useState<CourseTee | null>(null);
  const [formData, setFormData] = useState({
    nombre: '',
    grupo: '',
    genero: 'M' as 'M' | 'F',
  });
  const [teeToDelete, setTeeToDelete] = useState<CourseTee | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [actionError, setActionError] = useState('');

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
      console.error('Error cargando tees:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingTee(null);
    setFormData({ nombre: '', grupo: '', genero: 'M' });
    setShowForm(true);
  };

  const handleEdit = (tee: CourseTee) => {
    setEditingTee(tee);
    setFormData({
      nombre: tee.nombre,
      grupo: tee.grupo || '',
      genero: tee.genero === 'F' ? 'F' : 'M',
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
      alert(err.response?.data?.message || 'Error guardando tee');
    }
  };

  const handleClose = () => {
    onSave();
    onClose();
  };

  const handleConfirmDelete = async () => {
    if (!teeToDelete) return;
    try {
      setDeleting(true);
      await courseService.deleteTee(teeToDelete.id);
      setTeeToDelete(null);
      loadTees();
    } catch (err: any) {
      setTeeToDelete(null);
      setActionError(err.response?.data?.message || 'Error eliminando tee');
    } finally {
      setDeleting(false);
    }
  };

  const columns = [
    { header: 'Nombre del Tee', accessor: 'nombre' as keyof CourseTee },
    { header: 'Group/Color', accessor: (row: CourseTee) => row.grupo || '-' },
    {
      header: 'Género',
      accessor: (row: CourseTee) => (row.genero === 'F' ? 'Damas' : 'Caballeros'),
    },
  ];

  const teeActions: TableAction<CourseTee>[] = [
    {
      label: 'Editar',
      onClick: handleEdit,
      variant: 'primary',
    },
    {
      label: 'Eliminar',
      onClick: (tee) => setTeeToDelete(tee),
      variant: 'danger',
      show: () => canDelete,
    },
  ];

  return (
    <Modal 
      isOpen={isOpen} 
      onClose={handleClose} 
      title={`Gestionar Tees - ${course?.nombre}`} 
      size="large"
      footer={
        showForm && (
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            <button type="button" onClick={() => setShowForm(false)} className="btn btn-cancel">
              Cancelar
            </button>
            <button type="submit" form="tee-form" className="btn btn-primary">
              {editingTee ? 'Actualizar Tee' : 'Agregar Tee'}
            </button>
          </div>
        )
      }
    >
      <div style={{ minHeight: '400px' }}>
        <div style={{ marginBottom: '1.5rem' }}>
          <button onClick={handleCreate} className="btn btn-primary">
            Agregar Tee
          </button>
        </div>

        {loading ? (
          <div className="loading">Cargando tees...</div>
        ) : (
          <Table data={tees} columns={columns} actions={teeActions} emptyMessage="No hay tees configurados. Agrega tu primer tee." />
        )}

        {showForm && (
          <div style={{ marginTop: '2rem', padding: '1.5rem', backgroundColor: '#f8f9fa', borderRadius: '8px' }}>
            <h3 style={{ marginTop: 0 }}>{editingTee ? 'Editar Tee' : 'Agregar Tee'}</h3>
            <form id="tee-form" onSubmit={handleSubmit}>
              <div className="form-row">
                <div className="form-group">
                  <label>Nombre del Tee *</label>
                  <input
                    type="text"
                    value={formData.nombre}
                    onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
                    placeholder="e.g., Blanco, Rojo, Azul, Campeonato"
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Grupo/Color (opcional)</label>
                  <input
                    type="text"
                    value={formData.grupo}
                    onChange={(e) => setFormData({ ...formData, grupo: e.target.value })}
                    placeholder="e.g., Blanco, Rojo"
                  />
                </div>
              </div>
              <div className="form-group">
                <label>Género *</label>
                <div style={{ display: 'flex', gap: '1.5rem', marginTop: '0.4rem' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer', fontWeight: 500 }}>
                    <input
                      type="radio"
                      name="tee-genero"
                      value="M"
                      checked={formData.genero === 'M'}
                      onChange={() => setFormData({ ...formData, genero: 'M' })}
                    />
                    Caballeros
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer', fontWeight: 500 }}>
                    <input
                      type="radio"
                      name="tee-genero"
                      value="F"
                      checked={formData.genero === 'F'}
                      onChange={() => setFormData({ ...formData, genero: 'F' })}
                    />
                    Damas
                  </label>
                </div>
              </div>
            </form>
          </div>
        )}
      </div>

      <Modal
        isOpen={!!teeToDelete}
        onClose={() => !deleting && setTeeToDelete(null)}
        title="Eliminar tee"
        size="small"
        footer={
          <div className="flex gap-3 justify-end">
            <button type="button" className="btn btn-cancel" onClick={() => setTeeToDelete(null)} disabled={deleting}>
              Cancelar
            </button>
            <button type="button" className="btn btn-danger" onClick={handleConfirmDelete} disabled={deleting}>
              {deleting ? 'Eliminando…' : 'Eliminar'}
            </button>
          </div>
        }
      >
        <p>
          ¿Eliminar el tee '{teeToDelete?.nombre} - {teeToDelete?.genero === 'F' ? 'Damas' : 'Caballeros'}'?
          Se borrarán también sus equivalencias de HCP Course.
        </p>
      </Modal>

      <Modal
        isOpen={!!actionError}
        onClose={() => setActionError('')}
        title="No se pudo eliminar"
        size="small"
        footer={
          <div className="flex gap-3 justify-end">
            <button type="button" className="btn btn-primary" onClick={() => setActionError('')}>
              Aceptar
            </button>
          </div>
        }
      >
        <p>{actionError}</p>
      </Modal>
    </Modal>
  );
};

export default ManageTeesModal;