import { Outlet, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import './Layout.css';

const Layout = () => {
  const { user, logout, hasPermission } = useAuth();

  return (
    <div className="layout">
      <nav className="navbar">
        <div className="navbar-brand">
          <h1>Torneos de Golf</h1>
        </div>
        <div className="navbar-links">
          <Link to="/">Dashboard</Link>
          {hasPermission('GAMES') && (
            <>
              <Link to="/tournaments">Torneos</Link>
              <Link to="/players">Jugadores</Link>
              <Link to="/courses">Campos</Link>
            </>
          )}
          {hasPermission('TOTAL') && (
            <Link to="/users">Usuarios</Link>
          )}
          {hasPermission('ADMINISTRATION') && (
            <Link to="/administration">Administración</Link>
          )}
        </div>
        <div className="navbar-user">
          <span>{user?.email}</span>
          <button onClick={logout} className="btn-logout">Cerrar sesión</button>
        </div>
      </nav>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
};

export default Layout;
