idf_component_register(SRCS "main.c"
                       INCLUDE_DIRS "."
                       REQUIRES presence_protocole nvs_flash bt mbedtls esp_wifi esp_http_client)
