#!/bin/bash
echo "Starting npm install.."
rm -r node_modules/ui-platform-tools
rm -r node_modules/ui-platform-utils
rm -r node_modules/ui-platform-elements
rm -r node_modules/ui-platform-dataaccess
npm install

echo 'npm install completed!!!'