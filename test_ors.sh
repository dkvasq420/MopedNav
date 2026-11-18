#!/bin/bash
echo "Testing ORS connectivity..."
wget -q --spider https://api.openrouteservice.org 2>&1
echo "Exit code: $?"
