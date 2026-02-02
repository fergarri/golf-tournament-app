import { Outlet, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import './Layout.css';

const Layout = () => {
  const { user, logout } = useAuth();

  return (
    <div className="layout">
      <nav className="navbar">
        <div className="navbar-brand">
          <h1>Golf Tournament</h1>
        </div>
        <div className="navbar-links">
          <Link to="/">Dashboard</Link>
          <Link to="/tournaments">Tournaments</Link>
          <Link to="/players">Players</Link>
          <Link to="/courses">Courses</Link>
          <Link to="/users">Users</Link>
        </div>
        <div className="navbar-user">
          <span>{user?.email}</span>
          <button onClick={logout} className="btn-logout">Logout</button>
        </div>
      </nav>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
};

export default Layout;
