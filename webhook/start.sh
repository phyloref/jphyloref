#!/usr/bin/env bash
# Start a Webhook instance to respond to queries.
#
# Environmental variables:
# - PORT: The HTTP port to listen on (e.g. 80)

webhook -port "${PORT:=80}" -hooks hooks.json -header Access-Control-Allow-Origin=* -header Access-Control-Allow-Headers=x-hub-signature -verbose -urlprefix ""
