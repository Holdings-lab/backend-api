#!/bin/bash

set -e

echo "📊 AWS Lightsail $12 (2GB) 초기 설정 스크립트"
echo "==========================================="
echo "구성: PostgreSQL + API Server + Data ML (배치)"
echo ""

# 시스템 업데이트
echo "📦 시스템 업데이트 중..."
sudo apt-get update
sudo apt-get upgrade -y
sudo apt-get install -y git curl wget htop

# Docker 설치
echo "🐳 Docker 설치 중..."
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
newgrp docker

# Docker Compose 설치
echo "📚 Docker Compose 설치 중..."
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 앱 디렉토리 생성
echo "📁 앱 디렉토리 생성 중..."
mkdir -p ~/app
cd ~/app

# GitHub 저장소 클론
echo "📥 저장소 클론 중..."
read -p "GitHub 저장소 URL 입력: " REPO_URL
git clone $REPO_URL .

# 환경 파일 설정
echo "⚙️  환경 파일 설정 중..."
if [ ! -f .env.prod ]; then
    cp .env.example .env.prod
    echo ""
    echo "⚠️  .env.prod 파일을 생성했습니다."
    echo "편집이 필요합니다:"
    echo "   nano ~/.env.prod"
    echo ""
    echo "필수 입력 항목:"
    echo "   - POSTGRES_PASSWORD: 강력한 패스워드 (예: MyP@ssw0rd123!)"
    echo "   - WEBHOOK_SECRET: 웹훅 보안 키"
    echo ""
fi

# 필수 디렉토리 생성
echo "📂 필수 디렉토리 생성 중..."
mkdir -p backend-api/api-server/src/main/resources
mkdir -p data-ml/logs
mkdir -p logs

# 스왑 메모리 설정 (옵션이지만 추천)
echo "💾 스왑 메모리 설정 중 (2GB)..."
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
echo "✓ 스왑 설정 완료 (합계 4GB 가능메모리)"

# 방화벽 설정
echo "🔒 방화벽 설정 중..."
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp  # API Server
sudo ufw allow 9000/tcp  # Data ML Scheduler
sudo ufw enable -y || echo "⚠️  UFW는 이미 활성화되어 있습니다"

# 로그 로테이션 설정
echo "📝 로그 로테이션 설정 중..."
sudo tee /etc/logrotate.d/app-logs > /dev/null <<EOF
/home/ubuntu/app/logs/*.log {
    daily
    rotate 7
    compress
    delaycompress
    notifempty
    create 0640 ubuntu ubuntu
}

/home/ubuntu/app/data-ml/logs/*.log {
    daily
    rotate 7
    compress
    delaycompress
    notifempty
    create 0640 ubuntu ubuntu
}
EOF

# Systemd 서비스 생성
echo "⚙️  시스템 서비스 생성 중..."
sudo tee /etc/systemd/system/app-docker.service > /dev/null <<'EOF'
[Unit]
Description=PWA App Docker Compose (API + DB + ML)
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
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload

# 배포 헬퍼 스크립트 생성
echo "🔧 배포 헬퍼 스크립트 생성 중..."
tee ~/app/deploy.sh > /dev/null <<'EOF'
#!/bin/bash
set -e

echo "🚀 애플리케이션 배포 중..."
echo ""

# 환경 로드
set -a
source /home/ubuntu/app/.env.prod
set +a

echo "📥 최신 코드 pull 중..."
git pull origin main || git pull origin develop

echo "🐳 Docker 컨테이너 시작 중..."
docker compose -f docker-compose.prod.yml down --remove-orphans 2>/dev/null || true
docker compose -f docker-compose.prod.yml up -d

echo "⏳ 서비스 시작 대기 중 (15초)..."
sleep 15

echo ""
echo "📊 서비스 상태:"
docker compose -f docker-compose.prod.yml ps

echo ""
echo "✅ 배포 완료!"
echo ""
echo "접속 정보:"
echo "  - API Server: http://localhost:8080"
echo "  - Data ML Scheduler: http://localhost:9000"
echo "  - PostgreSQL: localhost:5432"
echo ""
echo "로그 확인:"
echo "  - API: docker compose logs -f api-server"
echo "  - DB: docker compose logs -f postgres"
echo "  - ML: docker compose logs -f data-ml"
echo ""
EOF

chmod +x ~/app/deploy.sh

echo ""
echo "==========================================3"
echo "✅ 초기 설정 완료!"
echo "==========================================="
echo ""
echo "📋 다음 단계:"
echo ""
echo "1️⃣  환경변수 설정:"
echo "   nano ~/.env.prod"
echo ""
echo "   필수 설정:"
echo "   - POSTGRES_PASSWORD=your_secure_password"
echo "   - WEBHOOK_SECRET=your_webhook_secret"
echo ""
echo "2️⃣  Firebase 키 파일 복사 (선택사항):"
echo "   scp serviceAccountKey.json ubuntu@YOUR_IP:~/app/backend-api/api-server/src/main/resources/"
echo ""
echo "3️⃣  애플리케이션 시작:"
echo "   cd ~/app"
echo "   bash deploy.sh"
echo ""
echo "4️⃣  자동 재부팅 시 자동 시작 설정:"
echo "   sudo systemctl enable app-docker"
echo "   sudo systemctl start app-docker"
echo ""
echo "5️⃣  배치 작업 모니터링:"
echo "   curl http://localhost:9000/health"
echo "   curl http://localhost:9000/jobs"
echo ""
echo "📊 Lightsail $12 리소스 분배:"
echo "   - PostgreSQL: ~150-200MB"
echo "   - API Server: ~400-500MB"
echo "   - Data ML: ~300-800MB (배치 실행 시만 사용)"
echo "   - 여유: ~400-600MB"
echo ""
echo "🔍 원격 접속:"
echo "   ssh -i key.pem ubuntu@YOUR_LIGHTSAIL_IP"
echo ""
