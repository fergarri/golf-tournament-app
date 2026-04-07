import axios from 'axios';
import { isTokenExpired } from '../utils/jwtUtils';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Rutas públicas que no requieren autenticación
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

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');

    if (token && isTokenExpired(token)) {
      // Limpiar el token expirado sin interrumpir la petición.
      // En páginas públicas el backend acepta la petición sin token (permitAll).
      // En páginas protegidas el backend devolverá 401 y el interceptor de
      // response se encargará de redirigir al login.
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      return config;
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;

    if (window.location.pathname === '/login') {
      return Promise.reject(error);
    }

    // No redirigir al login si el usuario está en una página pública
    if (status === 401 && !isPublicPage()) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }

    // 403 = sin permisos, no hacer logout; dejar que el componente lo maneje
    return Promise.reject(error);
  }
);

export default api;
