#!/bin/bash

# Server Setup Script for Firewall Web Application
# Run this script on your production server

set -e

# Configuration
APP_NAME="firewallweb"
APP_USER="firewallweb"
APP_DIR="/opt/firewallweb"
DOMAIN="your-domain.com"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if running as root
if [[ $EUID -ne 0 ]]; then
   print_error "This script must be run as root"
   exit 1
fi

print_status "Starting server setup for $APP_NAME..."

# Update system
print_status "Updating system packages..."
apt update && apt upgrade -y

# Install required packages
print_status "Installing required packages..."
apt install -y openjdk-17-jdk maven nginx certbot python3-certbot-nginx ufw fail2ban

# Create application user
print_status "Creating application user..."
if ! id "$APP_USER" &>/dev/null; then
    useradd -r -s /bin/bash -d $APP_DIR $APP_USER
else
    print_warning "User $APP_USER already exists"
fi

# Create application directory
print_status "Creating application directory..."
mkdir -p $APP_DIR
chown $APP_USER:$APP_USER $APP_DIR

# Setup MongoDB
print_status "Setting up MongoDB..."
if ! systemctl is-active --quiet mongod; then
    # Install MongoDB
    wget -qO - https://www.mongodb.org/static/pgp/server-6.0.asc | apt-key add -
    echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/6.0 multiverse" | tee /etc/apt/sources.list.d/mongodb-org-6.0.list
    apt update
    apt install -y mongodb-org
    
    # Start and enable MongoDB
    systemctl start mongod
    systemctl enable mongod
else
    print_warning "MongoDB is already running"
fi

# Configure firewall
print_status "Configuring firewall..."
ufw --force enable
ufw allow ssh
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 1402/tcp

# Configure Nginx
print_status "Configuring Nginx..."
cp nginx/firewallweb.conf /etc/nginx/sites-available/$APP_NAME
sed -i "s/your-domain.com/$DOMAIN/g" /etc/nginx/sites-available/$APP_NAME

# Enable site
ln -sf /etc/nginx/sites-available/$APP_NAME /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default

# Test Nginx configuration
nginx -t

# Start Nginx
systemctl restart nginx
systemctl enable nginx

# Setup SSL certificate
print_status "Setting up SSL certificate..."
if [ "$DOMAIN" != "your-domain.com" ]; then
    certbot --nginx -d $DOMAIN --non-interactive --agree-tos --email admin@$DOMAIN
else
    print_warning "Please update DOMAIN variable and run certbot manually"
fi

# Configure fail2ban
print_status "Configuring fail2ban..."
cat > /etc/fail2ban/jail.local <<EOF
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 3

[sshd]
enabled = true
port = ssh
filter = sshd
logpath = /var/log/auth.log
maxretry = 3

[nginx-http-auth]
enabled = true
filter = nginx-http-auth
port = http,https
logpath = /var/log/nginx/error.log
maxretry = 3
EOF

systemctl restart fail2ban
systemctl enable fail2ban

# Create systemd service
print_status "Creating systemd service..."
cat > /etc/systemd/system/$APP_NAME.service <<EOF
[Unit]
Description=Firewall Web Application
After=network.target mongod.service

[Service]
Type=simple
User=$APP_USER
WorkingDirectory=$APP_DIR
          ExecStart=/usr/bin/java -Xmx1g -Dspring.profiles.active=prod -jar $APP_NAME.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd
systemctl daemon-reload

# Create log rotation
print_status "Setting up log rotation..."
cat > /etc/logrotate.d/$APP_NAME <<EOF
$APP_DIR/logs/*.log {
    daily
    missingok
    rotate 52
    compress
    delaycompress
    notifempty
    create 644 $APP_USER $APP_USER
    postrotate
        systemctl reload $APP_NAME
    endscript
}
EOF

print_status "Server setup completed successfully!"
print_status "Next steps:"
print_status "1. Upload your JAR file to $APP_DIR"
print_status "2. Update domain in nginx configuration"
print_status "3. Run: systemctl start $APP_NAME"
print_status "4. Check status: systemctl status $APP_NAME"
