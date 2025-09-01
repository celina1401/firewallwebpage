#!/bin/bash

# Deploy script for Firewall Web Application
# Usage: ./deploy.sh [version]

set -e

# Configuration
APP_NAME="firewallweb"
APP_USER="firewallweb"
APP_DIR="/opt/firewallweb"
SERVICE_NAME="firewallweb"
JAR_FILE="firewallweb.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
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
if [[ $EUID -eq 0 ]]; then
   print_error "This script should not be run as root"
   exit 1
fi

# Check if JAR file exists
if [ ! -f "target/$JAR_FILE" ]; then
    print_error "JAR file not found. Please build the project first: mvn clean package"
    exit 1
fi

print_status "Starting deployment of $APP_NAME..."

# Create application directory if it doesn't exist
sudo mkdir -p $APP_DIR
sudo chown $USER:$USER $APP_DIR

# Stop the service if it's running
print_status "Stopping $SERVICE_NAME service..."
sudo systemctl stop $SERVICE_NAME || true

# Backup existing JAR
if [ -f "$APP_DIR/$JAR_FILE" ]; then
    print_status "Backing up existing JAR file..."
    cp "$APP_DIR/$JAR_FILE" "$APP_DIR/${JAR_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
fi

# Copy new JAR file
print_status "Copying new JAR file..."
cp "target/$JAR_FILE" "$APP_DIR/"

# Create logs directory
mkdir -p "$APP_DIR/logs"

# Create systemd service file
print_status "Creating systemd service..."
sudo tee /etc/systemd/system/$SERVICE_NAME.service > /dev/null <<EOF
[Unit]
Description=Firewall Web Application
After=network.target mongodb.service

[Service]
Type=simple
User=$USER
WorkingDirectory=$APP_DIR
          ExecStart=/usr/bin/java -Xmx1g -Dspring.profiles.active=prod -jar $JAR_FILE
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd and enable service
print_status "Reloading systemd and enabling service..."
sudo systemctl daemon-reload
sudo systemctl enable $SERVICE_NAME

# Start the service
print_status "Starting $SERVICE_NAME service..."
sudo systemctl start $SERVICE_NAME

# Wait for service to start
print_status "Waiting for service to start..."
sleep 10

# Check service status
if sudo systemctl is-active --quiet $SERVICE_NAME; then
    print_status "Service started successfully!"
    print_status "Service status:"
    sudo systemctl status $SERVICE_NAME --no-pager -l
    
    print_status "Application is running at: http://localhost:1402"
    print_status "Logs can be viewed with: sudo journalctl -u $SERVICE_NAME -f"
else
    print_error "Service failed to start!"
    print_status "Checking service logs:"
    sudo journalctl -u $SERVICE_NAME --no-pager -l -n 50
    exit 1
fi

print_status "Deployment completed successfully!"
