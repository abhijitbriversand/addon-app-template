#!/bin/bash
echo "Starting build process.."
  ruf-apps-cli compile
  ruf-apps-cli create
  ruf-apps-cli deploy
echo 'Build is ready!!!'