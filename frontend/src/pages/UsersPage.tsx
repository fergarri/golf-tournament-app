import { useState, useEffect } from 'react';
import { userService } from '../services/userService';
import { courseService } from '../services/courseService';
import { UserDetail, CreateUserRequest, Course } from '../types';
import Table, { TableAction } from '../components/Table';
import Modal from '../components/Modal';
import { formatDateSafe } from '../utils/dateUtils';
import { useAuth } from '../hooks/useAuth';
import '../components/Form.css';

const ROLES_WITH_CLUB = ['USER', 'ADMIN_CLUB'];

const UsersPage = () => {
  const { isSuperAdmin, user: currentUser } = useAuth();
  const [users, setUsers] = useState<UserDetail[]>([]);
  const [roles, setRoles] = useState<string[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserDetail | null>(null);

  const [createData, setCreateData] = useState<CreateUserRequest>({
    email: '',
    matricula: '',
    password: '',
    role: 'USER',
    courseId: null,
  });

  const [editData, setEditData] = useState({
    email: '',
    matricula: '',
    role: 'USER',
    courseId: null as number | null,
  });

  const [passwordData, setPasswordData] = useState({
    newPassword: '',
  });

  useEffect(() => {
    loadUsers();
    loadRoles();
    loadCourses();
  }, []);

  // Si el rol por defecto no está disponible para el usuario actual
  // (p. ej. ADMIN_CLUB, que sólo puede asignar USER/ADMIN_CLUB), usar el primero disponible.
  useEffect(() => {
    if (roles.length > 0 && !roles.includes(createData.role)) {
      setCreateData((prev) => ({ ...prev, role: roles[0] }));
    }
  }, [roles]);

  // Un admin de club queda preseleccionado en su propio club al abrir el alta.
  useEffect(() => {
    if (!isSuperAdmin && currentUser?.courseId && !createData.courseId) {
      setCreateData((prev) => ({ ...prev, courseId: currentUser.courseId ?? null }));
    }
  }, [isSuperAdmin, currentUser?.courseId]);

  const loadUsers = async () => {
    try {
      setLoading(true);
      const data = await userService.getAll();
      setUsers(data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando usuarios');
    } finally {
      setLoading(false);
    }
  };

  const loadRoles = async () => {
    try {
      const data = await userService.getRoles();
      setRoles(data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando roles');
    }
  };

  const loadCourses = async () => {
    try {
      const data = await courseService.getAll();
      setCourses(data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cargando clubes');
    }
  };

  const resolveCourseId = (role: string, selectedCourseId: number | null) => {
    if (!ROLES_WITH_CLUB.includes(role)) return null;
    // Un admin de club (no superadmin) sólo puede crear/editar usuarios de su propio club;
    // el backend fuerza esto igualmente, acá sólo evitamos mandar un courseId ajeno.
    return isSuperAdmin ? selectedCourseId : currentUser?.courseId ?? null;
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await userService.create({
        ...createData,
        courseId: resolveCourseId(createData.role, createData.courseId ?? null),
      });
      setShowCreateModal(false);
      setCreateData({
        email: '',
        matricula: '',
        password: '',
        role: roles.find((role) => role !== 'ADMIN') || roles[0] || 'USER',
        courseId: isSuperAdmin ? null : currentUser?.courseId ?? null,
      });
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error creando usuario');
    }
  };

  const openEditModal = (user: UserDetail) => {
    setSelectedUser(user);
    setEditData({
      email: user.email,
      matricula: user.matricula || '',
      role: user.role,
      courseId: user.courseId ?? null,
    });
    setShowEditModal(true);
  };

  const handleEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUser) return;
    try {
      await userService.update(selectedUser.id, {
        ...editData,
        courseId: resolveCourseId(editData.role, editData.courseId),
      });
      setShowEditModal(false);
      setSelectedUser(null);
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error actualizando usuario');
    }
  };

  const openPasswordModal = (user: UserDetail) => {
    setSelectedUser(user);
    setPasswordData({ newPassword: '' });
    setShowPasswordModal(true);
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUser) return;
    try {
      await userService.changePassword(selectedUser.id, passwordData);
      setShowPasswordModal(false);
      setSelectedUser(null);
      setPasswordData({ newPassword: '' });
      alert('Contraseña cambiada exitosamente');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cambiando contraseña');
    }
  };

  const handleDelete = async (user: UserDetail) => {
    if (!confirm(`¿Estás seguro de querer eliminar ${user.email}?`)) return;
    try {
      await userService.delete(user.id);
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error eliminando usuario');
    }
  };

  const columns = [
    { header: 'Email', accessor: 'email' as keyof UserDetail },
    { header: 'Matrícula', accessor: (row: UserDetail) => row.matricula || '-' },
    {
      header: 'Rol',
      accessor: (row: UserDetail) => (
        <span className={`role-badge ${row.role.toLowerCase()}`}>{row.role}</span>
      ),
    },
    { header: 'Club', accessor: (row: UserDetail) => row.courseName || '—' },
    { header: 'Creado', accessor: (row: UserDetail) => formatDateSafe(row.createdAt) },
  ];

  const userActions: TableAction<UserDetail>[] = [
    {
      label: 'Editar',
      onClick: openEditModal,
      variant: 'primary',
    },
    {
      label: 'Cambiar contraseña',
      onClick: openPasswordModal,
      variant: 'secondary',
    },
    {
      label: 'Eliminar',
      onClick: handleDelete,
      variant: 'danger',
    },
  ];

  const clubOptions = [...courses]
    .sort((a, b) => a.nombre.localeCompare(b.nombre));

  const extraClubOption = (courseId: number | null, courseName?: string | null) => {
    if (!courseId || clubOptions.some((course) => course.id === courseId)) return null;
    return { id: courseId, nombre: courseName || `Club #${courseId}` };
  };

  if (loading) return <div className="loading">Cargando usuarios...</div>;

  return (
    <div>
      <div className="page-header">
        <h1>Gestión de Usuarios</h1>
        <button onClick={() => {
          setCreateData((prev) => ({
            ...prev,
            email: '',
            matricula: '',
            password: '',
            role: roles.find((role) => role !== 'ADMIN') || roles[0] || 'USER',
            courseId: isSuperAdmin ? null : currentUser?.courseId ?? null,
          }));
          setShowCreateModal(true);
        }} className="btn btn-primary">
          Crear Usuario
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}

      <Table data={users} columns={columns} actions={userActions} />

      <Modal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Crear Nuevo Usuario"
        footer={
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            <button type="button" onClick={() => setShowCreateModal(false)} className="btn btn-cancel">
              Cancelar
            </button>
            <button type="submit" form="create-user-form" className="btn btn-primary">
              Crear Usuario
            </button>
          </div>
        }
      >
        <form id="create-user-form" onSubmit={handleCreate}>
          <div className="form-group">
            <label>Email *</label>
            <input
              type="email"
              value={createData.email}
              onChange={(e) => setCreateData({ ...createData, email: e.target.value })}
              required
            />
          </div>
          <div className="form-group">
            <label>Número de Matrícula</label>
            <input
              type="text"
              value={createData.matricula}
              onChange={(e) => setCreateData({ ...createData, matricula: e.target.value })}
            />
          </div>
          <div className="form-group">
            <label>Password *</label>
            <input
              type="password"
              value={createData.password}
              onChange={(e) => setCreateData({ ...createData, password: e.target.value })}
              required
              minLength={6}
            />
          </div>
          <div className="form-group">
            <label>Rol *</label>
            <select
              value={createData.role}
              onChange={(e) => {
                const role = e.target.value;
                setCreateData({
                  ...createData,
                  role,
                  courseId: ROLES_WITH_CLUB.includes(role)
                    ? (isSuperAdmin ? createData.courseId : currentUser?.courseId ?? null)
                    : null,
                });
              }}
              required
            >
              {roles.map((role) => (
                <option key={role} value={role}>{role}</option>
              ))}
            </select>
            <small style={{ color: '#7f8c8d', marginTop: '0.25rem', display: 'block' }}>
              ADMIN es superadmin (ve todos los clubes). ADMIN_CLUB además de administrar su club puede crear usuarios (USER/ADMIN_CLUB) para el mismo. USER es admin básico de un club específico.
            </small>
          </div>
          {ROLES_WITH_CLUB.includes(createData.role) && (
            <div className="form-group">
              <label>Club *</label>
              <select
                value={createData.courseId ?? ''}
                onChange={(e) => setCreateData({ ...createData, courseId: e.target.value ? parseInt(e.target.value) : null })}
                required
                disabled={!isSuperAdmin}
              >
                <option value="">Seleccionar un club</option>
                {(() => {
                  const extra = extraClubOption(createData.courseId ?? null, currentUser?.courseName);
                  return extra ? <option key={extra.id} value={extra.id}>{extra.nombre}</option> : null;
                })()}
                {clubOptions.map((course) => (
                  <option key={course.id} value={course.id}>{course.nombre}</option>
                ))}
              </select>
            </div>
          )}
        </form>
      </Modal>

      <Modal
        isOpen={showEditModal}
        onClose={() => setShowEditModal(false)}
        title="Editar Usuario"
        footer={
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            <button type="button" onClick={() => setShowEditModal(false)} className="btn btn-cancel">
              Cancelar
            </button>
            <button type="submit" form="edit-user-form" className="btn btn-primary">
              Actualizar
            </button>
          </div>
        }
      >
        <form id="edit-user-form" onSubmit={handleEdit}>
          <div className="form-group">
            <label>Email *</label>
            <input
              type="email"
              value={editData.email}
              onChange={(e) => setEditData({ ...editData, email: e.target.value })}
              required
            />
          </div>
          <div className="form-group">
            <label>Número de Matrícula</label>
            <input
              type="text"
              value={editData.matricula}
              onChange={(e) => setEditData({ ...editData, matricula: e.target.value })}
            />
          </div>
          <div className="form-group">
            <label>Rol *</label>
            <select
              value={editData.role}
              onChange={(e) => {
                const role = e.target.value;
                setEditData({
                  ...editData,
                  role,
                  courseId: ROLES_WITH_CLUB.includes(role)
                    ? (isSuperAdmin ? editData.courseId : currentUser?.courseId ?? null)
                    : null,
                });
              }}
              required
            >
              {roles.map((role) => (
                <option key={role} value={role}>{role}</option>
              ))}
            </select>
          </div>
          {ROLES_WITH_CLUB.includes(editData.role) && (
            <div className="form-group">
              <label>Club *</label>
              <select
                value={editData.courseId ?? ''}
                onChange={(e) => setEditData({ ...editData, courseId: e.target.value ? parseInt(e.target.value) : null })}
                required
                disabled={!isSuperAdmin}
              >
                <option value="">Seleccionar un club</option>
                {(() => {
                  const extra = extraClubOption(editData.courseId, selectedUser?.courseName);
                  return extra ? <option key={extra.id} value={extra.id}>{extra.nombre}</option> : null;
                })()}
                {clubOptions.map((course) => (
                  <option key={course.id} value={course.id}>{course.nombre}</option>
                ))}
              </select>
            </div>
          )}
        </form>
      </Modal>

      <Modal
        isOpen={showPasswordModal}
        onClose={() => setShowPasswordModal(false)}
        title="Cambiar Contraseña"
        size="small"
        footer={
          <div className="form-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
            <button type="button" onClick={() => setShowPasswordModal(false)} className="btn btn-cancel">
              Cancelar
            </button>
            <button type="submit" form="password-form" className="btn btn-primary">
              Cambiar Contraseña
            </button>
          </div>
        }
      >
        <p style={{ color: '#7f8c8d', marginBottom: '1.5rem' }}>Usuario: {selectedUser?.email}</p>
        <form id="password-form" onSubmit={handleChangePassword}>
          <div className="form-group">
            <label>Nueva Contraseña *</label>
            <input
              type="password"
              value={passwordData.newPassword}
              onChange={(e) => setPasswordData({ newPassword: e.target.value })}
              required
              minLength={6}
              placeholder="Ingrese nueva contraseña (mínimo 6 caracteres)"
            />
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default UsersPage;
