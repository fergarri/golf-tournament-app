import { useState, useEffect } from 'react';
import { userService } from '../services/userService';
import { UserDetail, CreateUserRequest } from '../types';
import Table, { TableAction } from '../components/Table';
import Modal from '../components/Modal';
import { formatDateSafe } from '../utils/dateUtils';
import '../components/Form.css';

const UsersPage = () => {
  const [users, setUsers] = useState<UserDetail[]>([]);
  const [roles, setRoles] = useState<string[]>([]);
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
    role: 'ADMIN',
  });

  const [editData, setEditData] = useState({
    email: '',
    matricula: '',
    role: 'ADMIN',
  });

  const [passwordData, setPasswordData] = useState({
    newPassword: '',
  });

  useEffect(() => {
    loadUsers();
    loadRoles();
  }, []);

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

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await userService.create(createData);
      setShowCreateModal(false);
      setCreateData({ email: '', matricula: '', password: '', role: 'ADMIN' });
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
    });
    setShowEditModal(true);
  };

  const handleEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUser) return;
    try {
      await userService.update(selectedUser.id, editData);
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

  if (loading) return <div className="loading">Cargando usuarios...</div>;

  return (
    <div>
      <div className="page-header">
        <h1>Gestión de Usuarios</h1>
        <button onClick={() => setShowCreateModal(true)} className="btn btn-primary">
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
              onChange={(e) => setCreateData({ ...createData, role: e.target.value })}
              required
            >
              {roles.map((role) => (
                <option key={role} value={role}>{role}</option>
              ))}
            </select>
          </div>
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
              onChange={(e) => setEditData({ ...editData, role: e.target.value })}
              required
            >
              {roles.map((role) => (
                <option key={role} value={role}>{role}</option>
              ))}
            </select>
          </div>
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
