# ⚡ Quick Start - Deployment en AWS

**Para la guía completa detallada, ver [DEPLOYMENT.md](DEPLOYMENT.md)**

## 🎯 Resumen Ultra-Rápido

### Lo Que TÚ Debes Hacer en AWS Console

#### 1. Crear RDS PostgreSQL (~5 min)
- Ir a RDS → Create Database
- Template: **Free tier**
- Engine: **PostgreSQL**
- DB identifier: `golf-tournament-db`
- Username: `postgres`, Password: `[tu_password_seguro]`
- Initial database name: `golf_tournament` ⚠️
- Public access: **Yes**
- Copiar el **Endpoint** cuando esté listo

#### 2. Crear EC2 (~5 min)
- Ir a EC2 → Launch Instance
- AMI: **Amazon Linux 2023** o Ubuntu 22.04
- Instance type: **t2.micro** (Free tier)
- Create new key pair: Descargar `golf-tournament-key.pem`
- Security Group: Permitir puertos 22, 80, 443, 3000, 8080
- Launch
- Copiar la **IP Pública**

#### 3. Conectar RDS con EC2 (~2 min)
- RDS → Security Group → Edit inbound rules
- Agregar regla: PostgreSQL (5432) desde el Security Group de EC2

### Lo Que Ejecutarás en Tu Terminal

#### 4. Conectarte a EC2
```bash
chmod 400 golf-tournament-key.pem
ssh -i golf-tournament-key.pem ec2-user@YOUR_EC2_IP
```

#### 5. Setup Inicial (solo una vez)
```bash
# Copiar el script a EC2 (desde tu máquina local)
scp -i golf-tournament-key.pem setup-ec2.sh ec2-user@YOUR_EC2_IP:~/

# En EC2, ejecutar:
chmod +x setup-ec2.sh
./setup-ec2.sh

# Cerrar y volver a conectar
exit
ssh -i golf-tournament-key.pem ec2-user@YOUR_EC2_IP
```

#### 6. Copiar el Proyecto a EC2
```bash
# Opción A: Desde GitHub
cd /opt/golf-tournament
git clone https://github.com/TU_USER/golf-tournament-app.git .

# Opción B: Desde tu máquina (ejecutar en tu máquina local)
scp -i golf-tournament-key.pem -r ./golf-tournament-app/* ec2-user@YOUR_EC2_IP:/opt/golf-tournament/
```

#### 7. Configurar Variables
```bash
cd /opt/golf-tournament
cp .env.production.example .env.production
nano .env.production
```

**Edita estos valores:**
```bash
DB_HOST=tu-rds-endpoint.xxxxx.rds.amazonaws.com
DB_PASSWORD=tu_password_de_rds
JWT_SECRET=$(openssl rand -base64 64)  # Genera uno nuevo
ALLOWED_ORIGINS=http://TU_EC2_IP:3000,http://TU_EC2_IP
VITE_API_URL=http://TU_EC2_IP:8080/api
```

#### 8. Deploy
```bash
./deploy.sh
```

#### 9. Verificar
Abre en tu navegador:
- Frontend: `http://TU_EC2_IP:3000`
- Backend: `http://TU_EC2_IP:8080/api/locations/countries`

---

## ✅ Checklist Rápido

- [ ] Crear RDS PostgreSQL (Free Tier)
- [ ] Anotar endpoint y password de RDS
- [ ] Crear EC2 t2.micro (Free Tier)
- [ ] Descargar archivo .pem
- [ ] Anotar IP pública de EC2
- [ ] Configurar Security Groups (RDS ← EC2)
- [ ] SSH a EC2
- [ ] Ejecutar setup-ec2.sh
- [ ] Reconectar a EC2
- [ ] Copiar código a /opt/golf-tournament
- [ ] Crear .env.production con tus valores
- [ ] Ejecutar ./deploy.sh
- [ ] Probar en el navegador

---

## 🆘 Comandos de Emergencia

```bash
# Ver logs
docker-compose -f docker-compose.production.yml logs -f

# Reiniciar todo
./deploy.sh

# Ver estado
docker-compose -f docker-compose.production.yml ps

# Detener todo
docker-compose -f docker-compose.production.yml down
```

---

## 💰 Costo

- **Primer año:** GRATIS (Free Tier)
- **Después:** ~$26-32/mes

---

## 📚 Documentación Completa

Para pasos detallados, troubleshooting, SSL, dominio personalizado, etc:

👉 **[DEPLOYMENT.md](DEPLOYMENT.md)**

---

**Tiempo total estimado:** 30-45 minutos para el primer deployment

¡Éxito! 🚀
