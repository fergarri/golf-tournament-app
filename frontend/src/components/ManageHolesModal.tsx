import { useState, useEffect } from 'react';
import { Course, Hole, CourseTee } from '../types';
import { courseService } from '../services/courseService';
import Modal from './Modal';
import './ManageHolesModal.css';

interface ManageHolesModalProps {
  isOpen: boolean;
  onClose: () => void;
  course: Course;
  onSave: () => void;
}

const ManageHolesModal = ({ isOpen, onClose, course, onSave }: ManageHolesModalProps) => {
  const [holes, setHoles] = useState<Hole[]>([]);
  const [tees, setTees] = useState<CourseTee[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (course && isOpen) {
      loadHoles();
      setTees(course.tees?.filter(t => t.active) || []);
    }
  }, [course, isOpen]);

  const loadHoles = async () => {
    try {
      setLoading(true);
      const holesData = await courseService.getHoles(course.id);
      if (holesData && holesData.length > 0) {
        setHoles(holesData);
      } else {
        initializeHoles();
      }
    } catch (err) {
      initializeHoles();
    } finally {
      setLoading(false);
    }
  };

  const initializeHoles = () => {
    if (course.holes && course.holes.length > 0) {
      setHoles(course.holes);
    } else {
      const newHoles: Hole[] = [];
      for (let i = 1; i <= course.cantidadHoyos; i++) {
        newHoles.push({
          id: 0,
          numeroHoyo: i,
          par: 4,
          handicap: i,
          distancesByTee: {},
        });
      }
      setHoles(newHoles);
    }
  };

  const updateHole = (index: number, field: keyof Hole, value: any) => {
    const newHoles = [...holes];
    newHoles[index] = { ...newHoles[index], [field]: value };
    setHoles(newHoles);
  };

  const updateDistance = (holeIndex: number, teeId: number, distance: number) => {
    const newHoles = [...holes];
    newHoles[holeIndex] = {
      ...newHoles[holeIndex],
      distancesByTee: {
        ...newHoles[holeIndex].distancesByTee,
        [teeId]: distance,
      },
    };
    setHoles(newHoles);
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      for (const hole of holes) {
        await courseService.saveHole(course.id, hole);
      }
      alert('Holes configuration saved successfully');
      onSave();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Error saving holes');
    } finally {
      setSaving(false);
    }
  };

  const totalPar = holes.reduce((sum, hole) => sum + hole.par, 0);

  if (loading) {
    return (
      <Modal isOpen={isOpen} onClose={onClose} title={`Manage Holes - ${course?.nombre}`} size="large">
        <div className="loading">Loading holes...</div>
      </Modal>
    );
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`Manage Holes - ${course?.nombre}`} size="large">
      <div className="holes-manager">
        <div className="holes-summary">
          <p>
            <strong>Total Holes:</strong> {course?.cantidadHoyos}
          </p>
          <p>
            <strong>Total Par:</strong> {totalPar}
          </p>
          <p>
            <strong>Active Tees:</strong> {tees.length}
          </p>
        </div>

        {tees.length === 0 && (
          <div className="warning-message">
            No tees configured for this course. Please add tees first to configure distances.
          </div>
        )}

        <div className="holes-table-wrapper">
          <table className="holes-table">
            <thead>
              <tr>
                <th>Hole</th>
                <th>Par</th>
                <th>HCP</th>
                {tees.map((tee) => (
                  <th key={tee.id}>{tee.nombre} (yds)</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {holes.map((hole, index) => (
                <tr key={hole.numeroHoyo}>
                  <td className="hole-number">{hole.numeroHoyo}</td>
                  <td>
                    <select
                      value={hole.par}
                      onChange={(e) => updateHole(index, 'par', parseInt(e.target.value))}
                      className="compact-select"
                    >
                      <option value={3}>3</option>
                      <option value={4}>4</option>
                      <option value={5}>5</option>
                    </select>
                  </td>
                  <td>
                    <input
                      type="number"
                      min="1"
                      max="18"
                      value={hole.handicap}
                      onChange={(e) => updateHole(index, 'handicap', parseInt(e.target.value))}
                      className="compact-input"
                    />
                  </td>
                  {tees.map((tee) => (
                    <td key={tee.id}>
                      <input
                        type="number"
                        min="0"
                        placeholder="yds"
                        value={hole.distancesByTee?.[tee.id] || ''}
                        onChange={(e) =>
                          updateDistance(index, tee.id, e.target.value ? parseInt(e.target.value) : 0)
                        }
                        className="compact-input"
                      />
                    </td>
                  ))}
                </tr>
              ))}
              <tr className="totals-row">
                <td><strong>TOTAL</strong></td>
                <td><strong>{totalPar}</strong></td>
                <td></td>
                {tees.map((tee) => {
                  const total = holes.reduce(
                    (sum, hole) => sum + (hole.distancesByTee?.[tee.id] || 0),
                    0
                  );
                  return <td key={tee.id}><strong>{total}</strong></td>;
                })}
              </tr>
            </tbody>
          </table>
        </div>

        <div className="form-actions">
          <button type="button" onClick={onClose} className="btn btn-cancel" disabled={saving}>
            Cancel
          </button>
          <button type="button" onClick={handleSave} className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving...' : 'Save Holes Configuration'}
          </button>
        </div>
      </div>
    </Modal>
  );
};

export default ManageHolesModal;
