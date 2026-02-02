# 🏗️ Arquitectura de Deployment - AWS

## 📊 Diagrama de Arquitectura de Producción

```
┌─────────────────────────────────────────────────────────────────┐
│                         INTERNET                                 │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ HTTPS/HTTP
                             │
                    ┌────────▼────────┐
                    │                 │
                    │   EC2 Instance  │  <- t2.micro (Free Tier)
                    │  (Amazon Linux) │
                    │                 │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        │                    │                    │
┌───────▼────────┐  ┌────────▼────────┐  ┌───────▼────────┐
│                │  │                 │  │                │
│  Nginx         │  │  Docker         │  │  Docker        │
│  Reverse Proxy │──│  Frontend       │  │  Backend       │
│  (Port 80/443) │  │  (Port 3000)    │  │  (Port 8080)   │
│                │  │                 │  │                │
└────────────────┘  └─────────────────┘  └────────┬───────┘
                                                   │
                                                   │ PostgreSQL
                                                   │ Connection
                                                   │
                                         ┌─────────▼──────────┐
                                         │                    │
                                         │   RDS PostgreSQL   │
                                         │   (t3.micro)       │
                                         │   Free Tier        │
                                         │                    │
                                         └────────────────────┘
```

## 🔧 Componentes

### 1. Amazon RDS - Base de Datos
- **Tipo:** PostgreSQL 15+
- **Instancia:** db.t3.micro (Free Tier)
- **Storage:** 20 GB SSD
- **Backups:** Automáticos (opcional)
- **High Availability:** Single-AZ (Free Tier)
- **Costo:** GRATIS primer año, ~$15-17/mes después

### 2. Amazon EC2 - Servidor de Aplicación
- **Tipo:** t2.micro (Free Tier)
- **OS:** Amazon Linux 2023 o Ubuntu 22.04
- **vCPU:** 1 core
- **RAM:** 1 GB
- **Storage:** 30 GB SSD
- **Costo:** GRATIS primer año, ~$8-10/mes después

### 3. Docker Containers en EC2
```
┌─────────────────────────────────────┐
│         EC2 Instance                │
│  ┌─────────────────────────────┐   │
│  │  Docker Network             │   │
│  │  ┌────────┐    ┌──────────┐ │   │
│  │  │Frontend│    │ Backend  │ │   │
│  │  │React   │◄───┤Spring    │ │   │
│  │  │:3000   │    │Boot:8080 │ │   │
│  │  └────────┘    └──────────┘ │   │
│  └─────────────────────────────┘   │
│           ▲                         │
│  ┌────────┴────────┐               │
│  │   Nginx :80     │               │
│  └─────────────────┘               │
└─────────────────────────────────────┘
```

### 4. Security Groups (Firewalls)

**EC2 Security Group:**
- Puerto 22 (SSH): Solo tu IP
- Puerto 80 (HTTP): Todo internet
- Puerto 443 (HTTPS): Todo internet
- Puertos 3000, 8080: Todo internet (solo para testing, cerrar después de configurar Nginx)

**RDS Security Group:**
- Puerto 5432 (PostgreSQL): Solo desde EC2 Security Group

## 🔄 Flujo de Datos

### Request de Usuario
```
1. Usuario → http://your-ec2-ip/
2. Nginx (EC2:80) → Frontend Container (EC2:3000)
3. Frontend renderiza en navegador del usuario

4. Usuario hace acción → API call a /api/...
5. Nginx (EC2:80) → Backend Container (EC2:8080)
6. Backend (EC2:8080) → RDS PostgreSQL (5432)
7. RDS responde → Backend
8. Backend responde → Nginx
9. Nginx responde → Usuario
```

### Con SSL (Después de configurar Let's Encrypt)
```
1. Usuario → https://yourdomain.com/
2. Nginx (EC2:443) [SSL Termination] → Frontend (EC2:3000)
3. ...mismo flujo que arriba
```

## 📁 Archivos de Configuración Importantes

```
golf-tournament-app/
├── 📄 DEPLOYMENT.md                    # Guía completa paso a paso
├── 📄 QUICKSTART_DEPLOYMENT.md         # Guía rápida
├── 📄 DEPLOYMENT_README.md             # Resumen de archivos
├── 📄 ARCHITECTURE.md                  # Este archivo
│
├── 🐳 docker-compose.production.yml    # Docker Compose para producción
├── 📝 .env.production.example          # Template de variables
│
├── 🔧 nginx-reverse-proxy.conf         # Configuración de Nginx
├── 📜 setup-ec2.sh                     # Script de setup inicial
├── 🚀 deploy.sh                        # Script de deployment
│
├── backend/
│   ├── Dockerfile                      # Build del backend
│   ├── src/.../SecurityConfig.java     # CORS configurado
│   └── src/.../application.yml         # Config dinámica
│
└── frontend/
    ├── Dockerfile                      # Build del frontend
    └── nginx.conf                      # Nginx interno del container
```

## 🔐 Variables de Entorno

### Backend (.env.production)
```bash
# Database
DB_HOST=your-rds-endpoint.rds.amazonaws.com
DB_PORT=5432
DB_NAME=golf_tournament
DB_USER=postgres
DB_PASSWORD=your-secure-password

# JWT
JWT_SECRET=your-generated-secret-key
JWT_EXPIRATION=86400000

# CORS
ALLOWED_ORIGINS=http://your-ip:3000,http://your-ip
```

### Frontend (build-time)
```bash
VITE_API_URL=http://your-ec2-ip:8080/api
```

## 🔒 Seguridad

### Implementado
- ✅ Security Groups (Firewall de AWS)
- ✅ JWT Authentication
- ✅ CORS configurado
- ✅ Passwords encriptados (BCrypt)
- ✅ SQL Injection protection (JPA)
- ✅ HTTPS ready (solo activar con SSL)

### Recomendado para Producción
- [ ] SSL/TLS con Let's Encrypt
- [ ] Cambiar passwords default
- [ ] Rotar JWT secrets regularmente
- [ ] Rate limiting en Nginx
- [ ] WAF (Web Application Firewall)
- [ ] Monitoring con CloudWatch
- [ ] Backups automáticos de RDS

## 📊 Escalabilidad

### Actual (Free Tier)
- **Usuarios concurrentes:** ~50-100
- **Requests/segundo:** ~10-20
- **Database size:** 20 GB
- **Traffic:** 15 GB/mes salida gratis

### Para Escalar (Futuro)
1. **Más tráfico:** 
   - EC2 t3.medium o t3.large
   - Auto Scaling Group
   - Application Load Balancer

2. **Más datos:**
   - RDS t3.medium
   - Aumentar storage
   - Read Replicas

3. **Mejor performance:**
   - ElastiCache (Redis) para caching
   - CloudFront CDN para frontend
   - RDS Multi-AZ para alta disponibilidad

4. **Múltiples regiones:**
   - Route 53 con geolocation
   - RDS Cross-Region Replicas
   - S3 para archivos estáticos

## 💰 Costo Detallado

### Mes 1-12 (Free Tier)
```
EC2 t2.micro:        $0    (750 hrs/mes gratis)
RDS t3.micro:        $0    (750 hrs/mes gratis)
Storage EC2:         $0    (30 GB gratis)
Storage RDS:         $0    (20 GB gratis)
Data Transfer:       $0    (15 GB gratis)
─────────────────────────
TOTAL:               $0 USD/mes
```

### Mes 13+ (Post Free Tier)
```
EC2 t2.micro:        $8.50  (On-Demand)
RDS t3.micro:        $16.00 (On-Demand)
Storage EC2 (30GB):  $3.00
Storage RDS (20GB):  $2.30
Data Transfer:       $1.00  (estimado)
─────────────────────────
TOTAL:               ~$30.80 USD/mes
```

### Optimizaciones de Costo
- **Reserved Instances:** Ahorra 30-50% comprando por 1-3 años
- **Lightsail:** Plan fijo $5-10/mes (más simple pero menos flexible)
- **Detener instancias:** Apaga cuando no uses (solo desarrollo)

## 🚀 Performance Esperado

### Free Tier (t2.micro + t3.micro)
- **Response time API:** 100-300ms
- **Page load:** 1-2 segundos
- **Throughput:** 10-20 req/s
- **Usuarios simultáneos:** 50-100

### Con Optimizaciones
- CloudFront: -50% page load
- ElastiCache: -70% response time API
- t3.medium: +300% throughput

## 📈 Monitoreo

### Incluido Gratis
- AWS CloudWatch Metrics básicos
- Docker logs: `docker-compose logs`
- Nginx access logs: `/var/log/nginx/`

### Recomendado
- AWS CloudWatch Alarms (para alertas)
- AWS CloudWatch Logs (centralizar logs)
- Grafana + Prometheus (monitoreo avanzado)

## 🔄 CI/CD (Futuro)

```
GitHub Actions
    │
    ├─► Build & Test
    │
    ├─► Build Docker Images
    │
    ├─► Push to ECR (AWS Container Registry)
    │
    └─► Deploy to EC2
        ├─► SSH to EC2
        ├─► Pull new images
        └─► Restart containers
```

---

**¿Preguntas?** Consulta [DEPLOYMENT.md](DEPLOYMENT.md) para guía completa.
