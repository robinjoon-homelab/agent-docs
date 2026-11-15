#!/bin/sh
# Run locally inside the server container. Never send the credential to application logs.
set -eu
umask 077
if [ "$#" -ne 1 ]; then
    echo 'Usage: sh emergency-login.sh /absolute/private/new-link-file' >&2
    exit 2
fi
case "$1" in
    /*) ;;
    *) echo 'An absolute output path is required.' >&2; exit 2 ;;
esac
if [ -e "$1" ] || [ -L "$1" ]; then
    echo 'The output file must not exist.' >&2
    exit 2
fi
exec java -Dloader.main=xyz.robinjoon.agentdocs.config.EmergencyConsoleConfig \
    -cp /app/application.jar org.springframework.boot.loader.launch.PropertiesLauncher "$1"
