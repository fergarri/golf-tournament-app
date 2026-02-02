# 🚀 Guía de Deployment en AWS

Esta guía te llevará paso a paso para deployar la aplicación Golf Tournament en AWS usando EC2 + RDS con el Free Tier.

## 📋 Tabla de Contenidos

1. [Pre-requisitos](#pre-requisitos)
2. [Parte 1: Crear Base de Datos RDS](#parte-1-crear-base-de-datos-rds)
3. [Parte 2: Crear Instancia EC2](#parte-2-crear-instancia-ec2)
4. [Parte 3: Configurar EC2](#parte-3-configurar-ec2)
5. [Parte 4: Deployar la Aplicación](#parte-4-deployar-la-aplicación)
6. [Parte 5: Configurar Nginx (Opcional)](#parte-5-configurar-nginx-opcional)
7. [Parte 6: Configurar SSL con Let's Encrypt (Opcional)](#parte-6-configurar-ssl-con-lets-encrypt-opcional)
8. [Troubleshooting](#troubleshooting)
9. [Comandos Útiles](#comandos-útiles)

---

## Pre-requisitos

- ✅ Cuenta de AWS (https://aws.amazon.com)
- ✅ Tarjeta de crédito (para AWS, no se cobrará nada si usas Free Tier)
- ✅ Código de la aplicación listo
- ✅ Cliente SSH (Terminal en Mac/Linux, PuTTY en Windows)

---

## Parte 1: Crear Base de Datos RDS

### 1.1 Acceder a RDS

1. Inicia sesión en AWS Console (https://console.aws.amazon.com)
2. Busca "RDS" en la barra de búsqueda
3. Haz clic en "Create database"

### 1.2 Configurar la Base de Datos

**Engine options:**
- Engine type: `PostgreSQL`
- Version: `PostgreSQL 15.x` o superior (cualquier versión reciente)

**Templates:**
- Selecciona: `Free tier` ⭐ (IMPORTANTE)

**Settings:**
- DB instance identifier: `golf-tournament-db`
- Master username: `postgres`
- Master password: **Crea una contraseña segura y guárdala** 🔑

**DB instance class:**
- Ya debería estar seleccionado: `db.t3.micro` (Free Tier)

**Storage:**
- Allocated storage: `20 GB` (Free Tier permite hasta 20 GB)
- ❌ **Desmarcar** "Enable storage autoscaling" (para evitar costos)

**Connectivity:**
- Virtual Private Cloud (VPC): `Default VPC`
- Public access: `Yes` ⭐ (para poder conectarte desde EC2)
- VPC security group: `Create new`
  - New VPC security group name: `golf-tournament-db-sg`

**Additional configuration:**
- Initial database name: `golf_tournament` ⭐ (IMPORTANTE)
- ❌ **Desmarcar** "Enable automated backups" (Free Tier solo cubre 20 GB)
- ❌ **Desmarcar** "Enable encryption" (opcional, pero genera complejidad)

### 1.3 Crear la Base de Datos

1. Haz clic en "Create database"
2. **Espera 5-10 minutos** mientras se crea ⏳
3. Copia el **Endpoint** cuando esté disponible (ejemplo: `golf-tournament-db.xxxxx.us-east-1.rds.amazonaws.com`)

### 1.4 Configurar Security Group

1. Ve a la pestaña "Connectivity & security" de tu base de datos
2. Haz clic en el Security Group
3. Haz clic en "Edit inbound rules"
4. **Importante:** Necesitarás agregar una regla para permitir conexiones desde EC2:
   - Tipo: `PostgreSQL`
   - Protocol: `TCP`
   - Port: `5432`
   - Source: `Custom` → **Aquí pondrás el Security Group de EC2 más tarde**

**Por ahora, déjalo así y continuaremos después de crear EC2.**

---

## Parte 2: Crear Instancia EC2

### 2.1 Acceder a EC2

1. En AWS Console, busca "EC2"
2. Haz clic en "Launch Instance"

### 2.2 Configurar la Instancia

**Name:**
- Name: `golf-tournament-app`

**Application and OS Images (AMI):**
- Amazon Machine Image: `Amazon Linux 2023 AMI` (recomendado)
- O también puedes usar: `Ubuntu Server 22.04 LTS`

**Instance type:**
- Selecciona: `t2.micro` ⭐ (Free Tier)

**Key pair (login):**
- Haz clic en "Create new key pair"
  - Key pair name: `golf-tournament-key`
  - Key pair type: `RSA`
  - Private key format: `.pem` (Mac/Linux) o `.ppk` (Windows con PuTTY)
  - **Descarga y guarda el archivo .pem/ppk en un lugar seguro** 🔑

**Network settings:**
- Haz clic en "Edit"
- Auto-assign public IP: `Enable`
- Firewall (security groups): `Create security group`
  - Security group name: `golf-tournament-app-sg`
  - Description: `Security group for Golf Tournament App`
  
**Agregar reglas de seguridad:**
- ✅ SSH (22) - Source: `My IP` (tu IP actual)
- ➕ Add security group rule:
  - Type: `HTTP`
  - Port: `80`
  - Source: `Anywhere (0.0.0.0/0)`
- ➕ Add security group rule:
  - Type: `HTTPS`
  - Port: `443`
  - Source: `Anywhere (0.0.0.0/0)`
- ➕ Add security group rule:
  - Type: `Custom TCP`
  - Port: `8080`
  - Source: `Anywhere (0.0.0.0/0)`
- ➕ Add security group rule:
  - Type: `Custom TCP`
  - Port: `3000`
  - Source: `Anywhere (0.0.0.0/0)`

**Configure storage:**
- Size: `30 GB` (Free Tier permite hasta 30 GB)
- Volume type: `gp3` o `gp2`

### 2.3 Lanzar la Instancia

1. Haz clic en "Launch instance"
2. Espera a que el estado sea `Running` (2-3 minutos)
3. **Copia la IP pública** de tu instancia 📝

### 2.4 Conectar RDS con EC2

Ahora que tienes EC2 creado, vuelve a RDS para permitir conexiones:

1. Ve a RDS → Tu base de datos → Security Group
2. Edit inbound rules
3. Agrega/modifica la regla:
   - Type: `PostgreSQL`
   - Port: `5432`
   - Source: `Custom` → Busca y selecciona `golf-tournament-app-sg` (el SG de EC2)
4. Save rules

---

## Parte 3: Configurar EC2

### 3.1 Conectarse por SSH

**En Mac/Linux:**
```bash
# Dale permisos al archivo .pem
chmod 400 ~/Downloads/golf-tournament-key.pem

# Conéctate a EC2
ssh -i ~/Downloads/golf-tournament-key.pem ec2-user@YOUR_EC2_PUBLIC_IP
```

**En Windows (con PowerShell):**
```powershell
ssh -i C:\Users\TuUsuario\Downloads\golf-tournament-key.pem ec2-user@YOUR_EC2_PUBLIC_IP
```

Reemplaza `YOUR_EC2_PUBLIC_IP` con la IP pública de tu instancia.

### 3.2 Ejecutar Script de Setup

Una vez conectado a EC2:

```bash
# Descargar el script de setup
curl -o setup-ec2.sh https://raw.githubusercontent.com/TU_USUARIO/TU_REPO/main/setup-ec2.sh

# O si tienes el código en tu máquina, usa SCP para copiarlo:
# En tu máquina local:
# scp -i ~/Downloads/golf-tournament-key.pem setup-ec2.sh ec2-user@YOUR_EC2_PUBLIC_IP:~/

# Dar permisos de ejecución
chmod +x setup-ec2.sh

# Ejecutar el script
./setup-ec2.sh
```

El script instalará:
- ✅ Docker
- ✅ Docker Compose
- ✅ Git
- ✅ Nginx
- ✅ Herramientas útiles

### 3.3 Reiniciar Sesión

**IMPORTANTE:** Después de ejecutar el script, debes cerrar sesión y volver a conectarte:

```bash
exit

# Vuelve a conectarte
ssh -i ~/Downloads/golf-tournament-key.pem ec2-user@YOUR_EC2_PUBLIC_IP
```

Esto es necesario para que los cambios de Docker tomen efecto.

---

## Parte 4: Deployar la Aplicación

### 4.1 Clonar el Repositorio

```bash
# Opción A: Si tu código está en GitHub/GitLab
cd /opt/golf-tournament
git clone https://github.com/TU_USUARIO/golf-tournament-app.git .

# Opción B: Si el código está en tu máquina local, usa SCP desde tu máquina:
# scp -i ~/Downloads/golf-tournament-key.pem -r /ruta/local/golf-tournament-app/* ec2-user@YOUR_EC2_PUBLIC_IP:/opt/golf-tournament/
```

### 4.2 Configurar Variables de Entorno

```bash
cd /opt/golf-tournament

# Copiar el archivo de ejemplo
cp .env.production.example .env.production

# Editar el archivo
nano .env.production
```

**Completa con tus valores:**

```bash
# Base de datos
DB_HOST=golf-tournament-db.xxxxx.us-east-1.rds.amazonaws.com  # Tu endpoint de RDS
DB_PORT=5432
DB_NAME=golf_tournament
DB_USER=postgres
DB_PASSWORD=TU_PASSWORD_DE_RDS  # La que configuraste en RDS

# JWT Secret (genera uno seguro)
JWT_SECRET=$(openssl rand -base64 64)  # Ejecuta este comando y copia el resultado
JWT_EXPIRATION=86400000

# Logging
SHOW_SQL=false
SQL_LOG_LEVEL=WARN

# CORS - Reemplaza YOUR_EC2_PUBLIC_IP con tu IP de EC2
ALLOWED_ORIGINS=http://YOUR_EC2_PUBLIC_IP:3000,http://YOUR_EC2_PUBLIC_IP

# Frontend API URL
VITE_API_URL=http://YOUR_EC2_PUBLIC_IP:8080/api
```

**Generar JWT_SECRET:**
```bash
openssl rand -base64 64
```
Copia el resultado y pégalo en `JWT_SECRET`.

**Guardar y salir de nano:**
- Presiona `Ctrl + X`
- Presiona `Y` para confirmar
- Presiona `Enter` para guardar

### 4.3 Deployar

```bash
# Ejecutar el script de deployment
./deploy.sh
```

Este script:
1. ✅ Verifica que Docker esté instalado
2. ✅ Detiene contenedores existentes (si los hay)
3. ✅ Construye las imágenes Docker
4. ✅ Inicia los contenedores
5. ✅ Muestra el estado de los servicios

### 4.4 Verificar que Funciona

**Verificar logs:**
```bash
docker-compose -f docker-compose.production.yml logs -f backend
```

**Verificar en el navegador:**
- Frontend: `http://YOUR_EC2_PUBLIC_IP:3000`
- Backend API: `http://YOUR_EC2_PUBLIC_IP:8080/api/locations/countries`

Si ves la aplicación, ¡felicitaciones! 🎉

---

## Parte 5: Configurar Nginx (Opcional pero Recomendado)

Nginx actuará como reverse proxy para:
- Servir todo desde el puerto 80 (HTTP estándar)
- Simplificar las URLs
- Preparar para SSL

### 5.1 Configurar Nginx

```bash
# Copiar la configuración de nginx
sudo cp nginx-reverse-proxy.conf /etc/nginx/sites-available/golf-tournament

# En Amazon Linux, crear directorio si no existe
sudo mkdir -p /etc/nginx/sites-available
sudo mkdir -p /etc/nginx/sites-enabled

# Crear symlink
sudo ln -s /etc/nginx/sites-available/golf-tournament /etc/nginx/sites-enabled/

# Editar el archivo
sudo nano /etc/nginx/sites-available/golf-tournament
```

**Reemplaza** `your-domain.com` con tu dominio (si tienes) o con la IP de EC2.

### 5.2 Actualizar configuración principal de Nginx

**Para Amazon Linux:**
```bash
sudo nano /etc/nginx/nginx.conf
```

**Agrega al final del bloque `http {}`:**
```nginx
include /etc/nginx/sites-enabled/*;
```

### 5.3 Probar y Reiniciar Nginx

```bash
# Probar configuración
sudo nginx -t

# Si todo está OK, reiniciar
sudo systemctl restart nginx

# Verificar que esté corriendo
sudo systemctl status nginx
```

Ahora puedes acceder a tu app en:
- Frontend: `http://YOUR_EC2_PUBLIC_IP/`
- Backend: `http://YOUR_EC2_PUBLIC_IP/api/`

---

## Parte 6: Configurar SSL con Let's Encrypt (Opcional)

**Requisito:** Debes tener un dominio propio apuntando a tu EC2.

### 6.1 Instalar Certbot

```bash
# Amazon Linux 2023
sudo dnf install -y certbot python3-certbot-nginx

# Ubuntu
sudo apt install -y certbot python3-certbot-nginx
```

### 6.2 Obtener Certificado

```bash
sudo certbot --nginx -d yourdomain.com -d www.yourdomain.com
```

Sigue las instrucciones:
- Ingresa tu email
- Acepta los términos
- Certbot configurará automáticamente nginx para HTTPS

### 6.3 Auto-renovación

Certbot configura auto-renovación automáticamente. Puedes probarla:

```bash
sudo certbot renew --dry-run
```

### 6.4 Actualizar Variables de Entorno

Actualiza `.env.production`:
```bash
ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com
VITE_API_URL=https://yourdomain.com/api
```

Redeploy:
```bash
./deploy.sh
```

---

## Troubleshooting

### Problema: No puedo conectarme por SSH

**Solución:**
```bash
# Verifica los permisos del archivo .pem
chmod 400 golf-tournament-key.pem

# Verifica que tu IP esté permitida en el Security Group de EC2
# Ve a AWS Console → EC2 → Security Groups → Inbound rules
```

### Problema: El backend no se conecta a RDS

**Solución:**
```bash
# Verifica que el Security Group de RDS permita conexiones desde EC2
# Ve a AWS Console → RDS → Security Groups
# Debe tener una regla que permita PostgreSQL (5432) desde el SG de EC2

# Prueba la conexión manualmente desde EC2:
sudo yum install postgresql15 -y
psql -h YOUR_RDS_ENDPOINT -U postgres -d golf_tournament
```

### Problema: El frontend no puede llamar al backend

**Solución:**
```bash
# Verifica que VITE_API_URL esté correctamente configurado
cat .env.production | grep VITE_API_URL

# Verifica que ALLOWED_ORIGINS incluya tu dominio/IP
cat .env.production | grep ALLOWED_ORIGINS

# Redeploy después de cambios
./deploy.sh
```

### Problema: Docker no está disponible después del setup

**Solución:**
```bash
# Cierra sesión y vuelve a entrar
exit
ssh -i golf-tournament-key.pem ec2-user@YOUR_EC2_PUBLIC_IP
```

### Problema: La aplicación se cayó

**Solución:**
```bash
# Ver logs
docker-compose -f docker-compose.production.yml logs -f

# Reiniciar servicios
docker-compose -f docker-compose.production.yml restart

# Si nada funciona, redeploy completo
./deploy.sh
```

---

## Comandos Útiles

### Gestión de Contenedores

```bash
# Ver estado de contenedores
docker-compose -f docker-compose.production.yml ps

# Ver logs en tiempo real
docker-compose -f docker-compose.production.yml logs -f

# Ver logs solo del backend
docker-compose -f docker-compose.production.yml logs -f backend

# Ver logs solo del frontend
docker-compose -f docker-compose.production.yml logs -f frontend

# Reiniciar servicios
docker-compose -f docker-compose.production.yml restart

# Detener servicios
docker-compose -f docker-compose.production.yml down

# Iniciar servicios
docker-compose -f docker-compose.production.yml up -d

# Reconstruir y reiniciar
./deploy.sh
```

### Gestión de EC2

```bash
# Ver uso de disco
df -h

# Ver uso de memoria
free -h

# Ver procesos
htop

# Ver logs del sistema
sudo journalctl -xe
```

### Actualizar la Aplicación

```bash
# Si usas Git
cd /opt/golf-tournament
git pull origin main
./deploy.sh

# Si copias archivos manualmente desde tu máquina local:
# scp -i golf-tournament-key.pem -r ./backend/* ec2-user@YOUR_EC2_PUBLIC_IP:/opt/golf-tournament/backend/
# scp -i golf-tournament-key.pem -r ./frontend/* ec2-user@YOUR_EC2_PUBLIC_IP:/opt/golf-tournament/frontend/
# Luego SSH y ./deploy.sh
```

### Backup de Base de Datos

```bash
# Desde EC2, hacer backup
pg_dump -h YOUR_RDS_ENDPOINT -U postgres -d golf_tournament > backup_$(date +%Y%m%d).sql

# Restaurar backup
psql -h YOUR_RDS_ENDPOINT -U postgres -d golf_tournament < backup_20260127.sql
```

---

## 💰 Estimación de Costos

### Primer Año (Free Tier)
- **EC2 t2.micro:** GRATIS (750 horas/mes)
- **RDS t3.micro:** GRATIS (750 horas/mes)
- **Storage:** GRATIS (hasta 30 GB EC2 + 20 GB RDS)
- **Data Transfer:** GRATIS (hasta 15 GB/mes salida)

**Total primer año:** $0 USD 🎉

### Después del Primer Año
- **EC2 t2.micro:** ~$8-10/mes
- **RDS t3.micro:** ~$15-17/mes
- **Storage:** ~$3-5/mes

**Total:** ~$26-32/mes

### Para Reducir Costos
- Usar RDS MySQL en vez de PostgreSQL (más barato)
- Detener servicios cuando no se usan
- Usar Amazon Lightsail ($5-10/mes todo incluido)

---

## 🎯 Próximos Pasos

1. ✅ Configurar un dominio personalizado
2. ✅ Configurar SSL/HTTPS
3. ✅ Configurar backups automáticos
4. ✅ Configurar monitoreo (CloudWatch)
5. ✅ Configurar CI/CD con GitHub Actions

---

## 📞 Soporte

Si tienes problemas, revisa:
1. Los logs de Docker
2. Los Security Groups en AWS
3. Las variables de entorno en `.env.production`

¡Buena suerte con tu deployment! 🚀⛳
