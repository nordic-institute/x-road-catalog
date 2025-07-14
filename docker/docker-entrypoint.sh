#!/bin/sh
set -e

# Default to /app/*.yaml, but allow override
CATALOG_APPLICATION_FILE=${CATALOG_APPLICATION_FILE:-/app/application.yaml}

echo "Starting entrypoint script..."
# Only update if settings file exists
if [ -f "$CATALOG_APPLICATION_FILE" ]; then
  # Loop through all environment variables
  env | while IFS='=' read -r VAR VALUE; do
    # Only process variables with 'setting_' prefix
    case "$VAR" in
      setting_*)
      # Strip the 'setting_' prefix for the YAML path
      PATH_VAR=${VAR#setting_}
      # Convert to yq path (replace _ with - for YAML keys)
      YQ_PATH=$(echo "$PATH_VAR" | sed 's/_/-/g')
      # Determine if the value should be quoted (if not a number, boolean, or null)
      case "$VALUE" in
        *[!0-9.]*|*.*.*|"")
        # Contains non-numeric characters, multiple dots, or empty - check for booleans/null
        case "$VALUE" in
          true|false|null)
          # No quotes for numbers, booleans, or null
          yq -i ".${YQ_PATH} = ${VALUE}" "$CATALOG_APPLICATION_FILE"
          ;;
        *)
          # Quote for strings
          yq -i ".${YQ_PATH} = \"${VALUE}\"" "$CATALOG_APPLICATION_FILE"
          ;;
        esac
      esac
    esac
  done
fi

exec "$@"