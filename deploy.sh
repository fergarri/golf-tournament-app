#!/bin/bash

# ============================================
# Deployment Script for Golf Tournament App
# Run this script on your EC2 instance to deploy/update the application
# ============================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}======================================"
echo "Golf Tournament App - Deployment"
echo "======================================${NC}"
echo ""

# Check if .env.production exists
if [ ! -f .env.production ]; then
    echo -e "${RED}ERROR: .env.production file not found!${NC}"
    echo ""
    echo "Please create .env.production from .env.production.example:"
    echo "  cp .env.production.example .env.production"
    echo "  nano .env.production  # Edit with your values"
    echo ""
    exit 1
fi

# Load environment variables
set -a
source .env.production
set +a

echo -e "${GREEN}[1/8] Checking Docker...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker is not installed or not in PATH${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}Docker Compose is not installed or not in PATH${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker and Docker Compose are available${NC}"
echo ""

echo -e "${GREEN}[2/8] Stopping existing containers...${NC}"
docker-compose -f docker-compose.production.yml down || true
echo ""

echo -e "${GREEN}[3/8] Removing old images...${NC}"
docker-compose -f docker-compose.production.yml rm -f || true
echo ""

echo -e "${GREEN}[4/8] Pulling latest code (if using git)...${NC}"
if [ -d .git ]; then
    git pull origin main || git pull origin master || echo "Could not pull from git, continuing..."
else
    echo -e "${YELLOW}Not a git repository, skipping pull${NC}"
fi
echo ""

echo -e "${GREEN}[5/8] Building Docker images...${NC}"
docker-compose -f docker-compose.production.yml build --no-cache
echo ""

echo -e "${GREEN}[6/8] Starting containers...${NC}"
docker-compose -f docker-compose.production.yml up -d
echo ""

echo -e "${GREEN}[7/8] Waiting for services to be healthy...${NC}"
sleep 10
echo ""

echo -e "${GREEN}[8/8] Checking container status...${NC}"
docker-compose -f docker-compose.production.yml ps
echo ""

echo -e "${GREEN}======================================"
echo "Deployment Complete!"
echo "======================================${NC}"
echo ""
echo -e "${BLUE}Service URLs:${NC}"
echo "  Frontend: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo 'YOUR_EC2_IP'):3000"
echo "  Backend:  http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo 'YOUR_EC2_IP'):8080/api"
echo ""
echo -e "${YELLOW}Useful commands:${NC}"
echo "  View logs:           docker-compose -f docker-compose.production.yml logs -f"
echo "  View backend logs:   docker-compose -f docker-compose.production.yml logs -f backend"
echo "  View frontend logs:  docker-compose -f docker-compose.production.yml logs -f frontend"
echo "  Stop services:       docker-compose -f docker-compose.production.yml down"
echo "  Restart services:    docker-compose -f docker-compose.production.yml restart"
echo ""
echo -e "${GREEN}Next steps:${NC}"
echo "  1. Configure nginx reverse proxy (see DEPLOYMENT.md)"
echo "  2. Set up SSL with Let's Encrypt (optional)"
echo "  3. Configure your domain DNS (optional)"
echo ""
