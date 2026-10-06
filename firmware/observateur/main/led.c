/* Voyant d'état piloté par une petite tâche cadencée à 50 ms. */
#include <stdbool.h>
#include "driver/gpio.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "sdkconfig.h"
#include "reseau.h"
#include "led.h"

#define PERIODE_MS      50
#define DEMI_CLIGNO     10      /* 10 x 50 ms : 0,5 s allumé, 0,5 s éteint */
#define DUREE_ECLAT     3       /* 150 ms d'inversion */

static volatile int s_eclat;

static void tache_led(void *arg)
{
    const gpio_num_t broche = (gpio_num_t)CONFIG_PRESENCE_GPIO_LED;
    unsigned tic = 0;
    for (;;) {
        bool allume = reseau_connecte() ? true : (tic / DEMI_CLIGNO) % 2 == 0;
        if (s_eclat > 0) {
            s_eclat--;
            allume = !allume;
        }
        gpio_set_level(broche, allume ? 1 : 0);
        tic++;
        vTaskDelay(pdMS_TO_TICKS(PERIODE_MS));
    }
}

esp_err_t led_demarrer(void)
{
    if (CONFIG_PRESENCE_GPIO_LED < 0) {
        return ESP_OK;
    }
    const gpio_num_t broche = (gpio_num_t)CONFIG_PRESENCE_GPIO_LED;
    gpio_reset_pin(broche);
    gpio_set_direction(broche, GPIO_MODE_OUTPUT);
    return xTaskCreate(tache_led, "led", 2048, NULL, 2, NULL) == pdPASS ? ESP_OK : ESP_ERR_NO_MEM;
}

void led_eclat(void)
{
    s_eclat = DUREE_ECLAT;
}
