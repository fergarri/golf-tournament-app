# 📦 Archivos de Deployment Preparados

¡Todo está listo para deployar tu aplicación en AWS! Aquí está todo lo que he preparado:

## 📄 Archivos Creados

### 1. **DEPLOYMENT.md** 📖
Guía completa paso a paso para deployar en AWS. Incluye:
- Creación de RDS (PostgreSQL)
- Creación de EC2
- Configuración de Security Groups
- Deployment completo
- Configuración de SSL opcional
- Troubleshooting y comandos útiles

### 2. **docker-compose.production.yml** 🐳
Docker Compose optimizado para producción con:
- Health checks
- Logging limitado
- Configuración para RDS externo
- Variables de entorno de producción

### 3. **.env.production.example** ⚙️
Template de variables de entorno que incluye:
- Configuración de base de datos (RDS)
- JWT secret
- CORS origins
- API URLs

### 4. **setup-ec2.sh** 🛠️
Script automatizado que instala en EC2:
- Docker y Docker Compose
- Git
- Nginx
- Herramientas útiles (htop, wget, curl)

### 5. **deploy.sh** 🚀
Script de deployment que:
- Verifica dependencias
- Detiene contenedores antiguos
- Construye nuevas imágenes
- Inicia servicios
- Muestra estado y logs

### 6. **nginx-reverse-proxy.conf** 🔀
Configuración de Nginx para:
- Reverse proxy (puerto 80)
- SSL preparado (comentado, listo para activar)
- Headers de seguridad
- Health checks

### 7. **SecurityConfig.java actualizado** 🔒
CORS configurado dinámicamente desde variables de entorno para soportar:
- Desarrollo (localhost)
- Producción (tu dominio/IP de EC2)

---

## 🚀 Cómo Usar

### Paso 1: Preparación Local (YA ESTÁ HECHO ✅)
Todos los archivos están listos en tu proyecto.

### Paso 2: Sigue la Guía
Abre `DEPLOYMENT.md` y sigue las instrucciones paso a paso.

### Paso 3: Comandos Rápidos

**En tu máquina local** (opcional - subir a GitHub):
```bash
git add .
git commit -m "Add production deployment configuration"
git push origin main
```

**En EC2** (después de seguir DEPLOYMENT.md):
```bash
# Setup inicial (solo una vez)
./setup-ec2.sh
exit  # Cerrar sesión y volver a entrar

# Configurar variables
cp .env.production.example .env.production
nano .env.production  # Editar con tus valores

# Deployar
./deploy.sh
```

---

## ⚠️ IMPORTANTE - Archivos Sensibles

**NUNCA** commitees estos archivos a Git:
- ❌ `.env.production` (contiene passwords y secrets)
- ❌ `*.pem` (llaves SSH de AWS)
- ❌ `*.ppk` (llaves SSH de AWS)

Estos archivos YA están en `.gitignore`.

---

## 📋 Checklist de Deployment

- [ ] Leer `DEPLOYMENT.md` completo
- [ ] Crear cuenta de AWS
- [ ] Crear RDS PostgreSQL (siguiendo la guía)
- [ ] Crear EC2 t2.micro (siguiendo la guía)
- [ ] Configurar Security Groups
- [ ] Conectarse a EC2 por SSH
- [ ] Ejecutar `setup-ec2.sh`
- [ ] Clonar/copiar el código a EC2
- [ ] Configurar `.env.production`
- [ ] Ejecutar `deploy.sh`
- [ ] Probar la aplicación
- [ ] (Opcional) Configurar dominio
- [ ] (Opcional) Configurar SSL

---

## 💰 Costo Estimado

- **Primer año:** GRATIS con AWS Free Tier
- **Después:** ~$26-32/mes

Ver detalles en `DEPLOYMENT.md`.

---

## 🆘 ¿Problemas?

Consulta la sección **Troubleshooting** en `DEPLOYMENT.md`.

Comandos útiles:
```bash
# Ver logs
docker-compose -f docker-compose.production.yml logs -f

# Reiniciar
docker-compose -f docker-compose.production.yml restart

# Estado
docker-compose -f docker-compose.production.yml ps
```

---

## 📞 Siguiente Nivel

Una vez que tengas todo funcionando, considera:

1. **Configurar CI/CD:** GitHub Actions para deployment automático
2. **Monitoreo:** AWS CloudWatch para logs y métricas
3. **Backups:** Automatizar backups de la base de datos
4. **CDN:** CloudFront para mejor performance del frontend
5. **Dominio:** Route 53 + SSL con Let's Encrypt

---

¡Éxito con tu deployment! 🎉⛳
