# Golf Tournament Management System

A comprehensive web application for creating, managing, and organizing golf tournaments.

## Tech Stack

### Backend
- Java 21
- Spring Boot 3.x
- PostgreSQL
- Flyway (database migrations)
- Spring Security + JWT
- Jsoup (for handicap API scraping)

### Frontend
- React 18
- TypeScript
- Vite
- React Router
- Axios

### Infrastructure
- Docker & Docker Compose
- Nginx (frontend production)

## Features

- User authentication (Admin)
- Course management (CRUD with tees and holes)
- Player management (CRUD)
- Tournament creation and management
- Tournament categories by handicap
- Public tournament inscription
- Handicap integration (AAG via vistagolf.com.ar)
- Scorecard management with cross-validation
- Leaderboard by category
- Mobile-responsive design

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)
- Node.js 20+ (for local development)

### Running with Docker (Development)

1. Clone the repository
2. Run the entire stack:

```bash
docker-compose up -d
```

This will start:
- PostgreSQL database on port 5432
- Backend API on port 8080
- Frontend on port 3000

### Production Deployment

Para deployar en producción (AWS), consulta:

📖 **[DEPLOYMENT.md](DEPLOYMENT.md)** - Guía completa paso a paso para AWS

Archivos de deployment incluidos:
- `docker-compose.production.yml` - Docker Compose para producción
- `.env.production.example` - Template de variables de entorno
- `setup-ec2.sh` - Script de configuración de EC2
- `deploy.sh` - Script de deployment
- `nginx-reverse-proxy.conf` - Configuración de Nginx

Ver [DEPLOYMENT_README.md](DEPLOYMENT_README.md) para resumen rápido.

### Local Development

#### Backend

```bash
cd backend
export JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
mvn clean install
mvn spring-boot:run
```

El backend no arranca sin `JWT_SECRET` (mínimo 32 bytes). En una base nueva, definí también `INITIAL_ADMIN_EMAIL` e `INITIAL_ADMIN_PASSWORD` para crear el primer superadmin.

#### Frontend

```bash
cd frontend
npm install
npm run dev
```

### Database Migrations

Flyway migrations are automatically applied on application startup. Migration scripts are located in `backend/src/main/resources/db/migration/`.

## API Documentation

The API is available at `http://localhost:8080/api`

### Authentication
- POST `/api/auth/login` - Admin login

### Players
- GET `/api/players` - Get all players (Admin)
- POST `/api/players` - Create player (Admin)
- PUT `/api/players/{id}` - Update player (Admin)
- DELETE `/api/players/{id}` - Delete player (Admin)

### Courses
- GET `/api/courses` - Get all courses
- POST `/api/courses` - Create course (Admin)
- POST `/api/courses/{id}/tees` - Add tee to course (Admin)
- POST `/api/courses/{id}/holes` - Add/update hole (Admin)

### Tournaments
- GET `/api/tournaments` - Get all tournaments
- POST `/api/tournaments` - Create tournament (Admin)
- GET `/api/tournaments/code/{codigo}` - Get tournament by code

### Inscriptions
- POST `/api/inscriptions/tournaments/{codigo}` - Public inscription
- POST `/api/inscriptions/admin/tournaments/{tournamentId}/players/{playerId}` - Manual inscription (Admin)

### Scorecards
- GET `/api/scorecards/tournaments/{tournamentId}/players/{playerId}` - Get or create scorecard
- PATCH `/api/scorecards/{id}/scores` - Update score
- POST `/api/scorecards/{id}/deliver` - Deliver scorecard

### Leaderboard
- GET `/api/leaderboard/tournaments/{tournamentId}` - Get leaderboard
- GET `/api/leaderboard/tournaments/{tournamentId}/categories/{categoryId}` - Get leaderboard by category

## Environment Variables

### Backend (application.yml)
- `DB_HOST` - Database host
- `DB_PORT` - Database port
- `DB_NAME` - Database name
- `DB_USER` - Database user
- `DB_PASSWORD` - Database password
- `JWT_SECRET` - JWT secret key (obligatorio, mínimo 32 bytes)
- `JWT_EXPIRATION` - JWT expiration time in milliseconds
- `INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD` - Superadmin inicial (opcional; solo si no existe ningún ADMIN)

### Frontend (.env)
- `VITE_API_URL` - Backend API URL

## Project Structure

```
golf-tournament-app/
├── backend/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/golf/tournament/
│   │       │   ├── controller/
│   │       │   ├── service/
│   │       │   ├── repository/
│   │       │   ├── model/
│   │       │   ├── dto/
│   │       │   ├── config/
│   │       │   └── exception/
│   │       └── resources/
│   │           ├── db/migration/
│   │           └── application.yml
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   ├── hooks/
│   │   └── types/
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── docker-compose.yml
└── README.md
```

## Contributing

1. Follow the existing code structure
2. Use English for all code, comments, and documentation
3. Test your changes before submitting
4. Keep components decoupled and reusable

## License

Private project - All rights reserved
