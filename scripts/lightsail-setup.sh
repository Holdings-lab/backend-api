#!/bin/bash

set -e

echo "🚀 AWS Lightsail Initial Setup Script"
echo "========================================"

# Update system
echo "📦 Updating system packages..."
sudo apt-get update
sudo apt-get upgrade -y

# Install Docker
echo "🐳 Installing Docker..."
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
newgrp docker

# Install Docker Compose
echo "📚 Installing Docker Compose..."
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Install Git
echo "🔧 Installing Git..."
sudo apt-get install -y git

# Install curl
echo "📡 Installing curl..."
sudo apt-get install -y curl

# Create app directory
echo "📁 Creating application directory..."
mkdir -p ~/app
cd ~/app

# Clone repository
echo "📥 Cloning repository..."
read -p "Enter your GitHub repository URL: " REPO_URL
git clone $REPO_URL .

# Create .env.prod file
echo "⚙️  Setting up environment variables..."
if [ ! -f .env.prod ]; then
    cp .env.example .env.prod
    echo "✏️  Please edit .env.prod with your actual values:"
    echo "   nano .env.prod"
fi

# Create necessary directories
echo "📂 Creating necessary directories..."
mkdir -p backend-api/api-server/src/main/resources
mkdir -p logs

# Setup firewall (if UFW is available)
echo "🔒 Configuring firewall..."
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
sudo ufw allow 5432/tcp
sudo ufw enable -y || echo "⚠️  UFW not available or already enabled"

# Setup log rotation
echo "📝 Setting up log rotation..."
sudo tee /etc/logrotate.d/app-logs > /dev/null <<EOF
/home/$USER/app/logs/*.log {
    daily
    rotate 7
    compress
    delaycompress
    notifempty
    create 0640 $USER $USER
}
EOF

# Create systemd service for docker-compose
echo "⚙️  Creating systemd service..."
sudo tee /etc/systemd/system/app-docker.service > /dev/null <<'EOF'
[Unit]
Description=App Docker Compose Service
Requires=docker.service
After=docker.service

[Service]
Type=simple
WorkingDirectory=/home/ubuntu/app
ExecStart=/usr/local/bin/docker-compose -f docker-compose.prod.yml up
ExecStop=/usr/local/bin/docker-compose -f docker-compose.prod.yml down
Restart=always
RestartSec=10
User=ubuntu

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload

# Create deployment helper script
echo "🔧 Creating deployment helper script..."
tee ~/app/deploy.sh > /dev/null <<'EOF'
#!/bin/bash
set -e

echo "🚀 Deploying application..."

# Load environment
set -a
source /home/ubuntu/app/.env.prod
set +a

# Pull latest code
echo "📥 Pulling latest code..."
git pull origin main || git pull origin develop

# Login to Docker registry
echo "🔐 Logging in to Docker registry..."
# Assuming you've already configured docker credentials

# Pull and start containers
echo "🐳 Starting Docker containers..."
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d

# Wait for health checks
echo "⏳ Waiting for services to be healthy..."
sleep 15

# Check status
echo "📊 Service status:"
docker compose -f docker-compose.prod.yml ps

echo "✅ Deployment complete!"
EOF

chmod +x ~/app/deploy.sh

echo ""
echo "✅ Setup Complete!"
echo ""
echo "📋 Next Steps:"
echo "1. Edit .env.prod with your production values:"
echo "   nano ~/app/.env.prod"
echo ""
echo "2. If using Firebase, copy your serviceAccountKey.json:"
echo "   scp serviceAccountKey.json ubuntu@YOUR_LIGHTSAIL_IP:~/app/backend-api/api-server/src/main/resources/"
echo ""
echo "3. Start the application:"
echo "   cd ~/app"
echo "   ./deploy.sh"
echo ""
echo "4. Or enable auto-start on boot:"
echo "   sudo systemctl enable app-docker"
echo "   sudo systemctl start app-docker"
echo ""
echo "5. View logs:"
echo "   docker compose -f docker-compose.prod.yml logs -f"
echo ""
echo "🌐 Your API will be available at: http://YOUR_LIGHTSAIL_IP:8080"
echo ""
