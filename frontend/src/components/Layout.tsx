import { useEffect, useRef, useState } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { authService } from '../services/authService';
import { Trophy, Users, Map, LayoutDashboard, Settings, LogOut, Menu, X, Shield, ChevronDown } from 'lucide-react';
import { Button } from './ui/button';
import { cn } from '@/lib/utils';
import ChangePasswordModal from './ChangePasswordModal';
import Modal from './Modal';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, permission: null },
  { to: '/tournaments', label: 'Torneos', icon: Trophy, permission: 'GAMES' },
  { to: '/players', label: 'Jugadores', icon: Users, permission: 'GAMES' },
  { to: '/courses', label: 'Campos', icon: Map, permission: 'GAMES' },
  { to: '/users', label: 'Usuarios', icon: Shield, permission: 'CLUB_USERS' },
  { to: '/administration', label: 'Administración', icon: Settings, permission: 'ADMINISTRATION' },
];

const Layout = () => {
  const { user, logout, hasPermission } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [passwordSuccessOpen, setPasswordSuccessOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!userMenuOpen) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setUserMenuOpen(false);
      }
    };
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setUserMenuOpen(false);
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [userMenuOpen]);

  const visibleItems = navItems.filter(
    (item) => item.permission === null || hasPermission(item.permission)
  );

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      {/* Navbar */}
      <header className="bg-[#1a3c2e] text-white shadow-md sticky top-0 z-40">
        <div className="w-full max-w-[max(1400px,90%)] mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
          {/* Logo */}
          <Link to="/dashboard" className="flex items-center gap-2 shrink-0">
            <Trophy className="h-6 w-6 text-emerald-400" />
            <span className="font-bold text-lg tracking-tight hidden sm:block">Torneos de Golf</span>
          </Link>

          {/* Desktop nav */}
          <nav className="hidden md:flex items-center gap-1">
            {visibleItems.map((item) => {
              const Icon = item.icon;
              const active =
                location.pathname === item.to || location.pathname.startsWith(`${item.to}/`);
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={cn(
                    'flex items-center gap-1.5 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                    active
                      ? 'bg-emerald-700 text-white'
                      : 'text-emerald-100 hover:bg-emerald-800 hover:text-white'
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </Link>
              );
            })}
          </nav>

          {/* User section */}
          <div className="flex items-center gap-3">
            {user?.courseName && (
              <span className="hidden lg:flex items-center gap-1.5 text-xs font-medium bg-emerald-900/60 text-emerald-100 px-2.5 py-1 rounded-full">
                <Map className="h-3.5 w-3.5" />
                {user.courseName}
              </span>
            )}
            <span className="hidden sm:block text-sm text-emerald-200 truncate max-w-[180px]">
              {user?.email}
            </span>
            <div className="relative" ref={userMenuRef}>
              <button
                type="button"
                className="p-1.5 rounded-md text-emerald-100 hover:text-white hover:bg-emerald-800"
                onClick={() => setUserMenuOpen((open) => !open)}
                aria-label="Menú de usuario"
                aria-haspopup="menu"
                aria-expanded={userMenuOpen}
              >
                <ChevronDown className={cn('h-4 w-4 transition-transform', userMenuOpen && 'rotate-180')} />
              </button>
              {userMenuOpen && (
                <div
                  role="menu"
                  className="absolute right-0 top-full z-50 mt-2 w-52 rounded-md bg-white py-1 shadow-lg ring-1 ring-black/5"
                >
                  <button
                    type="button"
                    role="menuitem"
                    className="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100"
                    onClick={() => {
                      setUserMenuOpen(false);
                      navigate('/perfil');
                    }}
                  >
                    Perfil
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    className="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100"
                    onClick={() => {
                      setUserMenuOpen(false);
                      setPasswordModalOpen(true);
                    }}
                  >
                    Cambiar contraseña
                  </button>
                </div>
              )}
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={logout}
              className="text-emerald-100 hover:text-white hover:bg-emerald-800 gap-1.5"
            >
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Cerrar sesión</span>
            </Button>
            {/* Mobile menu toggle */}
            <button
              className="md:hidden p-2 rounded-md text-emerald-200 hover:bg-emerald-800"
              onClick={() => setMobileOpen((v) => !v)}
              aria-label="Menú"
            >
              {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>

        {/* Mobile nav */}
        {mobileOpen && (
          <div className="md:hidden border-t border-emerald-800 bg-[#1a3c2e] px-4 py-2">
            {visibleItems.map((item) => {
              const Icon = item.icon;
              const active =
                location.pathname === item.to || location.pathname.startsWith(`${item.to}/`);
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={() => setMobileOpen(false)}
                  className={cn(
                    'flex items-center gap-2 px-3 py-2.5 rounded-md text-sm font-medium transition-colors mb-1',
                    active
                      ? 'bg-emerald-700 text-white'
                      : 'text-emerald-100 hover:bg-emerald-800 hover:text-white'
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </Link>
              );
            })}
          </div>
        )}
      </header>

      {/* Main content */}
      <main className="flex-1 max-w-[1400px] w-full mx-auto px-4 sm:px-6 py-6">
        <Outlet />
      </main>

      <ChangePasswordModal
        isOpen={passwordModalOpen}
        onClose={() => setPasswordModalOpen(false)}
        requireCurrentPassword
        formId="own-password-form"
        onSubmit={({ currentPassword, newPassword }) =>
          authService.changeOwnPassword({
            currentPassword: currentPassword ?? '',
            newPassword,
          })
        }
        onSuccess={() => setPasswordSuccessOpen(true)}
      />
      <Modal
        isOpen={passwordSuccessOpen}
        onClose={() => setPasswordSuccessOpen(false)}
        title="Contraseña actualizada"
        message="Tu contraseña se cambió correctamente."
        type="success"
      />
    </div>
  );
};

export default Layout;
