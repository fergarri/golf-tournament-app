import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../services/authService';
import { isTokenExpired } from '../utils/jwtUtils';
import { User } from '../types';

interface AuthContextType {
  user: User | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  isLoading: boolean;
  hasPermission: (permission: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const navigate = useNavigate();

  const PUBLIC_PATHS = [
    /^\/inscribe\//,
    /^\/play\//,
    /^\/results\//,
    /^\/frutales-results\//,
    /^\/stage-results\//,
    /^\/playoff-results\//,
    /^\/tournaments\/[^/]+\/scorecard/,
  ];

  const isPublicPage = () =>
    PUBLIC_PATHS.some((regex) => regex.test(window.location.pathname));

  useEffect(() => {
    const token = localStorage.getItem('token');
    const currentUser = authService.getCurrentUser();

    // Si no hay token o está expirado, limpiar sesión
    // Solo redirigir al login si NO es una página pública
    if (!token || isTokenExpired(token)) {
      authService.logout();
      setIsLoading(false);
      if (!isPublicPage() && window.location.pathname !== '/login') {
        navigate('/login');
      }
      return;
    }

    if (currentUser) {
      if (!currentUser.permissions) {
        authService.logout();
        setIsLoading(false);
        navigate('/login');
        return;
      }
      setUser(currentUser);
      if (window.location.pathname === '/login') {
        navigate('/');
      }
    }
    setIsLoading(false);
  }, [navigate]);

  const login = async (username: string, password: string) => {
    try {
      const response = await authService.login(username, password);
      const userData: User = {
        email: response.email,
        role: response.role,
        permissions: response.permissions,
      };
      
      localStorage.setItem('token', response.token);
      localStorage.setItem('user', JSON.stringify(userData));
      
      setUser(userData);
      navigate('/');
    } catch (error) {
      throw error;
    }
  };

  const logout = () => {
    authService.logout();
    setUser(null);
    navigate('/login');
  };

  const hasPermission = (permission: string): boolean => {
    if (!user || !user.permissions) return false;
    return user.permissions.includes('TOTAL') || user.permissions.includes(permission);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, isLoading, hasPermission }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
