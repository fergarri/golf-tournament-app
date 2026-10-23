import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Trophy,
  Menu,
  X,
  Map,
  Users,
  CalendarRange,
  ClipboardCheck,
  Swords,
  BarChart3,
  Shield,
  Flag,
  ArrowRight,
} from 'lucide-react';

const NAV = [
  { href: '#torneos', label: 'Torneos' },
  { href: '#tarjetas', label: 'Tarjetas' },
  { href: '#campo', label: 'Campo' },
  { href: '#resultados', label: 'Resultados' },
];

const STATS = [
  { value: 'Torneos', label: 'Un día y por etapas' },
  { value: 'Tarjetas', label: 'Carga en tiempo real' },
  { value: 'Match play', label: 'Llaves y partidos en vivo' },
  { value: 'Resultados', label: 'Tablas públicas' },
];

const MODULES = [
  {
    id: 'campo',
    icon: Map,
    title: 'Campo de golf',
    text: 'Hoyos, par, hándicap de hoyo y distancias por salida. Course Rating y Slope se importan desde el Reporte de Tarjeta de la AAG.',
  },
  {
    id: 'jugadores',
    icon: Users,
    title: 'Jugadores',
    text: 'Alta por matrícula y actualización del HCP Index con la planilla de la AAG. Ves altas, cambios de índice y matrículas que no se pudieron procesar.',
  },
  {
    id: 'torneos',
    icon: Trophy,
    title: 'Torneo de un día',
    text: 'Categorías por handicap, puntajes a medida, desempates y la tabla del día, lista para publicar.',
  },
  {
    id: 'etapas',
    icon: CalendarRange,
    title: 'Torneo por etapas',
    text: 'Un período con fechas. Cada etapa tiene su tabla y, al cierre, la clasificación general del torneo.',
  },
  {
    id: 'tarjetas',
    icon: ClipboardCheck,
    title: 'Tarjetas en vivo',
    text: 'Los jugadores cargan la tarjeta online mientras juegan. El marcador controla la del compañero: si no coinciden, el hoyo queda marcado.',
  },
  {
    id: 'match',
    icon: Swords,
    title: 'Match play',
    text: 'Llaves clasificatorias, tarjetas armadas para esa modalidad y el resultado del partido a medida que se cargan los hoyos.',
  },
  {
    id: 'resultados',
    icon: BarChart3,
    title: 'Resultados públicos',
    text: 'Tablas del torneo, de cada etapa y de la clasificación, para compartir sin entrar al sistema. También la hoja de salidas.',
  },
  {
    id: 'usuarios',
    icon: Shield,
    title: 'Usuarios',
    text: 'Cuentas del club con permisos separados para juegos, usuarios y administración.',
  },
];

const STEPS = [
  {
    n: '01',
    title: 'El campo',
    text: 'Cargá hoyos, salidas, par, distancias y las planillas de la AAG.',
  },
  {
    n: '02',
    title: 'La gente',
    text: 'Importá jugadores y actualizá el HCP Index antes de cada fecha.',
  },
  {
    n: '03',
    title: 'El torneo',
    text: 'Armá un día o una serie de etapas, se cargan las tarjetas y se publica la tabla.',
  },
];

const HomePage = () => {
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const previous = document.title;
    document.title = 'Golfero — El sistema para administrar tu campo de golf';
    return () => {
      document.title = previous;
    };
  }, []);

  const closeMenu = () => setMenuOpen(false);

  return (
    <div className="min-h-screen bg-[#071510] text-white">
      <header className="sticky top-0 z-40 border-b border-white/10 bg-[#071510]/80 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
          <a href="#inicio" className="flex items-center gap-2 shrink-0">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-emerald-500/15 ring-1 ring-emerald-400/40">
              <Trophy className="h-5 w-5 text-emerald-400" />
            </span>
            <span className="text-lg font-bold tracking-tight">Golfero</span>
          </a>

          <nav className="hidden items-center gap-7 md:flex">
            {NAV.map((item) => (
              <a
                key={item.href}
                href={item.href}
                className="text-sm text-emerald-100/80 transition-colors hover:text-white"
              >
                {item.label}
              </a>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            <Link
              to="/login"
              className="inline-flex h-10 items-center rounded-full bg-emerald-500 px-4 text-sm font-semibold text-white transition-colors hover:bg-emerald-400"
            >
              Ingresar
            </Link>
            <button
              type="button"
              className="rounded-md p-2 text-emerald-100 md:hidden"
              onClick={() => setMenuOpen((open) => !open)}
              aria-label="Menú"
            >
              {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>

        {menuOpen && (
          <nav className="border-t border-white/10 px-4 py-3 md:hidden">
            {NAV.map((item) => (
              <a
                key={item.href}
                href={item.href}
                onClick={closeMenu}
                className="block rounded-md px-3 py-2.5 text-sm text-emerald-100 hover:bg-white/5"
              >
                {item.label}
              </a>
            ))}
          </nav>
        )}
      </header>

      <main id="inicio">
        <section className="relative overflow-hidden">
          <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,_rgba(16,185,129,0.18),_transparent_55%)]" />
          <div className="relative mx-auto max-w-4xl px-4 pb-16 pt-20 text-center sm:px-6 sm:pt-28">
            <p className="mx-auto inline-flex items-center gap-2 rounded-full border border-emerald-400/30 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-200">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
              Sistema de gestión para clubes de golf
            </p>
            <h1 className="mt-6 text-4xl font-bold leading-tight tracking-tight sm:text-6xl">
              El sistema para administrar tu campo de golf
              <span className="mt-2 block bg-gradient-to-r from-cyan-300 via-sky-400 to-violet-400 bg-clip-text text-transparent">
                torneos y jugadores
              </span>
            </h1>
            <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-emerald-100/75 sm:text-lg">
              Torneos de un día y torneos por etapas. Cargá el campo, los jugadores y el handicap,
              seguí las tarjetas en vivo y publicá los resultados. Pensado para clubes argentinos.
            </p>
            <div className="mt-8 flex justify-center">
              <Link
                to="/login"
                className="inline-flex h-12 items-center gap-2 rounded-full bg-gradient-to-r from-cyan-400 to-emerald-400 px-6 text-sm font-semibold text-slate-950 transition-opacity hover:opacity-90"
              >
                Ingresar
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
          </div>

          <div className="relative mx-auto grid max-w-5xl grid-cols-2 gap-6 px-4 pb-20 sm:px-6 md:grid-cols-4">
            {STATS.map((stat) => (
              <div key={stat.value} className="text-center">
                <p className="text-2xl font-bold text-cyan-300 sm:text-3xl">{stat.value}</p>
                <p className="mt-1 text-sm text-emerald-100/60">{stat.label}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="border-t border-white/10 bg-[#0b1c16]">
          <div className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
            <div className="mx-auto max-w-2xl text-center">
              <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">Qué puede hacer el club</h2>
              <p className="mt-3 text-emerald-100/70">
                Del hoyo 1 a la tabla final, en un solo lugar.
              </p>
            </div>
            <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {MODULES.map((module) => {
                const Icon = module.icon;
                return (
                  <article
                    key={module.id}
                    id={module.id}
                    className="scroll-mt-24 rounded-2xl border border-white/10 bg-white/[0.03] p-5"
                  >
                    <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-300">
                      <Icon className="h-5 w-5" />
                    </span>
                    <h3 className="mt-4 text-base font-semibold">{module.title}</h3>
                    <p className="mt-2 text-sm leading-relaxed text-emerald-100/65">{module.text}</p>
                  </article>
                );
              })}
            </div>
          </div>
        </section>

        <section className="border-t border-white/10">
          <div className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
            <div className="mx-auto max-w-2xl text-center">
              <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">Cómo se usa</h2>
              <p className="mt-3 text-emerald-100/70">Tres pasos, en el orden en que trabaja el club.</p>
            </div>
            <ol className="mt-12 grid gap-6 md:grid-cols-3">
              {STEPS.map((step) => (
                <li key={step.n} className="rounded-2xl border border-white/10 p-6">
                  <p className="text-sm font-semibold tracking-widest text-cyan-300">{step.n}</p>
                  <h3 className="mt-3 text-xl font-semibold">{step.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-emerald-100/65">{step.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="border-t border-white/10">
          <div className="mx-auto max-w-3xl px-4 py-20 text-center sm:px-6">
            <Flag className="mx-auto h-8 w-8 text-emerald-400" />
            <h2 className="mt-4 text-3xl font-bold tracking-tight sm:text-4xl">
              El sistema para administrar tu campo de golf
            </h2>
            <p className="mt-3 text-emerald-100/70">Torneos, jugadores y resultados, listos para la próxima fecha.</p>
            <Link
              to="/login"
              className="mt-8 inline-flex h-12 items-center gap-2 rounded-full bg-emerald-500 px-6 text-sm font-semibold text-white transition-colors hover:bg-emerald-400"
            >
              Ingresar al panel
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </section>
      </main>

      <footer className="border-t border-white/10">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-4 py-8 text-sm text-emerald-100/50 sm:flex-row sm:px-6">
          <span className="font-semibold text-emerald-100/80">Golfero</span>
          <span>Campos, torneos y jugadores.</span>
        </div>
      </footer>
    </div>
  );
};

export default HomePage;
