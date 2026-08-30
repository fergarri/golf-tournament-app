import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { courseService } from '../services/courseService';
import {
  CourseTee,
  Hole,
  ImportHandicapConversionTeeResult,
  MissingCourseTee,
  TeeHandicapTable,
} from '../types';
import Modal from '../components/Modal';
import ManageHolesModal from '../components/ManageHolesModal';
import ManageTeesModal from '../components/ManageTeesModal';
import { Button } from '../components/ui/button';
import { Upload, ChevronDown, ChevronRight, ArrowLeft } from 'lucide-react';
import '../components/Form.css';
import '../components/ManageHolesModal.css';
import './CourseAdministrationPage.css';

const generoLabel = (genero?: string) => (genero === 'F' ? 'Damas' : 'Caballeros');

const formatDecimal = (value: number | null | undefined) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—';
  return Number(value).toFixed(1);
};

const teeTitle = (tee: { nombre: string; genero?: string }) =>
  `Tee ${tee.nombre} - ${generoLabel(tee.genero)}`;

type ImportKind = 'hcp' | 'distances';

const importCopy: Record<ImportKind, { title: string; error: string; readError: string; hint: string }> = {
  hcp: {
    title: 'Importar Tabla HCP Course',
    error: 'Error importando la tabla de HCP Course',
    readError: 'Error leyendo la planilla de HCP Course',
    hint: 'Columnas: tee_name, genero, HCI_I_DESDE, HCI_I_HASTA, HCO_COURSE_100',
  },
  distances: {
    title: 'Importar Distancias de Salidas',
    error: 'Error importando las distancias de salidas',
    readError: 'Error leyendo la planilla de distancias',
    hint: 'Columnas: nombre, genero, hoyo, par, HCP, distancia',
  },
};

const CourseAdministrationPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [course, setCourse] = useState<Awaited<ReturnType<typeof courseService.getById>> | null>(null);
  const [holes, setHoles] = useState<Hole[]>([]);
  const [tables, setTables] = useState<TeeHandicapTable[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expandedTees, setExpandedTees] = useState<Set<number>>(new Set());

  const [importKind, setImportKind] = useState<ImportKind | null>(null);
  const showImportModal = importKind !== null;
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [selectedTeeIds, setSelectedTeeIds] = useState<Set<number>>(new Set());
  const [importing, setImporting] = useState(false);
  const [importResults, setImportResults] = useState<ImportHandicapConversionTeeResult[] | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [missingTees, setMissingTees] = useState<MissingCourseTee[]>([]);
  const [showMissingModal, setShowMissingModal] = useState(false);
  const [confirmingMissing, setConfirmingMissing] = useState(false);
  const [showHolesModal, setShowHolesModal] = useState(false);
  const [showTeesModal, setShowTeesModal] = useState(false);

  const activeTees = useMemo(
    () => (course?.tees || [])
      .filter((tee) => tee.active)
      .sort((a, b) => a.nombre.localeCompare(b.nombre) || generoLabel(a.genero).localeCompare(generoLabel(b.genero))),
    [course]
  );

  const loadData = async () => {
    if (!id) return;
    try {
      setLoading(true);
      const courseId = parseInt(id);
      const [courseData, holesData, tablesData, teesData] = await Promise.all([
        courseService.getById(courseId),
        courseService.getHoles(courseId),
        courseService.getHandicapConversions(courseId),
        courseService.getTees(courseId),
      ]);
      setCourse({ ...courseData, tees: teesData || courseData.tees || [] });
      setHoles(holesData || []);
      setTables(tablesData || []);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando el campo');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [id]);

  const toggleTee = (teeId: number) => {
    setExpandedTees((prev) => {
      const next = new Set(prev);
      if (next.has(teeId)) next.delete(teeId);
      else next.add(teeId);
      return next;
    });
  };

  const openImportModal = (kind: ImportKind) => {
    setImportKind(kind);
    setSelectedFile(null);
    setSelectedTeeIds(new Set());
    setImportResults(null);
    setMissingTees([]);
    setShowMissingModal(false);
  };

  const closeAllImportModals = () => {
    setShowMissingModal(false);
    setImportKind(null);
    setSelectedFile(null);
    setSelectedTeeIds(new Set());
    setImportResults(null);
    setMissingTees([]);
  };

  const handleFile = (file: File | undefined) => {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      setError('El archivo debe ser formato .xlsx');
      return;
    }
    setSelectedFile(file);
    setImportResults(null);
  };

  const toggleImportTee = (teeId: number) => {
    setSelectedTeeIds((prev) => {
      const next = new Set(prev);
      if (next.has(teeId)) next.delete(teeId);
      else next.add(teeId);
      return next;
    });
  };

  const executeImport = async (createMissing: boolean) => {
    if (!course || !selectedFile || !importKind) return;
    if (!createMissing && selectedTeeIds.size === 0) {
      closeAllImportModals();
      return;
    }
    try {
      setImporting(true);
      setConfirmingMissing(false);
      setError('');
      const importer = importKind === 'hcp'
        ? courseService.importHandicapConversions
        : courseService.importHoleDistances;
      const result = await importer(
        course.id,
        selectedFile,
        Array.from(selectedTeeIds),
        createMissing
      );
      setShowMissingModal(false);
      setImportResults(result.tees);
      const [tablesData, teesData, holesData] = await Promise.all([
        courseService.getHandicapConversions(course.id),
        courseService.getTees(course.id),
        courseService.getHoles(course.id),
      ]);
      setTables(tablesData || []);
      setHoles(holesData || []);
      setCourse((prev) => (prev ? { ...prev, tees: teesData || prev.tees || [] } : prev));
    } catch (err: any) {
      setError(err.response?.data?.message || importCopy[importKind].error);
    } finally {
      setImporting(false);
    }
  };

  const handleImport = async () => {
    if (!course || !selectedFile || !importKind) return;
    try {
      setImporting(true);
      setError('');
      const previewer = importKind === 'hcp'
        ? courseService.previewHandicapConversions
        : courseService.previewHoleDistances;
      const preview = await previewer(course.id, selectedFile);
      const missing = preview.missingTees || [];
      if (missing.length > 0) {
        setMissingTees(missing);
        setShowMissingModal(true);
        return;
      }
      if (selectedTeeIds.size === 0) {
        setError('Debe seleccionar al menos un tee de salida');
        return;
      }
      await executeImport(false);
    } catch (err: any) {
      setError(err.response?.data?.message || importCopy[importKind].readError);
    } finally {
      setImporting(false);
    }
  };

  const handleAcceptCreateMissing = () => {
    setConfirmingMissing(true);
    executeImport(true);
  };

  const handleCancelCreateMissing = () => {
    if (confirmingMissing || importing) return;
    setShowMissingModal(false);
    if (selectedTeeIds.size === 0) {
      closeAllImportModals();
      return;
    }
    executeImport(false);
  };

  const totalPar = holes.reduce((sum, hole) => sum + (hole.par || 0), 0);

  if (loading) return <div className="loading">Cargando administración del campo...</div>;
  if (!course) {
    return (
      <div>
        {error && <div className="error-message">{error}</div>}
        <button type="button" className="btn btn-cancel" onClick={() => navigate('/courses')}>
          Volver a Campos
        </button>
      </div>
    );
  }

  return (
    <div className="course-admin-page">
      <div className="page-header">
        <div>
          <h1>Administración de campos</h1>
          <p className="course-admin-subtitle">{course.nombre}</p>
        </div>
        <div className="header-actions">
          <Button variant="outline" size="sm" onClick={() => navigate('/courses')}>
            <ArrowLeft className="h-4 w-4" />
            Volver
          </Button>
          <Button size="sm" onClick={() => openImportModal('hcp')}>
            <Upload className="h-4 w-4" />
            Importar Tabla HCP Course
          </Button>
          <Button size="sm" onClick={() => openImportModal('distances')}>
            <Upload className="h-4 w-4" />
            Importar Distancias de Salidas
          </Button>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <section className="course-admin-section">
        <h2>Datos del campo</h2>
        <div className="course-info-grid">
          <div>
            <span>Nombre</span>
            <strong>{course.nombre}</strong>
          </div>
          <div>
            <span>País</span>
            <strong>{course.pais || '—'}</strong>
          </div>
          <div>
            <span>Provincia</span>
            <strong>{course.provincia || '—'}</strong>
          </div>
          <div>
            <span>Ciudad</span>
            <strong>{course.ciudad || '—'}</strong>
          </div>
          <div>
            <span>Cantidad de hoyos</span>
            <strong>{course.cantidadHoyos}</strong>
          </div>
          <div>
            <span>Handicap Course</span>
            <strong>{course.courseRating ?? '—'}</strong>
          </div>
          <div>
            <span>Slope Rating</span>
            <strong>{course.slopeRating ?? '—'}</strong>
          </div>
        </div>
      </section>

      <section className="course-admin-section">
        <h2>Hoyos</h2>
        <div className="holes-section-actions">
          <Button variant="outline" size="sm" onClick={() => setShowHolesModal(true)}>
            Gestionar Hoyos
          </Button>
          <Button variant="outline" size="sm" onClick={() => setShowTeesModal(true)}>
            Gestionar Tees
          </Button>
        </div>
        <div className="holes-summary">
          <p><strong>Total Hoyos:</strong> {course.cantidadHoyos}</p>
          <p><strong>Total Par:</strong> {totalPar}</p>
          <p><strong>Tees Activos:</strong> {activeTees.length}</p>
        </div>
        {activeTees.length === 0 ? (
          <div className="warning-message">No hay tees activos configurados para este campo.</div>
        ) : holes.length === 0 ? (
          <div className="warning-message">No hay hoyos configurados para este campo.</div>
        ) : (
          <div className="holes-table-wrapper">
            <table className="holes-table">
              <thead>
                <tr>
                  <th>Hoyo</th>
                  <th>Par</th>
                  <th>HCP</th>
                  {activeTees.map((tee) => (
                    <th key={tee.id}>
                      {tee.grupo ? `${tee.grupo} - ` : ''}{tee.nombre} (yds)
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {holes.map((hole) => (
                  <tr key={hole.numeroHoyo}>
                    <td className="hole-number">{hole.numeroHoyo}</td>
                    <td>{hole.par}</td>
                    <td>{hole.handicap}</td>
                    {activeTees.map((tee) => (
                      <td key={tee.id}>{hole.distancesByTee?.[tee.id] ?? '—'}</td>
                    ))}
                  </tr>
                ))}
                <tr className="totals-row">
                  <td><strong>TOTAL</strong></td>
                  <td><strong>{totalPar}</strong></td>
                  <td></td>
                  {activeTees.map((tee) => {
                    const total = holes.reduce((sum, hole) => sum + (hole.distancesByTee?.[tee.id] || 0), 0);
                    return <td key={tee.id}><strong>{total || '—'}</strong></td>;
                  })}
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="course-admin-section">
        <h2>Tablas de equivalencia HCP Course</h2>
        {tables.filter((t) => t.active).length === 0 ? (
          <p className="text-sm text-slate-500">No hay tees activos para mostrar tablas de equivalencia.</p>
        ) : (
          <div className="hcp-tables">
            {tables.filter((t) => t.active).map((table) => {
              const expanded = expandedTees.has(table.teeId);
              const mid = Math.ceil(table.conversions.length / 2);
              const left = table.conversions.slice(0, mid);
              const right = table.conversions.slice(mid);
              return (
                <div key={table.teeId} className="hcp-table-card">
                  <button type="button" className="hcp-table-toggle" onClick={() => toggleTee(table.teeId)}>
                    <span>
                      {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                    </span>
                    <span className="hcp-table-title">{teeTitle(table)}</span>
                    <span className="hcp-table-hint">
                      {expanded ? 'Ocultar tabla' : 'Desplegar tabla'}
                      {table.conversions.length === 0 ? ' (sin datos)' : ` (${table.conversions.length})`}
                    </span>
                  </button>
                  {expanded && (
                    table.conversions.length === 0 ? (
                      <p className="hcp-empty">No hay equivalencias cargadas para este tee.</p>
                    ) : (
                      <div className="hcp-split">
                        <EquivalenceTable rows={left} />
                        {right.length > 0 && <EquivalenceTable rows={right} />}
                      </div>
                    )
                  )}
                </div>
              );
            })}
          </div>
        )}
      </section>

      <ManageTeesModal
        isOpen={showTeesModal}
        onClose={() => setShowTeesModal(false)}
        course={{ ...course, holes }}
        onSave={() => {
          loadData();
          setShowTeesModal(false);
        }}
      />

      <ManageHolesModal
        isOpen={showHolesModal}
        onClose={() => setShowHolesModal(false)}
        course={{ ...course, holes }}
        onSave={() => {
          loadData();
          setShowHolesModal(false);
        }}
      />

      <Modal
        isOpen={showImportModal}
        onClose={() => !importing && !showMissingModal && closeAllImportModals()}
        title={importKind ? importCopy[importKind].title : ''}
        size="medium"
        footer={
          <div className="flex gap-3 justify-end">
            <Button variant="outline" onClick={closeAllImportModals} disabled={importing}>
              Cerrar
            </Button>
            {!importResults && (
              <Button
                onClick={handleImport}
                disabled={!selectedFile || importing}
              >
                {importing && !showMissingModal ? 'Importando…' : 'Importar'}
              </Button>
            )}
          </div>
        }
      >
        {importResults ? (
          <div className="space-y-3">
            <p className="text-sm text-slate-600">Resultado de la importación:</p>
            <ul className="import-result-list">
              {importResults.map((r) => (
                <li key={r.teeId}>
                  <strong>{teeTitle({ nombre: r.teeNombre, genero: r.genero })}:</strong> {r.message}
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <div className="space-y-4">
            <div
              className={`import-dropzone ${isDragging ? 'dragging' : ''}`}
              onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
              onDragLeave={() => setIsDragging(false)}
              onDrop={(e) => {
                e.preventDefault();
                setIsDragging(false);
                handleFile(e.dataTransfer.files?.[0]);
              }}
            >
              <Upload className="h-6 w-6 text-slate-400" />
              <p>Arrastrá la planilla aquí o buscala en tu equipo</p>
              {importKind && <p className="text-xs text-slate-500">{importCopy[importKind].hint}</p>}
              <input
                type="file"
                accept=".xlsx"
                onChange={(e) => handleFile(e.target.files?.[0])}
              />
              {selectedFile && <p className="selected-file">{selectedFile.name}</p>}
            </div>

            <div>
              <p className="text-sm font-medium text-slate-700 mb-2">Tees de salida a importar</p>
              {activeTees.length === 0 ? (
                <p className="text-sm text-slate-500">
                  No hay tees activos. Si la planilla trae tees nuevos, al importar se ofrecerá crearlos.
                </p>
              ) : (
                <div className="import-tee-list">
                  {activeTees.map((tee: CourseTee) => (
                    <label key={tee.id}>
                      <input
                        type="checkbox"
                        checked={selectedTeeIds.has(tee.id)}
                        onChange={() => toggleImportTee(tee.id)}
                      />
                      <span>{teeTitle(tee)}</span>
                    </label>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>

      <Modal
        isOpen={showMissingModal}
        onClose={handleCancelCreateMissing}
        title="Tees no existentes"
        size="medium"
        footer={
          <div className="flex gap-3 justify-end">
            <Button variant="outline" onClick={handleCancelCreateMissing} disabled={importing}>
              Cancelar
            </Button>
            <Button onClick={handleAcceptCreateMissing} disabled={importing}>
              {importing ? 'Creando…' : 'Aceptar'}
            </Button>
          </div>
        }
      >
        <div className="text-sm text-slate-600 leading-relaxed">
          {missingTees.length === 1 ? (
            <p>
              El Tee de salida: '{missingTees[0].nombre} - {generoLabel(missingTees[0].genero)}' no existe. ¿Desea crearlo?
            </p>
          ) : (
            <>
              <p>Los siguientes Tees de salida no existen:</p>
              <ul className="list-disc pl-5 my-3 space-y-1">
                {missingTees.map((tee) => (
                  <li key={`${tee.nombre}-${tee.genero}`}>
                    '{tee.nombre} - {generoLabel(tee.genero)}'
                  </li>
                ))}
              </ul>
              <p>¿Desea crearlos?</p>
            </>
          )}
        </div>
      </Modal>
    </div>
  );
};

const EquivalenceTable = ({ rows }: { rows: TeeHandicapTable['conversions'] }) => (
  <table className="hcp-eq-table">
    <thead>
      <tr>
        <th>HCP Index Desde</th>
        <th>HCP Index Hasta</th>
        <th>HCP Course 100%</th>
      </tr>
    </thead>
    <tbody>
      {rows.map((row) => (
        <tr key={row.id}>
          <td>{formatDecimal(row.hcpIndexFrom)}</td>
          <td>{formatDecimal(row.hcpIndexTo)}</td>
          <td className="hcp-course-value">{formatDecimal(row.courseHandicap)}</td>
        </tr>
      ))}
    </tbody>
  </table>
);

export default CourseAdministrationPage;
