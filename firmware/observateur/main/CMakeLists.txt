idf_component_register(SRCS "main.c"
                            "identite.c"
                            "reseau.c"
                            "config.c"
                            "nonce.c"
                            "ble.c"
                            "attestation.c"
                            "journal.c"
                            "led.c"
                       INCLUDE_DIRS "."
                       REQUIRES presence_protocole nvs_flash bt mbedtls esp_wifi esp_netif
                                esp_event esp_http_client json esp_timer driver)
