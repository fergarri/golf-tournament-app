/**
 * Rutas públicas de la app (no requieren login).
 * Se usa tanto en el interceptor de axios (api.ts) como en AuthProvider (useAuth.tsx)
 * para decidir si hay que redirigir a /login cuando no hay token válido.
 *
 * IMPORTANTE: mantener esta lista actualizada al agregar nuevas páginas públicas.
 */
export const PUBLIC_PATHS = [
  /^\/inscribe\//,
  /^\/play\//,
  /^\/results\//,
  /^\/frutales-results\//,
  /^\/stage-results\//,
  /^\/playoff-results\//,
  /^\/playoff-brackets\//,
  /^\/playoff-match\//,
  /^\/tournaments\/[^/]+\/scorecard/,
];

export const isPublicPage = (): boolean =>
  PUBLIC_PATHS.some((regex) => regex.test(window.location.pathname));
